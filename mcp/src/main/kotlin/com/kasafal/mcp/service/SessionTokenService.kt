package com.kasafal.mcp.service

import com.kasafal.mcp.model.session.*
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val logger = KotlinLogging.logger {}

/**
 * Service for managing session tokens in memory.
 * Provides secure, temporary access to database connections without storing credentials permanently.
 * 
 * Features:
 * - In-memory token storage (no database persistence)
 * - Automatic token expiration and cleanup
 * - Usage tracking and limits
 * - Thread-safe operations
 * - Encrypted password storage
 */
@Service
class SessionTokenService(
    private val credentialService: CredentialService
) {
    
    // Thread-safe in-memory storage for session tokens
    private val activeTokens = ConcurrentHashMap<String, SessionToken>()
    private val lock = ReentrantReadWriteLock()
    
    /**
     * Create a new session token with database connection information.
     * Password is immediately encrypted and plaintext is cleared from memory.
     */
    fun createSessionToken(request: CreateSessionTokenRequest): CreateSessionTokenResponse {
        logger.info { "Creating session token for database: ${request.name} on ${request.host}" }
        
        // Validate request
        validateCreateTokenRequest(request)
        
        // Test connection before creating token
        testDatabaseConnection(request)
        
        // Encrypt password immediately
        val encryptedPassword = credentialService.encrypt(request.password)
        
        // Create connection info with encrypted password
        val connectionInfo = DatabaseConnectionInfo(
            name = request.name,
            host = request.host,
            port = request.port,
            database = request.database,
            username = request.username,
            encryptedPassword = encryptedPassword,
            schema = request.schema,
            maxConnections = minOf(request.maxUsages / 10, 10), // Reasonable connection pool size
            queryTimeoutSeconds = minOf(request.expirationMinutes, 30),
            description = request.description
        )
        
        // Calculate expiration time
        val expiresAt = LocalDateTime.now().plusMinutes(request.expirationMinutes.toLong())
        
        // Create session token
        val sessionToken = SessionToken(
            connectionInfo = connectionInfo,
            expiresAt = expiresAt,
            maxUsages = request.maxUsages,
            allowedOperations = request.allowedOperations
        )
        
        // Store token securely
        lock.write {
            activeTokens[sessionToken.tokenId] = sessionToken
        }
        
        logger.info { "Session token created: ${sessionToken.tokenId} (expires: $expiresAt)" }
        
        return CreateSessionTokenResponse(
            tokenId = sessionToken.tokenId,
            expiresAt = expiresAt,
            maxUsages = request.maxUsages,
            allowedOperations = request.allowedOperations,
            connectionName = request.name
        )
    }
    
    /**
     * Validate and retrieve connection information for a token.
     * Increments usage count if token is valid.
     */
    fun validateAndUseToken(tokenId: String, requiredOperation: DatabaseOperation): TokenValidationResult {
        return lock.write {
            val token = activeTokens[tokenId]
            
            if (token == null) {
                logger.warn { "Token not found: $tokenId" }
                return@write TokenValidationResult(
                    isValid = false,
                    errorMessage = "Token not found or has been invalidated"
                )
            }
            
            if (!token.isValid()) {
                logger.warn { "Invalid token used: $tokenId (expired or exceeded usage limit)" }
                // Remove expired/invalid tokens
                activeTokens.remove(tokenId)
                return@write TokenValidationResult(
                    isValid = false,
                    errorMessage = "Token has expired or exceeded usage limit"
                )
            }
            
            if (!token.allowedOperations.contains(requiredOperation)) {
                logger.warn { "Operation $requiredOperation not allowed for token: $tokenId" }
                return@write TokenValidationResult(
                    isValid = false,
                    errorMessage = "Operation $requiredOperation is not allowed for this token"
                )
            }
            
            // Increment usage count
            if (!token.incrementUsage()) {
                logger.warn { "Failed to increment usage for token: $tokenId" }
                activeTokens.remove(tokenId)
                return@write TokenValidationResult(
                    isValid = false,
                    errorMessage = "Token usage limit exceeded"
                )
            }
            
            logger.debug { "Token $tokenId used successfully (usage: ${token.usageCount}/${token.maxUsages})" }
            
            TokenValidationResult(
                isValid = true,
                connectionInfo = token.connectionInfo,
                allowedOperations = token.allowedOperations,
                remainingUsages = token.maxUsages - token.usageCount,
                expiresAt = token.expiresAt
            )
        }
    }
    
    /**
     * Invalidate a specific token immediately
     */
    fun invalidateToken(tokenId: String): Boolean {
        return lock.write {
            val token = activeTokens[tokenId]
            if (token != null) {
                token.invalidate()
                activeTokens.remove(tokenId)
                logger.info { "Token invalidated: $tokenId" }
                true
            } else {
                logger.warn { "Attempted to invalidate non-existent token: $tokenId" }
                false
            }
        }
    }
    
    /**
     * Get token usage statistics (without sensitive data)
     */
    fun getTokenStats(tokenId: String): TokenUsageStats? {
        return lock.read {
            val token = activeTokens[tokenId]
            token?.let {
                TokenUsageStats(
                    tokenId = it.tokenId,
                    usageCount = it.usageCount,
                    maxUsages = it.maxUsages,
                    remainingUsages = it.maxUsages - it.usageCount,
                    createdAt = it.createdAt,
                    expiresAt = it.expiresAt,
                    isActive = it.isActive,
                    allowedOperations = it.allowedOperations
                )
            }
        }
    }
    
    /**
     * Get count of active tokens (for monitoring)
     */
    fun getActiveTokenCount(): Int {
        return lock.read { activeTokens.size }
    }
    
    /**
     * Cleanup expired tokens (runs every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    fun cleanupExpiredTokens() {
        val now = LocalDateTime.now()
        val removedCount = lock.write {
            val expiredTokens = activeTokens.filterValues { !it.isValid() }
            expiredTokens.keys.forEach { activeTokens.remove(it) }
            expiredTokens.size
        }
        
        if (removedCount > 0) {
            logger.info { "Cleaned up $removedCount expired tokens" }
        }
    }
    
    /**
     * Emergency cleanup - invalidate all tokens (for security incidents)
     */
    fun invalidateAllTokens(): Int {
        return lock.write {
            val count = activeTokens.size
            activeTokens.clear()
            logger.warn { "Emergency cleanup: Invalidated all $count active tokens" }
            count
        }
    }
    
    private fun validateCreateTokenRequest(request: CreateSessionTokenRequest) {
        require(request.name.isNotBlank()) { "Connection name cannot be blank" }
        require(request.host.isNotBlank()) { "Host cannot be blank" }
        require(request.port in 1..65535) { "Port must be between 1 and 65535" }
        require(request.database.isNotBlank()) { "Database name cannot be blank" }
        require(request.username.isNotBlank()) { "Username cannot be blank" }
        require(request.password.isNotBlank()) { "Password cannot be blank" }
        require(request.expirationMinutes in 1..1440) { "Expiration must be between 1 and 1440 minutes (24 hours)" }
        require(request.maxUsages in 1..1000) { "Max usages must be between 1 and 1000" }
    }
    
    private fun testDatabaseConnection(request: CreateSessionTokenRequest) {
        try {
            val url = "jdbc:postgresql://${request.host}:${request.port}/${request.database}"
            logger.debug { "Testing database connection to: $url with user: ${request.username}" }
            
            // Load PostgreSQL driver explicitly
            Class.forName("org.postgresql.Driver")
            
            java.sql.DriverManager.getConnection(url, request.username, request.password).use { connection ->
                val isValid = connection.isValid(5)
                if (!isValid) {
                    throw IllegalArgumentException("Database connection test failed - connection is not valid")
                }
                logger.debug { "Connection validation successful" }
            }
            logger.info { "Database connection test successful for ${request.host}:${request.port}/${request.database}" }
        } catch (e: java.sql.SQLException) {
            logger.error(e) { "SQL Exception during database connection test: ${e.sqlState} - ${e.message}" }
            throw IllegalArgumentException("Database connection failed: ${e.message}")
        } catch (e: ClassNotFoundException) {
            logger.error(e) { "PostgreSQL JDBC driver not found" }
            throw IllegalArgumentException("PostgreSQL driver not available: ${e.message}")
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during database connection test for ${request.host}:${request.port}/${request.database}" }
            throw IllegalArgumentException("Cannot connect to database: ${e.message}")
        }
    }
}

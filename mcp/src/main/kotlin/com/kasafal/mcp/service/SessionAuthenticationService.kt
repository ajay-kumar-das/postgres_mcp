package com.kasafal.mcp.service

import com.kasafal.mcp.exception.McpException
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
 * Service for managing session-based authentication.
 * Handles session creation, authentication, and validation without tokens.
 */
@Service
class SessionAuthenticationService(
    private val credentialService: CredentialService,
    private val databaseService: DatabaseService
) {
    
    // Thread-safe in-memory storage for sessions
    private val activeSessions = ConcurrentHashMap<String, SessionAuth>()
    private val lock = ReentrantReadWriteLock()
    private val sessionTimeout = 2 * 60 * 60 * 1000L // 2 hours in milliseconds
    
    /**
     * Create a new session for authentication
     */
    fun createSession(purpose: String = "database_access"): SessionAuthResponse {
        val sessionAuth = SessionAuth(
            purpose = purpose,
            allowedOperations = setOf(
                DatabaseOperation.SCHEMA_DISCOVERY,
                DatabaseOperation.TABLE_SAMPLING,
                DatabaseOperation.SELECT_QUERIES,
                DatabaseOperation.EXPLAIN_QUERIES,
                DatabaseOperation.CONNECTION_TEST
            )
        )
        
        lock.write {
            activeSessions[sessionAuth.sessionId] = sessionAuth
        }
        
        logger.info { "Created new session: ${sessionAuth.sessionId} for purpose: $purpose" }
        
        return SessionAuthResponse(
            sessionId = sessionAuth.sessionId,
            status = sessionAuth.status,
            expiresAt = sessionAuth.expiresAt,
            allowedOperations = sessionAuth.allowedOperations,
            message = "Session created. Please authenticate with your database credentials."
        )
    }
    
    /**
     * Authenticate a session with database credentials
     */
    fun authenticateSession(request: AuthenticateSessionRequest): SessionAuthResponse {
        logger.info { "Authenticating session: ${request.sessionId}" }
        
        return lock.write {
            val session = activeSessions[request.sessionId]
                ?: throw McpException("Session not found: ${request.sessionId}")
            
            if (session.isExpired()) {
                session.status = AuthStatus.EXPIRED
                throw McpException("Session has expired: ${request.sessionId}")
            }
            
            if (session.status != AuthStatus.PENDING) {
                throw McpException("Session is not in pending state: ${request.sessionId}")
            }
            
            // Test database connection before storing credentials
            testDatabaseConnection(request)
            
            // Encrypt password and create connection info
            val encryptedPassword = credentialService.encrypt(request.password)
            val connectionInfo = DatabaseConnectionInfo(
                name = request.name,
                host = request.host,
                port = request.port,
                database = request.database,
                username = request.username,
                encryptedPassword = encryptedPassword,
                schema = request.schema,
                rateLimitPerSecond = request.rateLimitPerSecond,
                description = request.description
            )
            
            // Update session with authentication info
            session.status = AuthStatus.AUTHENTICATED
            session.authenticatedAt = LocalDateTime.now()
            session.connectionInfo = connectionInfo
            
            logger.info { "Session authenticated successfully: ${request.sessionId}" }
            
            SessionAuthResponse(
                sessionId = session.sessionId,
                status = session.status,
                expiresAt = session.expiresAt,
                allowedOperations = session.allowedOperations,
                connectionName = request.name,
                message = "Authentication successful. Session is ready for database operations."
            )
        }
    }
    
    /**
     * Validate and use a session for database operations
     */
    fun validateAndUseSession(sessionId: String, requiredOperation: DatabaseOperation): SessionValidationResult {
        return lock.write {
            val session = activeSessions[sessionId]
            
            if (session == null) {
                logger.warn { "Session not found: $sessionId" }
                return@write SessionValidationResult(
                    isValid = false,
                    errorMessage = "Session not found or has been invalidated"
                )
            }
            
            if (session.isExpired()) {
                logger.warn { "Session has expired: $sessionId" }
                session.status = AuthStatus.EXPIRED
                activeSessions.remove(sessionId)
                return@write SessionValidationResult(
                    isValid = false,
                    errorMessage = "Session has expired"
                )
            }
            
            if (session.status != AuthStatus.AUTHENTICATED) {
                logger.warn { "Session is not authenticated: $sessionId (status: ${session.status})" }
                return@write SessionValidationResult(
                    isValid = false,
                    errorMessage = "Session is not authenticated. Please complete authentication first."
                )
            }
            
            if (!session.allowedOperations.contains(requiredOperation)) {
                logger.warn { "Operation $requiredOperation not allowed for session: $sessionId" }
                return@write SessionValidationResult(
                    isValid = false,
                    errorMessage = "Operation $requiredOperation is not allowed for this session"
                )
            }
            
            if (session.usageCount >= session.maxUsages) {
                logger.warn { "Session usage limit exceeded: $sessionId" }
                session.status = AuthStatus.EXPIRED
                activeSessions.remove(sessionId)
                return@write SessionValidationResult(
                    isValid = false,
                    errorMessage = "Session usage limit exceeded"
                )
            }
            
            // Increment usage count
            session.usageCount++
            
            logger.debug { "Session $sessionId used successfully (usage: ${session.usageCount}/${session.maxUsages})" }
            
            SessionValidationResult(
                isValid = true,
                sessionAuth = session
            )
        }
    }
    
    /**
     * Get session status
     */
    fun getSessionStatus(sessionId: String): SessionAuthResponse? {
        return lock.read {
            val session = activeSessions[sessionId]
            session?.let {
                if (it.isExpired()) {
                    it.status = AuthStatus.EXPIRED
                }
                
                SessionAuthResponse(
                    sessionId = it.sessionId,
                    status = it.status,
                    expiresAt = it.expiresAt,
                    allowedOperations = it.allowedOperations,
                    connectionName = it.connectionInfo?.name,
                    message = when (it.status) {
                        AuthStatus.PENDING -> "Session is waiting for authentication"
                        AuthStatus.AUTHENTICATED -> "Session is authenticated and ready"
                        AuthStatus.EXPIRED -> "Session has expired"
                        AuthStatus.INVALID -> "Session is invalid"
                    }
                )
            }
        }
    }
    
    /**
     * Get session usage statistics
     */
    fun getSessionStats(sessionId: String): SessionUsageStats? {
        return lock.read {
            val session = activeSessions[sessionId]
            session?.let {
                SessionUsageStats(
                    sessionId = it.sessionId,
                    status = it.status,
                    usageCount = it.usageCount,
                    maxUsages = it.maxUsages,
                    remainingUsages = it.maxUsages - it.usageCount,
                    createdAt = it.createdAt,
                    expiresAt = it.expiresAt,
                    allowedOperations = it.allowedOperations,
                    authenticatedAt = it.authenticatedAt
                )
            }
        }
    }
    
    /**
     * Invalidate a session immediately
     */
    fun invalidateSession(sessionId: String): Boolean {
        return lock.write {
            val session = activeSessions.remove(sessionId)
            if (session != null) {
                session.status = AuthStatus.INVALID
                logger.info { "Session invalidated: $sessionId" }
                true
            } else {
                logger.warn { "Attempted to invalidate non-existent session: $sessionId" }
                false
            }
        }
    }
    
    /**
     * Get count of active sessions (for monitoring)
     */
    fun getActiveSessionCount(): Int {
        return lock.read {
            activeSessions.values.count { it.status == AuthStatus.AUTHENTICATED && !it.isExpired() }
        }
    }
    
    /**
     * Get all session statistics for monitoring
     */
    fun getAllSessionStats(): Map<String, Any> {
        return lock.read {
            val sessions = activeSessions.values
            mapOf(
                "totalSessions" to sessions.size,
                "pendingSessions" to sessions.count { it.status == AuthStatus.PENDING },
                "authenticatedSessions" to sessions.count { it.status == AuthStatus.AUTHENTICATED },
                "expiredSessions" to sessions.count { it.status == AuthStatus.EXPIRED },
                "invalidSessions" to sessions.count { it.status == AuthStatus.INVALID }
            )
        }
    }
    
    /**
     * Clean up expired sessions (runs every 10 minutes)
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    fun cleanupExpiredSessions() {
        val cleanedCount = lock.write {
            val iterator = activeSessions.entries.iterator()
            var count = 0
            
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val session = entry.value
                
                if (session.isExpired() || session.status == AuthStatus.INVALID) {
                    iterator.remove()
                    count++
                }
            }
            count
        }
        
        if (cleanedCount > 0) {
            logger.info { "Cleaned up $cleanedCount expired/invalid sessions" }
        }
    }
    
    /**
     * Emergency: Invalidate all sessions
     */
    fun invalidateAllSessions(): Int {
        return lock.write {
            val count = activeSessions.size
            activeSessions.clear()
            logger.warn { "Emergency invalidation: cleared all $count sessions" }
            count
        }
    }
    
    private fun testDatabaseConnection(request: AuthenticateSessionRequest) {
        try {
            val url = "jdbc:postgresql://${request.host}:${request.port}/${request.database}"
            java.sql.DriverManager.getConnection(url, request.username, request.password).use { connection ->
                if (!connection.isValid(5)) {
                    throw McpException("Database connection test failed")
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to test connection to ${request.host}:${request.port}/${request.database}" }
            throw McpException("Connection test failed: ${e.message}")
        }
    }
}

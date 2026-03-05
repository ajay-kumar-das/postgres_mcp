package com.kasafal.mcp.service

import com.kasafal.mcp.exception.McpException
import com.kasafal.mcp.model.session.*
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

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
    
    // Thread-safe in-memory storage for sessions - ConcurrentHashMap provides lock-free operations
    private val activeSessions = ConcurrentHashMap<String, SessionAuth>()
    private val sessionTimeout = 2 * 60 * 60 * 1000L // 2 hours in milliseconds
    
    /**
     * Create a new session for authentication
     */
    fun createSession(purpose: String = "database_access", source: String = "unknown"): SessionAuthResponse {
        val sessionAuth = SessionAuth(
            purpose = purpose,
            source = source,
            allowedOperations = setOf(
                DatabaseOperation.SCHEMA_DISCOVERY,
                DatabaseOperation.TABLE_SAMPLING,
                DatabaseOperation.SELECT_QUERIES,
                DatabaseOperation.EXPLAIN_QUERIES,
                DatabaseOperation.CONNECTION_TEST
            )
        )
        
        activeSessions[sessionAuth.sessionId] = sessionAuth
        
        logger.info { "Created new session: ${sessionAuth.sessionId} for purpose: $purpose from source: $source" }
        
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
        
        val session = activeSessions[request.sessionId]
            ?: throw McpException("Session not found: ${request.sessionId}")
        
        if (session.isExpired()) {
            session.status = AuthStatus.EXPIRED
            activeSessions.remove(request.sessionId)
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
        
        // Update session with authentication info atomically
        session.status = AuthStatus.AUTHENTICATED
        session.authenticatedAt = LocalDateTime.now()
        session.connectionInfo = connectionInfo
        
        logger.info { "Session authenticated successfully: ${request.sessionId} from source: ${session.source} for database: ${request.name}" }
        
        return SessionAuthResponse(
            sessionId = session.sessionId,
            status = session.status,
            expiresAt = session.expiresAt,
            allowedOperations = session.allowedOperations,
            connectionName = request.name,
            message = "Authentication successful. Session is ready for database operations."
        )
    }
    
    /**
     * Validate and use a session for database operations
     */
    fun validateAndUseSession(sessionId: String, requiredOperation: DatabaseOperation): SessionValidationResult {
        val session = activeSessions[sessionId]
        
        if (session == null) {
            logger.warn { "Session not found: $sessionId" }
            return SessionValidationResult(
                isValid = false,
                errorMessage = "Session not found or has been invalidated"
            )
        }
        
        // Check if session is expired and atomically mark it
        if (session.isExpired()) {
            logger.warn { "Session has expired: $sessionId" }
            if (session.compareAndSetStatus(session.status, AuthStatus.EXPIRED)) {
                activeSessions.remove(sessionId)
            }
            return SessionValidationResult(
                isValid = false,
                errorMessage = "Session has expired"
            )
        }
        
        // Check authentication status
        if (session.status != AuthStatus.AUTHENTICATED) {
            logger.warn { "Session is not authenticated: $sessionId (status: ${session.status})" }
            return SessionValidationResult(
                isValid = false,
                errorMessage = "Session is not authenticated. Please complete authentication first."
            )
        }
        
        // Check allowed operations
        if (!session.allowedOperations.contains(requiredOperation)) {
            logger.warn { "Operation $requiredOperation not allowed for session: $sessionId" }
            return SessionValidationResult(
                isValid = false,
                errorMessage = "Operation $requiredOperation is not allowed for this session"
            )
        }
        
        // Check and atomically increment usage count
        if (session.wouldExceedUsageLimit()) {
            logger.warn { "Session usage limit exceeded: $sessionId" }
            if (session.compareAndSetStatus(AuthStatus.AUTHENTICATED, AuthStatus.EXPIRED)) {
                activeSessions.remove(sessionId)
            }
            return SessionValidationResult(
                isValid = false,
                errorMessage = "Session usage limit exceeded"
            )
        }
        
        // Atomically increment usage count
        val newUsageCount = session.incrementUsage()
        
        // Double-check usage limit after increment (rare edge case)
        if (newUsageCount > session.maxUsages) {
            logger.warn { "Session usage limit exceeded after increment: $sessionId" }
            if (session.compareAndSetStatus(AuthStatus.AUTHENTICATED, AuthStatus.EXPIRED)) {
                activeSessions.remove(sessionId)
            }
            return SessionValidationResult(
                isValid = false,
                errorMessage = "Session usage limit exceeded"
            )
        }
        
        logger.debug { "Session $sessionId used successfully (usage: $newUsageCount/${session.maxUsages})" }
        
        return SessionValidationResult(
            isValid = true,
            sessionAuth = session,
            newUsageCount = newUsageCount
        )
    }
    
    /**
     * Get session status
     */
    fun getSessionStatus(sessionId: String): SessionAuthResponse? {
        val session = activeSessions[sessionId]
        return session?.let {
            // Atomically check and mark expired sessions
            if (it.isExpired() && it.compareAndSetStatus(it.status, AuthStatus.EXPIRED)) {
                activeSessions.remove(sessionId)
                // Clean up connection pools when session expires
                it.connectionInfo?.let { connectionInfo ->
                    databaseService.releaseSessionFromPool(sessionId, connectionInfo)
                }
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
    
    /**
     * Get session usage statistics
     */
    fun getSessionStats(sessionId: String): SessionUsageStats? {
        val session = activeSessions[sessionId]
        return session?.let {
            SessionUsageStats.fromSession(it)
        }
    }
    
    /**
     * Invalidate a session immediately
     */
    fun invalidateSession(sessionId: String): Boolean {
        val session = activeSessions.remove(sessionId)
        return if (session != null) {
            session.compareAndSetStatus(session.status, AuthStatus.INVALID)
            
            // Clean up connection pools when session is invalidated
            session.connectionInfo?.let { connectionInfo ->
                databaseService.releaseSessionFromPool(sessionId, connectionInfo)
            }
            
            logger.info { "Session invalidated: $sessionId" }
            true
        } else {
            logger.warn { "Attempted to invalidate non-existent session: $sessionId" }
            false
        }
    }
    
    /**
     * Get count of active sessions (for monitoring)
     */
    fun getActiveSessionCount(): Int {
        return activeSessions.values.count { it.status == AuthStatus.AUTHENTICATED && !it.isExpired() }
    }
    
    /**
     * Get all session statistics for monitoring
     */
    fun getAllSessionStats(): Map<String, Any> {
        val sessions = activeSessions.values
        return mapOf(
            "totalSessions" to sessions.size,
            "pendingSessions" to sessions.count { it.status == AuthStatus.PENDING },
            "authenticatedSessions" to sessions.count { it.status == AuthStatus.AUTHENTICATED },
            "expiredSessions" to sessions.count { it.status == AuthStatus.EXPIRED },
            "invalidSessions" to sessions.count { it.status == AuthStatus.INVALID }
        )
    }
    
    /**
     * Clean up expired sessions (runs every 10 minutes)
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    fun cleanupExpiredSessions() {
        var cleanedCount = 0
        val iterator = activeSessions.entries.iterator()
        
        while (iterator.hasNext()) {
            val (sessionId, session) = iterator.next()
            
            if (session.isExpired() || session.status == AuthStatus.INVALID) {
                // Atomically mark expired sessions
                if (session.isExpired()) {
                    session.compareAndSetStatus(session.status, AuthStatus.EXPIRED)
                }
                iterator.remove()
                cleanedCount++
                logger.debug { "Cleaned up session: $sessionId (status: ${session.status})" }
            }
        }
        
        if (cleanedCount > 0) {
            logger.info { "Cleaned up $cleanedCount expired/invalid sessions" }
        }
    }
    
    /**
     * Emergency: Invalidate all sessions
     */
    fun invalidateAllSessions(): Int {
        val count = activeSessions.size
        // Clean up all connection pools before clearing sessions
        activeSessions.forEach { (sessionId, session) ->
            session.connectionInfo?.let { connectionInfo ->
                databaseService.releaseSessionFromPool(sessionId, connectionInfo)
            }
        }
        
        activeSessions.clear()
        logger.warn { "Emergency invalidation: cleared all $count sessions" }
        return count
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
            logger.error(e.message) { "Failed to test connection to ${request.host}:${request.port}/${request.database}" }
            throw McpException("Connection test failed: ${e.message}")
        }
    }
}

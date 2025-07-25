package com.kasafal.mcp.model.session

import java.time.LocalDateTime
import java.util.*

/**
 * Authentication status for a session
 */
enum class AuthStatus {
    PENDING,        // Session created, waiting for authentication
    AUTHENTICATED,  // User has provided credentials and they are valid
    EXPIRED,        // Session has expired
    INVALID         // Session is invalid or revoked
}

/**
 * Database operations allowed for a session
 */
enum class DatabaseOperation {
    SCHEMA_DISCOVERY,      // Get database schema, tables, columns
    TABLE_SAMPLING,        // Sample data from tables
    SELECT_QUERIES,        // Execute SELECT queries
    DATA_QUALITY_ANALYSIS, // Analyze data quality
    DUPLICATE_DETECTION,   // Find duplicate records
    EXPLAIN_QUERIES,       // Explain query execution plans
    CONNECTION_TEST        // Test database connectivity
}

/**
 * Session-based authentication information
 */
data class SessionAuth(
    val sessionId: String = UUID.randomUUID().toString(),
    var status: AuthStatus = AuthStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var expiresAt: LocalDateTime = LocalDateTime.now().plusHours(2), // 2 hour default
    var authenticatedAt: LocalDateTime? = null,
    var connectionInfo: DatabaseConnectionInfo? = null,
    val allowedOperations: Set<DatabaseOperation> = setOf(
        DatabaseOperation.SCHEMA_DISCOVERY,
        DatabaseOperation.TABLE_SAMPLING,
        DatabaseOperation.SELECT_QUERIES
    ),
    val purpose: String = "database_access",
    var usageCount: Int = 0,
    val maxUsages: Int = 100
) {
    /**
     * Check if the session is still valid
     */
    fun isValid(): Boolean {
        return status == AuthStatus.AUTHENTICATED &&
               LocalDateTime.now().isBefore(expiresAt) &&
               usageCount < maxUsages
    }
    
    /**
     * Check if session has expired
     */
    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expiresAt)
    }
}

/**
 * Database connection information (encrypted at rest in memory)
 */
data class DatabaseConnectionInfo(
    val name: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val encryptedPassword: String, // Always stored encrypted
    val schema: String = "public",
    val maxConnections: Int = 5,
    val queryTimeoutSeconds: Int = 30,
    val rateLimitPerSecond: Double = 5.0, // Rate limit: queries per second
    val description: String? = null
)

/**
 * Request to authenticate a session with database credentials
 */
data class AuthenticateSessionRequest(
    val sessionId: String,
    val name: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String, // Will be encrypted immediately
    val schema: String = "public",
    val rateLimitPerSecond: Double = 5.0, // Rate limit: queries per second (default 5)
    val description: String? = null
)

/**
 * Response when creating or authenticating a session
 */
data class SessionAuthResponse(
    val sessionId: String,
    val status: AuthStatus,
    val expiresAt: LocalDateTime,
    val allowedOperations: Set<DatabaseOperation>,
    val connectionName: String? = null,
    val message: String
)

/**
 * Session validation result
 */
data class SessionValidationResult(
    val isValid: Boolean,
    val sessionAuth: SessionAuth? = null,
    val errorMessage: String? = null
)

/**
 * Session usage statistics
 */
data class SessionUsageStats(
    val sessionId: String,
    val status: AuthStatus,
    val usageCount: Int,
    val maxUsages: Int,
    val remainingUsages: Int,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val allowedOperations: Set<DatabaseOperation>,
    val authenticatedAt: LocalDateTime?
)

package com.kasafal.mcp.model.session

import java.time.LocalDateTime
import java.util.*

/**
 * Represents a temporary session token that holds database connection information.
 * Used to avoid exposing database credentials to AI while maintaining security.
 */
data class SessionToken(
    val tokenId: String = UUID.randomUUID().toString(),
    val connectionInfo: DatabaseConnectionInfo,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val expiresAt: LocalDateTime,
    val maxUsages: Int = 100, // Maximum number of times this token can be used
    var usageCount: Int = 0,
    val allowedOperations: Set<DatabaseOperation> = setOf(
        DatabaseOperation.SCHEMA_DISCOVERY,
        DatabaseOperation.TABLE_SAMPLING,
        DatabaseOperation.SELECT_QUERIES
    ),
    var isActive: Boolean = true
) {
    /**
     * Check if the token is still valid
     */
    fun isValid(): Boolean {
        return isActive && 
               LocalDateTime.now().isBefore(expiresAt) && 
               usageCount < maxUsages
    }
    
    /**
     * Increment usage count and check if token should be deactivated
     */
    fun incrementUsage(): Boolean {
        if (!isValid()) return false
        
        usageCount++
        if (usageCount >= maxUsages) {
            isActive = false
        }
        return true
    }
    
    /**
     * Invalidate the token immediately
     */
    fun invalidate() {
        isActive = false
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
    val description: String? = null
)

/**
 * Request to create a new session token
 */
data class CreateSessionTokenRequest(
    val name: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String, // Will be encrypted immediately
    val schema: String = "public",
    val description: String? = null,
    val expirationMinutes: Int = 60, // Default 1 hour
    val maxUsages: Int = 100,
    val allowedOperations: Set<DatabaseOperation> = setOf(
        DatabaseOperation.SCHEMA_DISCOVERY,
        DatabaseOperation.TABLE_SAMPLING,
        DatabaseOperation.SELECT_QUERIES
    )
)

/**
 * Response when creating a session token
 */
data class CreateSessionTokenResponse(
    val tokenId: String,
    val expiresAt: LocalDateTime,
    val maxUsages: Int,
    val allowedOperations: Set<DatabaseOperation>,
    val connectionName: String,
    val message: String = "Token created successfully. Keep this token secure and use it for database operations."
)

/**
 * Token validation result
 */
data class TokenValidationResult(
    val isValid: Boolean,
    val connectionInfo: DatabaseConnectionInfo? = null,
    val allowedOperations: Set<DatabaseOperation> = emptySet(),
    val remainingUsages: Int = 0,
    val expiresAt: LocalDateTime? = null,
    val errorMessage: String? = null
)

/**
 * Allowed database operations for a token
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
 * Token usage statistics
 */
data class TokenUsageStats(
    val tokenId: String,
    val usageCount: Int,
    val maxUsages: Int,
    val remainingUsages: Int,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val isActive: Boolean,
    val allowedOperations: Set<DatabaseOperation>
)

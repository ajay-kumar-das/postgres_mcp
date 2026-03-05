package com.kasafal.mcp.model.session

import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
 * Database operations allowed for a session - now with granular permissions
 */
enum class DatabaseOperation(val displayName: String, val description: String, val category: String) {
    // Schema & Structure
    SCHEMA_DISCOVERY("Schema Discovery", "View database schemas, tables, and structure", "Schema"),
    TABLE_LISTING("Table Listing", "List tables and views in database", "Schema"),
    TABLE_DESCRIPTION("Table Description", "Get detailed table column information", "Schema"),
    
    // Data Access
    SELECT_QUERIES("Execute Queries", "Run SELECT queries on database", "Data Access"),
    TABLE_SAMPLING("Table Sampling", "Get sample data from tables", "Data Access"),
    VIEW_DATA("View Data", "Browse table contents with pagination", "Data Access"),
    
    // Analysis & Quality
    DATA_QUALITY_ANALYSIS("Data Quality Analysis", "Analyze data quality issues", "Analysis"),
    DUPLICATE_DETECTION("Duplicate Detection", "Find duplicate records in tables", "Analysis"),
    DATA_PROFILING("Data Profiling", "Generate statistical profiles of data", "Analysis"),
    COLUMN_ANALYSIS("Column Analysis", "Analyze column distributions and patterns", "Analysis"),
    
    // Performance & Diagnostics
    EXPLAIN_QUERIES("Query Explanation", "Analyze query execution plans", "Performance"),
    PERFORMANCE_MONITORING("Performance Monitoring", "Monitor query performance metrics", "Performance"),
    INDEX_ANALYSIS("Index Analysis", "Analyze table indexes and recommendations", "Performance"),
    
    // Security & Validation
    SQL_VALIDATION("SQL Validation", "Validate SQL syntax and security", "Security"),
    CONNECTION_TEST("Connection Test", "Test database connectivity", "Security"),
    QUERY_AUDITING("Query Auditing", "Log and audit executed queries", "Security"),
    
    // Advanced Features
    CUSTOM_FUNCTIONS("Custom Functions", "Execute database functions and procedures", "Advanced"),
    METADATA_ACCESS("Metadata Access", "Access database metadata and system tables", "Advanced")
}

/**
 * Thread-safe session-based authentication information
 */
class SessionAuth(
    val sessionId: String = UUID.randomUUID().toString(),
    initialStatus: AuthStatus = AuthStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val allowedOperations: Set<DatabaseOperation> = setOf(
        DatabaseOperation.SCHEMA_DISCOVERY,
        DatabaseOperation.TABLE_LISTING,
        DatabaseOperation.TABLE_SAMPLING,
        DatabaseOperation.SELECT_QUERIES,
        DatabaseOperation.SQL_VALIDATION
    ),
    val purpose: String = "database_access",
    val source: String = "unknown", // Track source of the session request
    val maxUsages: Int = 100
) {
    // Thread-safe atomic fields
    private val _status = AtomicReference(initialStatus)
    private val _expiresAt = AtomicReference(LocalDateTime.now().plusHours(2))
    private val _authenticatedAt = AtomicReference<LocalDateTime?>(null)
    private val _connectionInfo = AtomicReference<DatabaseConnectionInfo?>(null)
    private val _usageCount = AtomicInteger(0)
    
    // Thread-safe accessors
    var status: AuthStatus
        get() = _status.get()
        set(value) { _status.set(value) }
    
    var expiresAt: LocalDateTime
        get() = _expiresAt.get()
        set(value) { _expiresAt.set(value) }
    
    var authenticatedAt: LocalDateTime?
        get() = _authenticatedAt.get()
        set(value) { _authenticatedAt.set(value) }
    
    var connectionInfo: DatabaseConnectionInfo?
        get() = _connectionInfo.get()
        set(value) { _connectionInfo.set(value) }
    
    val usageCount: Int
        get() = _usageCount.get()
    /**
     * Atomically increment usage count and return new value
     */
    fun incrementUsage(): Int {
        return _usageCount.incrementAndGet()
    }
    
    /**
     * Atomically check and set status from expected to new value
     * Returns true if successful, false if current status != expected
     */
    fun compareAndSetStatus(expected: AuthStatus, new: AuthStatus): Boolean {
        return _status.compareAndSet(expected, new)
    }
    
    /**
     * Check if the session is still valid (thread-safe snapshot)
     */
    fun isValid(): Boolean {
        val currentStatus = status
        val currentExpiry = expiresAt
        val currentUsage = usageCount
        
        return currentStatus == AuthStatus.AUTHENTICATED &&
               LocalDateTime.now().isBefore(currentExpiry) &&
               currentUsage < maxUsages
    }
    
    /**
     * Check if session has expired (thread-safe)
     */
    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expiresAt)
    }
    
    /**
     * Atomically check if usage limit would be exceeded after increment
     */
    fun wouldExceedUsageLimit(): Boolean {
        return _usageCount.get() >= maxUsages
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
 * Request to authenticate a session with database credentials and permissions
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
    val description: String? = null,
    val requestedOperations: Set<DatabaseOperation> = setOf(
        DatabaseOperation.SCHEMA_DISCOVERY,
        DatabaseOperation.TABLE_LISTING,
        DatabaseOperation.SELECT_QUERIES
    )
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
    val errorMessage: String? = null,
    val newUsageCount: Int? = null  // Include new usage count for tracking
)

/**
 * Session usage statistics (immutable snapshot)
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
    val authenticatedAt: LocalDateTime?,
    val purpose: String,
    val source: String
) {
    companion object {
        /**
         * Create a thread-safe snapshot of SessionAuth
         */
        fun fromSession(session: SessionAuth): SessionUsageStats {
            return SessionUsageStats(
                sessionId = session.sessionId,
                status = session.status,
                usageCount = session.usageCount,
                maxUsages = session.maxUsages,
                remainingUsages = session.maxUsages - session.usageCount,
                createdAt = session.createdAt,
                expiresAt = session.expiresAt,
                allowedOperations = session.allowedOperations,
                authenticatedAt = session.authenticatedAt,
                purpose = session.purpose,
                source = session.source
            )
        }
    }
}

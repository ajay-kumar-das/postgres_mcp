package com.kasafal.mcp.service

import com.kasafal.mcp.exception.DatabaseException
import com.kasafal.mcp.model.database.*
import com.kasafal.mcp.model.session.DatabaseConnectionInfo
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

@Service
class DatabaseService(
    private val credentialService: CredentialService
) {
    
    // Cache for DataSources to avoid creating multiple pools for the same connection info
    private val dataSourceCache = ConcurrentHashMap<String, DataSource>()
    
    // Track which sessions are using which connection pools
    // Key: cacheKey (host:port:db:user), Value: Set of session IDs using this pool
    private val poolSessionReferences = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * Test database connection with provided credentials
     */
    fun testConnection(dto: DatabaseConnectionDto): Boolean {
        return try {
            val url = "jdbc:postgresql://${dto.host}:${dto.port}/${dto.database}"
            DriverManager.getConnection(url, dto.username, dto.password).use { connection ->
                connection.isValid(5)
            }
        } catch (e: SQLException) {
            logger.error(e) { "Failed to test connection to ${dto.host}:${dto.port}/${dto.database}" }
            throw DatabaseException("Connection test failed: ${e.message}", e)
        }
    }
    
    /**
     * Get DataSource for a session (tracks session reference to pool)
     */
    fun getDataSourceForSession(sessionId: String, connectionInfo: DatabaseConnectionInfo): DataSource {
        val cacheKey = "${connectionInfo.host}:${connectionInfo.port}:${connectionInfo.database}:${connectionInfo.username}"
        
        // Add this session to the pool reference set
        poolSessionReferences.computeIfAbsent(cacheKey) { ConcurrentHashMap.newKeySet() }.add(sessionId)
        
        val dataSource = dataSourceCache.computeIfAbsent(cacheKey) {
            logger.info { "Creating new HikariCP connection pool for: $cacheKey" }
            createDataSource(connectionInfo)
        }
        
        logger.debug { "Session $sessionId using pool $cacheKey (${poolSessionReferences[cacheKey]?.size} sessions total)" }
        return dataSource
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun getDataSourceByInfo(connectionInfo: DatabaseConnectionInfo): DataSource {
        val cacheKey = "${connectionInfo.host}:${connectionInfo.port}:${connectionInfo.database}:${connectionInfo.username}"
        return dataSourceCache.computeIfAbsent(cacheKey) {
            logger.info { "Creating new HikariCP connection pool for: $cacheKey (legacy access)" }
            createDataSource(connectionInfo)
        }
    }
    
    private fun createDataSource(connectionInfo: DatabaseConnectionInfo): DataSource {
        val decryptedPassword = credentialService.decrypt(connectionInfo.encryptedPassword)
        
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${connectionInfo.host}:${connectionInfo.port}/${connectionInfo.database}"
            username = connectionInfo.username
            password = decryptedPassword
            schema = connectionInfo.schema

            // Connection pool settings based on session token limits
            maximumPoolSize = connectionInfo.maxConnections
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 300000 // 5 minutes for session-based connections
            maxLifetime = 900000 // 15 minutes for session-based connections
            leakDetectionThreshold = 60000

            // PostgreSQL specific settings
            addDataSourceProperty("ApplicationName", "PostgreSQL MCP Server (Session)")
            addDataSourceProperty("currentSchema", connectionInfo.schema)

            // Security settings
            addDataSourceProperty("ssl", "false") // Configure based on your needs
            addDataSourceProperty("sslmode", "prefer")

            // Performance settings
            addDataSourceProperty("prepareThreshold", "3")
            addDataSourceProperty("prepareTreshold", "3")
            addDataSourceProperty("binaryTransfer", "true")
        }

        val dataSource = HikariDataSource(config)
        
        logger.info { 
            "Created HikariCP pool - MaxPool: ${config.maximumPoolSize}, " +
            "MinIdle: ${config.minimumIdle}, ConnTimeout: ${config.connectionTimeout}ms"
        }
        
        return dataSource
    }
    
    /**
     * Execute a query and return results
     */
    fun executeQuery(dataSource: DataSource, query: String, timeoutSeconds: Int = 30): QueryResult {
        val startTime = System.currentTimeMillis()
        
        return dataSource.connection.use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.queryTimeout = timeoutSeconds
                
                val resultSet = statement.executeQuery()
                val metaData = resultSet.metaData
                val columnCount = metaData.columnCount
                
                // Get column names
                val columns = (1..columnCount).map { metaData.getColumnName(it) }
                
                // Get rows
                val rows = mutableListOf<Map<String, Any?>>()
                while (resultSet.next()) {
                    val row = mutableMapOf<String, Any?>()
                    for (i in 1..columnCount) {
                        row[columns[i - 1]] = resultSet.getObject(i)
                    }
                    rows.add(row)
                }
                
                val executionTime = System.currentTimeMillis() - startTime
                
                QueryResult(
                    columns = columns,
                    rows = rows,
                    rowCount = rows.size,
                    executionTimeMs = executionTime
                )
            }
        }
    }
    
    /**
     * Execute an update/insert/delete query
     */
    fun executeUpdate(dataSource: DataSource, query: String, timeoutSeconds: Int = 30): QueryResult {
        val startTime = System.currentTimeMillis()
        
        return dataSource.connection.use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.queryTimeout = timeoutSeconds
                
                val affectedRows = statement.executeUpdate()
                val executionTime = System.currentTimeMillis() - startTime
                
                QueryResult(
                    columns = emptyList(),
                    rows = emptyList(),
                    rowCount = 0,
                    executionTimeMs = executionTime,
                    affectedRows = affectedRows
                )
            }
        }
    }
    
    /**
     * Release a session's reference to its connection pool
     * Automatically cleans up pool if no more sessions reference it
     */
    fun releaseSessionFromPool(sessionId: String, connectionInfo: DatabaseConnectionInfo) {
        val cacheKey = "${connectionInfo.host}:${connectionInfo.port}:${connectionInfo.database}:${connectionInfo.username}"
        
        val sessionSet = poolSessionReferences[cacheKey]
        if (sessionSet != null) {
            sessionSet.remove(sessionId)
            logger.debug { "Session $sessionId released from pool $cacheKey (${sessionSet.size} sessions remaining)" }
            
            // If no more sessions reference this pool, clean it up
            if (sessionSet.isEmpty()) {
                cleanupPoolByKey(cacheKey)
                poolSessionReferences.remove(cacheKey)
                logger.info { "Connection pool $cacheKey cleaned up (no more session references)" }
            }
        } else {
            logger.warn { "Attempted to release session $sessionId from unknown pool $cacheKey" }
        }
    }
    
    /**
     * Release all pools referenced by a specific session (when session expires/invalidates)
     */
    fun releaseAllPoolsForSession(sessionId: String) {
        val releasedPools = mutableListOf<String>()
        
        poolSessionReferences.forEach { (cacheKey, sessionSet) ->
            if (sessionSet.remove(sessionId)) {
                releasedPools.add(cacheKey)
                logger.debug { "Session $sessionId released from pool $cacheKey (${sessionSet.size} sessions remaining)" }
                
                // If no more sessions reference this pool, clean it up
                if (sessionSet.isEmpty()) {
                    cleanupPoolByKey(cacheKey)
                    poolSessionReferences.remove(cacheKey)
                    logger.info { "Connection pool $cacheKey cleaned up (no more session references)" }
                }
            }
        }
        
        if (releasedPools.isNotEmpty()) {
            logger.info { "Session $sessionId released from ${releasedPools.size} connection pools" }
        }
    }
    
    /**
     * Internal method to close and remove a specific pool
     */
    private fun cleanupPoolByKey(cacheKey: String) {
        val dataSource = dataSourceCache.remove(cacheKey)
        
        if (dataSource is HikariDataSource) {
            try {
                dataSource.close()
                logger.info { "Closed HikariCP pool: $cacheKey" }
            } catch (e: Exception) {
                logger.warn(e) { "Error closing pool: $cacheKey" }
            }
        }
    }
    
    /**
     * Scheduled cleanup to handle any orphaned pools (safety net)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    fun cleanupOrphanedPools() {
        val orphanedPools = mutableListOf<String>()
        
        poolSessionReferences.forEach { (cacheKey, sessionSet) ->
            if (sessionSet.isEmpty()) {
                orphanedPools.add(cacheKey)
            }
        }
        
        orphanedPools.forEach { cacheKey ->
            cleanupPoolByKey(cacheKey)
            poolSessionReferences.remove(cacheKey)
        }
        
        if (orphanedPools.isNotEmpty()) {
            logger.info { "Cleaned up ${orphanedPools.size} orphaned connection pools" }
        }
    }
    
    /**
     * Emergency cleanup of all connection pools
     */
    fun cleanupAllDataSources() {
        val poolCount = dataSourceCache.size
        
        dataSourceCache.values.forEach { dataSource ->
            if (dataSource is HikariDataSource) {
                try {
                    dataSource.close()
                } catch (e: Exception) {
                    logger.warn(e) { "Error closing DataSource during emergency cleanup" }
                }
            }
        }
        
        dataSourceCache.clear()
        poolSessionReferences.clear()
        
        logger.warn { "Emergency cleanup: closed all $poolCount connection pools" }
    }
    
    /**
     * Get connection pool statistics for monitoring
     */
    fun getPoolStatistics(): Map<String, Any> {
        return mapOf(
            "totalPools" to dataSourceCache.size,
            "poolDetails" to dataSourceCache.keys.map { cacheKey ->
                val sessionCount = poolSessionReferences[cacheKey]?.size ?: 0
                val sessionIds = poolSessionReferences[cacheKey]?.toList() ?: emptyList()
                
                mapOf(
                    "pool" to cacheKey,
                    "sessionCount" to sessionCount,
                    "sessionIds" to sessionIds
                )
            }
        )
    }
}

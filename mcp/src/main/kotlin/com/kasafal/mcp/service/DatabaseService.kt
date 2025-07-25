package com.kasafal.mcp.service

import com.kasafal.mcp.exception.DatabaseException
import com.kasafal.mcp.model.database.*
import com.kasafal.mcp.model.session.DatabaseConnectionInfo
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
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
     * Create a DataSource from session token connection info with caching
     */
    fun getDataSourceByInfo(connectionInfo: DatabaseConnectionInfo): DataSource {
        val cacheKey = "${connectionInfo.host}:${connectionInfo.port}:${connectionInfo.database}:${connectionInfo.username}"
        
        return dataSourceCache.computeIfAbsent(cacheKey) {
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

        return HikariDataSource(config)
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
     * Clean up cached DataSources (call when tokens are invalidated)
     */
    fun cleanupDataSource(connectionInfo: DatabaseConnectionInfo) {
        val cacheKey = "${connectionInfo.host}:${connectionInfo.port}:${connectionInfo.database}:${connectionInfo.username}"
        val dataSource = dataSourceCache.remove(cacheKey)
        
        if (dataSource is HikariDataSource) {
            try {
                dataSource.close()
                logger.info { "Closed DataSource for $cacheKey" }
            } catch (e: Exception) {
                logger.warn(e) { "Error closing DataSource for $cacheKey" }
            }
        }
    }
    
    /**
     * Clean up all cached DataSources
     */
    fun cleanupAllDataSources() {
        dataSourceCache.values.forEach { dataSource ->
            if (dataSource is HikariDataSource) {
                try {
                    dataSource.close()
                } catch (e: Exception) {
                    logger.warn(e) { "Error closing DataSource during cleanup" }
                }
            }
        }
        dataSourceCache.clear()
        logger.info { "Cleaned up all cached DataSources" }
    }
}

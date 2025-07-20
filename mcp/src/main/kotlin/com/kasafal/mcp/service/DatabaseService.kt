package com.kasafal.mcp.service

import com.kasafal.mcp.exception.DatabaseException
import com.kasafal.mcp.model.database.*
import com.kasafal.mcp.repository.DatabaseConnectionRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

@Service
class DatabaseService(
    private val connectionRepository: DatabaseConnectionRepository,
    private val credentialService: CredentialService
) {

    private val dataSources = ConcurrentHashMap<Long, DataSource>()

    fun createConnection(dto: DatabaseConnectionDto): DatabaseConnection {
        logger.info { "Creating new database connection: ${dto.name}" }

        // Check if connection already exists
        val existing = connectionRepository.findByNameAndHostAndPortAndDatabaseAndUsernameAndSchema(
            dto.name, dto.host, dto.port, dto.database, dto.username, dto.schema
        )
        if (existing != null) {
            logger.info { "Connection already exists: ${existing.id}" }
            return existing
        }

        // Test connection first
        testConnection(dto)

        val encryptedPassword = credentialService.encrypt(dto.password)

        val connection = DatabaseConnection(
            name = dto.name,
            host = dto.host,
            port = dto.port,
            database = dto.database,
            username = dto.username,
            encryptedPassword = encryptedPassword,
            schema = dto.schema,
            description = dto.description,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        return connectionRepository.save(connection)
    }

    fun getAllConnections(): List<DatabaseConnection> {
        return connectionRepository.findByIsActiveTrue()
    }

    fun getConnection(id: Long): DatabaseConnection {
        return connectionRepository.findById(id).orElseThrow {
            DatabaseException("Database connection not found: $id")
        }
    }

    fun deleteConnection(id: Long) {
        logger.info { "Deleting database connection: $id" }

        // Close existing data source
        dataSources[id]?.let { dataSource ->
            if (dataSource is HikariDataSource) {
                dataSource.close()
            }
        }
        dataSources.remove(id)

        connectionRepository.deleteById(id)
    }

    fun getDataSource(connectionId: Long): DataSource {
        return dataSources.computeIfAbsent(connectionId) { id ->
            val dbConnection = getConnection(id)
            createDataSource(dbConnection)
        }
    }

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

    fun testConnection(connectionId: Long): Boolean {
        val dbConnection = getConnection(connectionId)
        val decryptedPassword = credentialService.decrypt(dbConnection.encryptedPassword)

        return testConnection(DatabaseConnectionDto(
            name = dbConnection.name,
            host = dbConnection.host,
            port = dbConnection.port,
            database = dbConnection.database,
            username = dbConnection.username,
            password = decryptedPassword,
            schema = dbConnection.schema
        ))
    }

    private fun createDataSource(dbConnection: DatabaseConnection): DataSource {
        val decryptedPassword = credentialService.decrypt(dbConnection.encryptedPassword)

        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${dbConnection.host}:${dbConnection.port}/${dbConnection.database}"
            username = dbConnection.username
            password = decryptedPassword
            schema = dbConnection.schema

            // Connection pool settings
            maximumPoolSize = dbConnection.maxConnections
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            leakDetectionThreshold = 60000

            // PostgreSQL specific settings
            addDataSourceProperty("ApplicationName", "PostgreSQL MCP Server")
            addDataSourceProperty("currentSchema", dbConnection.schema)

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

    fun executeQuery(connectionId: Long, sql: String): QueryResult {
        val startTime = System.currentTimeMillis()

        return try {
            getDataSource(connectionId).connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.queryTimeout = getConnection(connectionId).queryTimeout

                    val resultSet = statement.executeQuery()
                    val metaData = resultSet.metaData
                    val columnCount = metaData.columnCount

                    val columns = (1..columnCount).map { metaData.getColumnName(it) }
                    val rows = mutableListOf<Map<String, Any?>>()

                    var rowCount = 0
                    while (resultSet.next() && rowCount < 1000) { // Limit results
                        val row = mutableMapOf<String, Any?>()
                        for (i in 1..columnCount) {
                            row[columns[i - 1]] = resultSet.getObject(i)
                        }
                        rows.add(row)
                        rowCount++
                    }

                    QueryResult(
                        columns = columns,
                        rows = rows,
                        rowCount = rowCount,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "Failed to execute query on connection $connectionId: $sql" }
            throw DatabaseException("Query execution failed: ${e.message}", e)
        }
    }

    fun executeUpdate(connectionId: Long, sql: String): QueryResult {
        val startTime = System.currentTimeMillis()

        return try {
            getDataSource(connectionId).connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.queryTimeout = getConnection(connectionId).queryTimeout
                    val affectedRows = statement.executeUpdate()

                    QueryResult(
                        columns = emptyList(),
                        rows = emptyList(),
                        rowCount = 0,
                        executionTimeMs = System.currentTimeMillis() - startTime,
                        affectedRows = affectedRows
                    )
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "Failed to execute update on connection $connectionId: $sql" }
            throw DatabaseException("Update execution failed: ${e.message}", e)
        }
    }

    fun getConnectionInfo(connectionId: Long): Map<String, Any?> {
        val dbConnection = getConnection(connectionId)

        return mapOf(
            "id" to dbConnection.id,
            "name" to dbConnection.name,
            "host" to dbConnection.host,
            "port" to dbConnection.port,
            "database" to dbConnection.database,
            "username" to dbConnection.username,
            "schema" to dbConnection.schema,
            "isActive" to dbConnection.isActive,
            "createdAt" to dbConnection.createdAt,
            "description" to dbConnection.description
        )
    }

    fun disconnectConnection(connectionId: Long): Boolean {
        return try {
            // Close and remove the DataSource if present
            dataSources[connectionId]?.let { dataSource ->
                if (dataSource is HikariDataSource) {
                    dataSource.close()
                }
            }
            dataSources.remove(connectionId)
            // Mark connection as inactive in repository
            val dbConnection = getConnection(connectionId)
            val updated = dbConnection.copy(isActive = false, updatedAt = LocalDateTime.now())
            connectionRepository.save(updated)
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to disconnect connection $connectionId" }
            false
        }
    }

    fun getConnectionDetails(connectionId: Long): DatabaseConnection? {
        return try {
            getConnection(connectionId)
        } catch (e: Exception) {
            null
        }
    }
}
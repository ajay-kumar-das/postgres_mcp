package com.kasafal.mcp.service

import com.kasafal.mcp.exception.InvalidSQlQueryException
import com.kasafal.mcp.model.database.QueryResult
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

@Service
class ParameterizedQueryService {

    /**
     * Execute a parameterized SELECT query with safe parameter binding
     */
    fun executeParameterizedQuery(
        dataSource: DataSource,
        sql: String,
        parameters: Map<String, Any?> = emptyMap(),
        queryTimeout: Int = 30
    ): QueryResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            dataSource.connection.use { connection ->
                val preparedSql = prepareSqlWithNamedParameters(sql, parameters)
                val parameterValues = extractParameterValues(sql, parameters)
                
                connection.prepareStatement(preparedSql).use { statement ->
                    statement.queryTimeout = queryTimeout
                    
                    // Set parameters safely
                    parameterValues.forEachIndexed { index, value ->
                        setParameterSafely(statement, index + 1, value)
                    }
                    
                    val resultSet = statement.executeQuery()
                    parseResultSet(resultSet, startTime)
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "Failed to execute parameterized query: $sql" }
            throw InvalidSQlQueryException("Query execution failed: ${e.message}", e)
        }
    }

    /**
     * Execute parameterized queries for schema discovery and metadata
     */
    fun executeSchemaQuery(
        dataSource: DataSource,
        sql: String,
        schemaName: String? = null,
        tableName: String? = null,
        queryTimeout: Int = 30
    ): QueryResult {
        val parameters = mutableMapOf<String, Any?>()
        schemaName?.let { parameters["schema_name"] = it }
        tableName?.let { parameters["table_name"] = it }
        
        return executeParameterizedQuery(dataSource, sql, parameters, queryTimeout)
    }

    /**
     * Execute table sampling queries with safe limits
     */
    fun executeSampleQuery(
        dataSource: DataSource,
        schemaName: String,
        tableName: String,
        sampleSize: Int,
        queryTimeout: Int = 30
    ): QueryResult {
        // Validate input parameters
        validateIdentifier(schemaName, "schema")
        validateIdentifier(tableName, "table")
        validateSampleSize(sampleSize)
        
        val sql = """
            SELECT * FROM "${schemaName}"."${tableName}" 
            ORDER BY RANDOM() 
            LIMIT ?
        """.trimIndent()
        
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.queryTimeout = queryTimeout
                    statement.setInt(1, sampleSize)
                    
                    val resultSet = statement.executeQuery()
                    parseResultSet(resultSet, System.currentTimeMillis())
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "Failed to execute sample query for table: $schemaName.$tableName" }
            throw InvalidSQlQueryException("Sample query execution failed: ${e.message}", e)
        }
    }

    /**
     * Execute queries for finding duplicates with parameterized column selection
     */
    fun executeDuplicateQuery(
        dataSource: DataSource,
        schemaName: String,
        tableName: String,
        columns: List<String>,
        queryTimeout: Int = 30
    ): QueryResult {
        // Validate inputs
        validateIdentifier(schemaName, "schema")
        validateIdentifier(tableName, "table")
        if (columns.isEmpty()) {
            throw InvalidSQlQueryException("At least one column must be specified for duplicate detection")
        }
        columns.forEach { validateIdentifier(it, "column") }
        
        val columnList = columns.joinToString(", ") { "\"$it\"" }
        val sql = """
            SELECT $columnList, COUNT(*) as duplicate_count
            FROM "${schemaName}"."${tableName}"
            GROUP BY $columnList
            HAVING COUNT(*) > 1
            ORDER BY COUNT(*) DESC
            LIMIT 100
        """.trimIndent()
        
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.queryTimeout = queryTimeout
                    
                    val resultSet = statement.executeQuery()
                    parseResultSet(resultSet, System.currentTimeMillis())
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "Failed to execute duplicate query for table: $schemaName.$tableName" }
            throw InvalidSQlQueryException("Duplicate query execution failed: ${e.message}", e)
        }
    }

    private fun prepareSqlWithNamedParameters(sql: String, parameters: Map<String, Any?>): String {
        var preparedSql = sql
        
        // Replace named parameters with ? placeholders
        parameters.keys.forEach { paramName ->
            preparedSql = preparedSql.replace(":$paramName", "?")
        }
        
        return preparedSql
    }

    private fun extractParameterValues(sql: String, parameters: Map<String, Any?>): List<Any?> {
        val values = mutableListOf<Any?>()
        
        // Extract parameters in the order they appear in the SQL
        val parameterPattern = """:(\w+)""".toRegex()
        val matches = parameterPattern.findAll(sql)
        
        for (match in matches) {
            val paramName = match.groupValues[1]
            values.add(parameters[paramName])
        }
        
        return values
    }

    private fun setParameterSafely(statement: PreparedStatement, index: Int, value: Any?) {
        when (value) {
            null -> statement.setNull(index, java.sql.Types.NULL)
            is String -> statement.setString(index, value)
            is Int -> statement.setInt(index, value)
            is Long -> statement.setLong(index, value)
            is Double -> statement.setDouble(index, value)
            is Boolean -> statement.setBoolean(index, value)
            is java.sql.Date -> statement.setDate(index, value)
            is java.sql.Timestamp -> statement.setTimestamp(index, value)
            else -> statement.setObject(index, value)
        }
    }

    private fun parseResultSet(resultSet: ResultSet, startTime: Long): QueryResult {
        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount
        
        val columns = (1..columnCount).map { metaData.getColumnName(it) }
        val rows = mutableListOf<Map<String, Any?>>()
        
        var rowCount = 0
        while (resultSet.next() && rowCount < 1000) { // Limit results for safety
            val row = mutableMapOf<String, Any?>()
            for (i in 1..columnCount) {
                row[columns[i - 1]] = resultSet.getObject(i)
            }
            rows.add(row)
            rowCount++
        }
        
        return QueryResult(
            columns = columns,
            rows = rows,
            rowCount = rowCount,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun validateIdentifier(identifier: String, type: String) {
        if (identifier.isBlank()) {
            throw InvalidSQlQueryException("$type name cannot be blank")
        }
        
        // Allow alphanumeric characters, underscores, and hyphens
        if (!identifier.matches("""^[a-zA-Z][a-zA-Z0-9_-]*$""".toRegex())) {
            throw InvalidSQlQueryException("Invalid $type name: $identifier. Only alphanumeric characters, underscores, and hyphens are allowed.")
        }
    }

    private fun validateSampleSize(sampleSize: Int) {
        if (sampleSize < 1) {
            throw InvalidSQlQueryException("Sample size must be at least 1")
        }
        if (sampleSize > 1000) {
            throw InvalidSQlQueryException("Sample size cannot exceed 1000 for performance reasons")
        }
    }
}

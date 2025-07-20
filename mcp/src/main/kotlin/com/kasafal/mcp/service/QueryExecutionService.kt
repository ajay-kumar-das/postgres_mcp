package com.kasafal.mcp.service

import com.kasafal.mcp.exception.DatabaseException
import com.kasafal.mcp.model.database.*
import com.kasafal.mcp.util.SqlValidator
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.sql.SQLException

private val logger = KotlinLogging.logger {}

@Service
class QueryExecutionService(
    private val databaseService: DatabaseService,
    private val sqlValidator: SqlValidator
) {

    fun executeSelectQuery(connectionId: Long, query: String, limit: Int = 100): QueryResult {
        logger.info { "Executing SELECT query on connection $connectionId with limit $limit" }

        // Validate the query
        val validation = sqlValidator.validateSelectQuery(query)
        if (!validation.isValid) {
            throw DatabaseException("Invalid query: ${validation.message}")
        }

        // Add limit if not present
        val limitedQuery = addLimitToQuery(query, limit)

        return databaseService.executeQuery(connectionId, limitedQuery)
    }

    fun executeQuery(connectionId: Long, query: String): QueryResult {
        logger.info { "Executing general query on connection $connectionId" }

        val validation = sqlValidator.validateQuery(query)
        if (!validation.isValid) {
            throw DatabaseException("Invalid query: ${validation.message}")
        }

        return if (sqlValidator.isSelectQuery(query)) {
            databaseService.executeQuery(connectionId, query)
        } else {
            databaseService.executeUpdate(connectionId, query)
        }
    }

    fun explainQuery(connectionId: Long, query: String): ExplainResult {
        logger.info { "Explaining query on connection $connectionId" }

        val validation = sqlValidator.validateSelectQuery(query)
        if (!validation.isValid) {
            throw DatabaseException("Invalid query for EXPLAIN: ${validation.message}")
        }

        val explainQuery = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) $query"

        return try {
            val result = databaseService.executeQuery(connectionId, explainQuery)
            parseExplainResult(result)
        } catch (e: SQLException) {
            logger.error(e) { "Failed to explain query: $query" }
            throw DatabaseException("EXPLAIN failed: ${e.message}", e)
        }
    }

    fun validateQuery(query: String): ValidationResult {
        return sqlValidator.validateQuery(query)
    }

    fun sampleTableData(connectionId: Long, tableName: String, schemaName: String? = null,
                        sampleSize: Int = 10): List<Map<String, Any?>> {
        val dbConnection = databaseService.getConnection(connectionId)
        val targetSchema = schemaName ?: dbConnection.schema

        val query = """
            SELECT * FROM "$targetSchema"."$tableName" 
            ORDER BY RANDOM() 
            LIMIT $sampleSize
        """.trimIndent()

        val result = databaseService.executeQuery(connectionId, query)
        return result.rows
    }

    fun findDuplicates(connectionId: Long, tableName: String, columns: List<String>,
                       schemaName: String? = null): List<Map<String, Any?>> {
        if (columns.isEmpty()) {
            throw DatabaseException("At least one column must be specified for duplicate detection")
        }

        val dbConnection = databaseService.getConnection(connectionId)
        val targetSchema = schemaName ?: dbConnection.schema

        val columnList = columns.joinToString(", ") { "\"$it\"" }
        val query = """
            SELECT $columnList, COUNT(*) as duplicate_count
            FROM "$targetSchema"."$tableName"
            GROUP BY $columnList
            HAVING COUNT(*) > 1
            ORDER BY COUNT(*) DESC
            LIMIT 100
        """.trimIndent()

        val result = databaseService.executeQuery(connectionId, query)
        return result.rows
    }

    fun analyzeDataQuality(connectionId: Long, tableName: String, schemaName: String? = null): DataQualityReport {
        val dbConnection = databaseService.getConnection(connectionId)
        val targetSchema = schemaName ?: dbConnection.schema

        // Get total row count
        val totalRowsQuery = "SELECT COUNT(*) as total_rows FROM \"$targetSchema\".\"$tableName\""
        val totalRowsResult = databaseService.executeQuery(connectionId, totalRowsQuery)
        val totalRows = (totalRowsResult.rows.firstOrNull()?.get("total_rows") as? Number)?.toLong() ?: 0L

        if (totalRows == 0L) {
            return DataQualityReport(tableName, 0L, emptyList())
        }

        // Get table schema to analyze each column
        val schemaDiscoveryService = SchemaDiscoveryService(databaseService)
        val tableSchema = schemaDiscoveryService.describeTable(connectionId, tableName, targetSchema)

        val issues = mutableListOf<DataQualityIssue>()

        // Analyze each column for data quality issues
        for (column in tableSchema.columns) {
            issues.addAll(analyzeColumnQuality(connectionId, targetSchema, tableName, column, totalRows))
        }

        return DataQualityReport(tableName, totalRows, issues)
    }

    private fun analyzeColumnQuality(connectionId: Long, schemaName: String, tableName: String,
                                     column: ColumnInfo, totalRows: Long): List<DataQualityIssue> {
        val issues = mutableListOf<DataQualityIssue>()

        try {
            // Check for null values
            if (column.isNullable) {
                val nullQuery = """
                    SELECT COUNT(*) as null_count 
                    FROM "$schemaName"."$tableName" 
                    WHERE "${column.name}" IS NULL
                """.trimIndent()

                val nullResult = databaseService.executeQuery(connectionId, nullQuery)
                val nullCount = (nullResult.rows.firstOrNull()?.get("null_count") as? Number)?.toLong() ?: 0L

                if (nullCount > 0) {
                    val percentage = (nullCount.toDouble() / totalRows) * 100
                    issues.add(DataQualityIssue(
                        type = "null_values",
                        column = column.name,
                        count = nullCount,
                        percentage = percentage,
                        description = "Column contains null values"
                    ))
                }
            }

            // Check for empty strings in text columns
            if (column.dataType in listOf("text", "varchar", "character varying", "char")) {
                val emptyQuery = """
                    SELECT COUNT(*) as empty_count 
                    FROM "$schemaName"."$tableName" 
                    WHERE "${column.name}" = '' OR "${column.name}" IS NULL
                """.trimIndent()

                val emptyResult = databaseService.executeQuery(connectionId, emptyQuery)
                val emptyCount = (emptyResult.rows.firstOrNull()?.get("empty_count") as? Number)?.toLong() ?: 0L

                if (emptyCount > 0) {
                    val percentage = (emptyCount.toDouble() / totalRows) * 100
                    issues.add(DataQualityIssue(
                        type = "empty_values",
                        column = column.name,
                        count = emptyCount,
                        percentage = percentage,
                        description = "Column contains empty or null values"
                    ))
                }
            }

            // Check for potential duplicates in unique-looking columns
            if (column.name.lowercase().contains("id") || column.name.lowercase().contains("email")) {
                val duplicateQuery = """
                    SELECT COUNT(*) - COUNT(DISTINCT "${column.name}") as duplicate_count
                    FROM "$schemaName"."$tableName"
                    WHERE "${column.name}" IS NOT NULL
                """.trimIndent()

                val duplicateResult = databaseService.executeQuery(connectionId, duplicateQuery)
                val duplicateCount = (duplicateResult.rows.firstOrNull()?.get("duplicate_count") as? Number)?.toLong() ?: 0L

                if (duplicateCount > 0) {
                    val percentage = (duplicateCount.toDouble() / totalRows) * 100
                    issues.add(DataQualityIssue(
                        type = "duplicates",
                        column = column.name,
                        count = duplicateCount,
                        percentage = percentage,
                        description = "Column contains duplicate values (expected to be unique)"
                    ))
                }
            }

        } catch (e: Exception) {
            logger.warn(e) { "Failed to analyze column ${column.name} for data quality" }
        }

        return issues
    }

    private fun addLimitToQuery(query: String, limit: Int): String {
        val normalizedQuery = query.trim().lowercase()

        // Check if LIMIT is already present
        if (normalizedQuery.contains("limit")) {
            return query
        }

        // Add LIMIT clause
        return "$query LIMIT $limit"
    }

    private fun parseExplainResult(result: QueryResult): ExplainResult {
        return try {
            // PostgreSQL EXPLAIN with JSON format returns a single row with JSON
            val jsonString = result.rows.firstOrNull()?.values?.firstOrNull()?.toString()

            if (jsonString != null) {
                // Simple parsing - in production, use a JSON library
                val planningTimeRegex = """"Planning Time": ([\d.]+)""".toRegex()
                val executionTimeRegex = """"Execution Time": ([\d.]+)""".toRegex()
                val totalCostRegex = """"Total Cost": ([\d.]+)""".toRegex()
                val actualRowsRegex = """"Actual Rows": (\d+)""".toRegex()

                ExplainResult(
                    executionPlan = jsonString,
                    actualRows = actualRowsRegex.find(jsonString)?.groupValues?.get(1)?.toLongOrNull(),
                    planningTime = planningTimeRegex.find(jsonString)?.groupValues?.get(1)?.toDoubleOrNull(),
                    executionTime = executionTimeRegex.find(jsonString)?.groupValues?.get(1)?.toDoubleOrNull(),
                    totalCost = totalCostRegex.find(jsonString)?.groupValues?.get(1)?.toDoubleOrNull()
                )
            } else {
                ExplainResult(
                    executionPlan = "No execution plan available",
                    actualRows = null,
                    planningTime = null,
                    executionTime = null,
                    totalCost = null
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse EXPLAIN result" }
            ExplainResult(
                executionPlan = "Failed to parse execution plan: ${e.message}",
                actualRows = null,
                planningTime = null,
                executionTime = null,
                totalCost = null
            )
        }
    }
}
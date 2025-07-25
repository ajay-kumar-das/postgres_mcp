package com.kasafal.mcp.service

import com.kasafal.mcp.exception.InvalidSQlQueryException
import com.kasafal.mcp.model.database.*
import com.kasafal.mcp.model.session.DatabaseOperation
import com.kasafal.mcp.util.SqlValidator
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.sql.SQLException

private val logger = KotlinLogging.logger {}

@Service
class QueryExecutionService(
    private val databaseService: DatabaseService,
    private val sqlValidator: SqlValidator,
    private val parameterizedQueryService: ParameterizedQueryService,
    private val sessionTokenService: SessionTokenService,
    private val schemaDiscoveryService: SchemaDiscoveryService
) {

    /**
     * Execute SELECT query using session token (secure method)
     */
    fun executeSelectUsingToken(tokenId: String, query: String, limit: Int = 100): QueryResult {
        logger.info { "Executing SELECT query using token with limit $limit" }

        // Runtime monitoring: log suspicious queries
        if (query.contains("pg_", ignoreCase = true) || query.contains("information_schema", ignoreCase = true) || query.contains("union", ignoreCase = true)) {
            logger.warn { "Suspicious query detected: $query" }
        }

        // Advanced analysis hooks (Phase 2)
        runAdvancedAnalysis(query)

        // Validate the query
        val validation = sqlValidator.validateSelectQuery(query)
        if (!validation.isValid) {
            logger.warn { "Alert: Risky sql query - $query"}
            throw InvalidSQlQueryException("Invalid query: ${validation.message}")
        }

        // Add limit if not present
        val limitedQuery = addLimitToQuery(query, limit)

        return executeQueryUsingToken(tokenId, limitedQuery, DatabaseOperation.SELECT_QUERIES)
    }

    fun executeQueryUsingToken(tokenId: String, query: String, requiredOperation: DatabaseOperation): QueryResult {
        val validation = sessionTokenService.validateAndUseToken(tokenId, requiredOperation)
        if (!validation.isValid) {
            throw InvalidSQlQueryException("Token validation failed: ${validation.errorMessage}")
        }
        
        val connectionInfo = validation.connectionInfo ?: throw InvalidSQlQueryException("No connection info found for token")
        val dataSource = databaseService.getDataSourceByInfo(connectionInfo)
        
        logger.info { "Executing query using token on database: ${connectionInfo.name}" }

        val validationResponse = sqlValidator.validateQuery(query)
        if (!validationResponse.isValid) {
            throw InvalidSQlQueryException("Invalid query: ${validationResponse.message}")
        }

        // Check if query is parameterized and route to appropriate execution method
        return if (isQueryParameterized(query)) {
            logger.debug { "Using parameterized query execution for query with parameters" }
            parameterizedQueryService.executeParameterizedQuery(
                dataSource, query, emptyMap(), connectionInfo.queryTimeoutSeconds
            )
        } else {
            logger.debug { "Using direct query execution for non-parameterized query" }
            databaseService.executeQuery(
                dataSource, query, connectionInfo.queryTimeoutSeconds
            )
        }
    }

    // --- Security Feature Hooks ---
    private fun isQueryParameterized(query: String): Boolean {
        // Basic check: look for parameter placeholders (e.g., ? or $1)
        return query.contains("?") || Regex("\\$\\d+").containsMatchIn(query)
    }

    private fun runAdvancedAnalysis(query: String) {
        // --- Phase 2: Content-based analysis ---
        if (query.length > 1000) {
            logger.warn { "Query is unusually long and may be suspicious: $query" }
        }
        if (query.contains("--") || query.contains("/*") || query.contains("#")) {
            logger.warn { "Query contains suspicious comment patterns: $query" }
        }
        if (Regex("(?i)select\\s+\\*\\s+from").containsMatchIn(query)) {
            logger.info { "Query uses SELECT *; recommend column selection for security and performance." }
        }

        // --- ML-based detection (placeholder) ---
        // Example: If you have an ML model, call it here
        // val mlScore = mlModel.score(query)
        // if (mlScore > 0.8) logger.warn { "ML model flagged query as suspicious: $query" }

        // --- Pattern recognition ---
        val riskyPatterns = listOf(
            "sleep(", "benchmark(", "waitfor delay", "extractvalue(", "updatexml(", "load_file(", "outfile"
        )
        for (pattern in riskyPatterns) {
            if (query.contains(pattern, ignoreCase = true)) {
                logger.warn { "Query contains risky pattern '$pattern': $query" }
            }
        }
    }


    fun explainQueryUsingToken(tokenId: String, query: String): ExplainResult {
        logger.info { "Explaining query using token" }

        val validation = sqlValidator.validateSelectQuery(query)
        if (!validation.isValid) {
            throw InvalidSQlQueryException("Invalid query for EXPLAIN: ${validation.message}")
        }

        val explainQuery = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) $query"

        return try {
            val result = executeQueryUsingToken(tokenId, explainQuery, DatabaseOperation.SELECT_QUERIES)
            parseExplainResult(result)
        } catch (e: SQLException) {
            logger.error(e) { "Failed to explain query: $query" }
            throw InvalidSQlQueryException("EXPLAIN failed: ${e.message}", e)
        }
    }


    fun validateQuery(query: String): ValidationResult {
        return sqlValidator.validateQuery(query)
    }

    fun sampleTableDataUsingToken(tokenId: String, tableName: String, schemaName: String? = null,
                        sampleSize: Int = 10): List<Map<String, Any?>> {
        val validation = sessionTokenService.validateAndUseToken(tokenId, DatabaseOperation.SELECT_QUERIES)
        if (!validation.isValid) {
            throw InvalidSQlQueryException("Token validation failed: ${validation.errorMessage}")
        }
        
        val connectionInfo = validation.connectionInfo ?: throw InvalidSQlQueryException("No connection info found for token")
        val targetSchema = schemaName ?: connectionInfo.schema
        val dataSource = databaseService.getDataSourceByInfo(connectionInfo)
        
        val result = parameterizedQueryService.executeSampleQuery(
            dataSource, targetSchema, tableName, sampleSize, connectionInfo.queryTimeoutSeconds
        )
        return result.rows
    }


    fun findDuplicatesUsingToken(tokenId: String, tableName: String, columns: List<String>,
                       schemaName: String? = null): List<Map<String, Any?>> {
        val validation = sessionTokenService.validateAndUseToken(tokenId, DatabaseOperation.SELECT_QUERIES)
        if (!validation.isValid) {
            throw InvalidSQlQueryException("Token validation failed: ${validation.errorMessage}")
        }
        
        val connectionInfo = validation.connectionInfo ?: throw InvalidSQlQueryException("No connection info found for token")
        val targetSchema = schemaName ?: connectionInfo.schema
        val dataSource = databaseService.getDataSourceByInfo(connectionInfo)
        
        val result = parameterizedQueryService.executeDuplicateQuery(
            dataSource, targetSchema, tableName, columns, connectionInfo.queryTimeoutSeconds
        )
        return result.rows
    }


    fun analyzeDataQualityUsingToken(tokenId: String, tableName: String, schemaName: String? = null): DataQualityReport {
        val validation = sessionTokenService.validateAndUseToken(tokenId, DatabaseOperation.SELECT_QUERIES)
        if (!validation.isValid) {
            throw InvalidSQlQueryException("Token validation failed: ${validation.errorMessage}")
        }
        
        val connectionInfo = validation.connectionInfo ?: throw InvalidSQlQueryException("No connection info found for token")
        val targetSchema = schemaName ?: connectionInfo.schema

        // Get total row count
        val totalRowsQuery = "SELECT COUNT(*) as total_rows FROM \"$targetSchema\".\"$tableName\""
        val totalRowsResult = executeQueryUsingToken(tokenId, totalRowsQuery, DatabaseOperation.SELECT_QUERIES)
        val totalRows = (totalRowsResult.rows.firstOrNull()?.get("total_rows") as? Number)?.toLong() ?: 0L

        if (totalRows == 0L) {
            return DataQualityReport(tableName, 0L, emptyList())
        }

        // Get table schema to analyze each column
        val tableSchema = schemaDiscoveryService.describeTableUsingToken(tokenId, tableName, targetSchema)

        val issues = mutableListOf<DataQualityIssue>()

        // Analyze each column for data quality issues
        for (column in tableSchema.columns) {
            issues.addAll(analyzeColumnQualityUsingToken(tokenId, targetSchema, tableName, column, totalRows))
        }

        return DataQualityReport(tableName, totalRows, issues)
    }


    private fun analyzeColumnQualityUsingToken(tokenId: String, schemaName: String, tableName: String,
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

                val nullResult = executeQueryUsingToken(tokenId, nullQuery, DatabaseOperation.SELECT_QUERIES)
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

                val emptyResult = executeQueryUsingToken(tokenId, emptyQuery, DatabaseOperation.SELECT_QUERIES)
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

                val duplicateResult = executeQueryUsingToken(tokenId, duplicateQuery, DatabaseOperation.SELECT_QUERIES)
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
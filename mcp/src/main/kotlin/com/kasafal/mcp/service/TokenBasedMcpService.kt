package com.kasafal.mcp.service

import com.kasafal.mcp.exception.McpException
import com.kasafal.mcp.model.mcp.*
import com.kasafal.mcp.model.session.DatabaseOperation
import com.kasafal.mcp.util.SqlValidator
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Token-based MCP service that uses session tokens instead of persistent database connections.
 * This ensures that AI never has direct access to database credentials.
 */
@Service
class TokenBasedMcpService(
    private val sessionTokenService: SessionTokenService,
    private val queryExecutionService: QueryExecutionService,
    private val schemaDiscoveryService: SchemaDiscoveryService,
    private val sqlValidator: SqlValidator,
    @Value("\${server.port:8080}") private val serverPort: Int
) {

    // Tools that don't require database credentials/tokens
    private val tokenFreeTools = setOf("validate_sql", "get_token_ui_redirect")

    private val tools = mapOf(
        "execute_query" to McpTool(
            name = "execute_query",
            description = "Execute a SELECT query using session token (secure method)",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "query" to mapOf("type" to "string", "description" to "SELECT query to execute"),
                    "limit" to mapOf("type" to "integer", "description" to "Maximum number of rows to return", "default" to 100)
                ),
                "required" to listOf("token_id", "query")
            )
        ),

        "sample_table_data" to McpTool(
            name = "sample_table_data",
            description = "Get a random sample of data from a table using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)"),
                    "sample_size" to mapOf("type" to "integer", "description" to "Number of sample rows", "default" to 10)
                ),
                "required" to listOf("token_id", "table_name")
            )
        ),

        "discover_schema" to McpTool(
            name = "discover_schema",
            description = "Discover database schema information using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("token_id")
            )
        ),

        "list_tables" to McpTool(
            name = "list_tables",
            description = "List all tables in the database schema using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("token_id")
            )
        ),

        "describe_table" to McpTool(
            name = "describe_table",
            description = "Get detailed information about a specific table using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("token_id", "table_name")
            )
        ),

        "find_duplicates" to McpTool(
            name = "find_duplicates",
            description = "Find duplicate records in a table using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "columns" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Columns to check for duplicates"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("token_id", "table_name", "columns")
            )
        ),

        "analyze_data_quality" to McpTool(
            name = "analyze_data_quality",
            description = "Analyze data quality issues in a table using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("token_id", "table_name")
            )
        ),

        "validate_sql" to McpTool(
            name = "validate_sql",
            description = "Validate SQL syntax and check for potential issues",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "SQL query to validate")
                ),
                "required" to listOf("query")
            )
        ),

        "get_table_stats" to McpTool(
            name = "get_table_stats",
            description = "Get statistical information about a table using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("token_id", "table_name")
            )
        ),

        "explain_query" to McpTool(
            name = "explain_query",
            description = "Get the execution plan for a SELECT query using session token",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID"),
                    "query" to mapOf("type" to "string", "description" to "SELECT query to explain")
                ),
                "required" to listOf("token_id", "query")
            )
        ),

        "disconnect_database" to McpTool(
            name = "disconnect_database",
            description = "Invalidate a session token and clean up associated resources",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "token_id" to mapOf("type" to "string", "description" to "Session token ID to invalidate")
                ),
                "required" to listOf("token_id")
            )
        ),
        
        "get_token_ui_redirect" to McpTool(
            name = "get_token_ui_redirect",
            description = "Get a redirect URL to the token generation UI when database access is needed",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "purpose" to mapOf("type" to "string", "description" to "Purpose for which the token is needed (optional)"),
                    "requested_operations" to mapOf(
                        "type" to "array", 
                        "items" to mapOf("type" to "string"),
                        "description" to "List of database operations needed (optional)"
                    )
                ),
                "required" to emptyList<String>()
            )
        )
    )

    fun handleMcpRequest(request: McpRequest): McpResponse {
        logger.info { "Handling token-based MCP request: ${request.method}" }

        return try {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> listTools(request)
                "tools/call" -> callTool(request)
                else -> errorResponse(request.id ?: "unknown", "Unknown method: ${request.method}", -32601)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling MCP request: ${request.method}" }
            errorResponse(request.id ?: "error", "Internal error: ${e.message}", -32603)
        }
    }

    private fun handleInitialize(request: McpRequest): McpResponse {
        val result = mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf(
                "logging" to emptyMap<String, Any>(),
                "tools" to mapOf("listChanged" to true)
            ),
            "serverInfo" to mapOf(
                "name" to "PostgreSQL MCP Server (Token-Based)",
                "version" to "2.0.0"
            )
        )

        return McpResponse(id = request.id, result = result)
    }

    private fun listTools(request: McpRequest): McpResponse {
        return McpResponse(id = request.id, result = mapOf("tools" to tools.values.toList()))
    }

    fun listTools(): McpResponse {
        return McpResponse(id = Math.random().toString(), result = mapOf("tools" to tools.values.toList()))
    }

    private fun callTool(request: McpRequest): McpResponse {
        val params = request.params ?: return errorResponse(request.id, "Missing parameters")
        val toolName = params["name"] as? String ?: return errorResponse(request.id, "Missing tool name")
        val arguments = params["arguments"] as? Map<String, Any> ?: emptyMap()

        logger.info { "Calling token-based tool: $toolName with arguments: $arguments" }

        return try {
            val result = when (toolName) {
                "execute_query" -> executeQuery(arguments)
                "sample_table_data" -> sampleTableData(arguments)
                "discover_schema" -> discoverSchema(arguments)
                "list_tables" -> listTables(arguments)
                "describe_table" -> describeTable(arguments)
                "find_duplicates" -> findDuplicates(arguments)
                "analyze_data_quality" -> analyzeDataQuality(arguments)
                "get_table_stats" -> getTableStats(arguments)
                "validate_sql" -> validateSql(arguments)
                "explain_query" -> explainQuery(arguments)
                "disconnect_database" -> disconnectDatabase(arguments)
                "get_token_ui_redirect" -> getTokenUiRedirect(arguments)
                else -> throw McpException("Unknown tool: $toolName")
            }

            McpResponse(
                id = request.id,
                result = mapOf(
                    "content" to listOf(
                        mapOf(
                            "type" to "text",
                            "text" to formatToolResult(result)
                        )
                    )
                )
            )
        } catch (e: Exception) {
            logger.error(e.message) { "Error executing token-based tool: $toolName" }
            errorResponse(request.id, "Tool execution failed: ${e.message}")
        }
    }

    private fun executeQuery(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val query = arguments["query"] as? String
            ?: throw McpException("Missing query")
        val limit = (arguments["limit"] as? Number)?.toInt() ?: 100

        return queryExecutionService.executeSelectUsingToken(tokenId, query, limit)
    }

    private fun sampleTableData(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String
        val sampleSize = (arguments["sample_size"] as? Number)?.toInt() ?: 10

        return queryExecutionService.sampleTableDataUsingToken(tokenId, tableName, schemaName, sampleSize)
    }

    private fun discoverSchema(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.discoverDatabaseSchemaUsingToken(tokenId, schemaName)
    }

    private fun listTables(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.listTablesUsingToken(tokenId, schemaName)
    }

    private fun describeTable(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.describeTableUsingToken(tokenId, tableName, schemaName)
    }

    private fun findDuplicates(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val columns = arguments["columns"] as? List<String>
            ?: throw McpException("Missing columns")
        val schemaName = arguments["schema_name"] as? String

        return queryExecutionService.findDuplicatesUsingToken(tokenId, tableName, columns, schemaName)
    }

    private fun analyzeDataQuality(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return queryExecutionService.analyzeDataQualityUsingToken(tokenId, tableName, schemaName)
    }

    private fun getTableStats(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.getTableStatisticsUsingToken(tokenId, tableName, schemaName)
    }

    private fun validateSql(arguments: Map<String, Any>): Any {
        val query = arguments["query"] as? String
            ?: throw McpException("Missing query")

        return sqlValidator.validateQuery(query)
    }

    private fun explainQuery(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")
        val query = arguments["query"] as? String
            ?: throw McpException("Missing query")

        val explainQuery = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON) $query"
        
        return queryExecutionService.executeQueryUsingToken(tokenId, explainQuery, DatabaseOperation.EXPLAIN_QUERIES)
    }

    private fun disconnectDatabase(arguments: Map<String, Any>): Any {
        val tokenId = arguments["token_id"] as? String
            ?: throw McpException("Missing token_id")

        // Invalidate the session token and clean up resources
        sessionTokenService.invalidateToken(tokenId)
        
        return mapOf(
            "success" to true,
            "message" to "Session token '$tokenId' has been invalidated and database connection resources cleaned up",
            "token_id" to tokenId
        )
    }
    
    private fun getTokenUiRedirect(arguments: Map<String, Any>): Any {
        val purpose = arguments["purpose"] as? String ?: "Database access"
        val requestedOperations = arguments["requested_operations"] as? List<String> ?: listOf("SELECT_QUERIES", "SCHEMA_DISCOVERY")
        
        val uiUrl = "http://localhost:$serverPort/token-ui"
        val encodedPurpose = java.net.URLEncoder.encode(purpose, "UTF-8")
        val encodedOperations = requestedOperations.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
        
        val fullUrl = "$uiUrl?purpose=$encodedPurpose&operations=$encodedOperations"
        
        return mapOf(
            "redirect_url" to fullUrl,
            "message" to "Please visit the following URL to generate a database access token:",
            "instructions" to listOf(
                "1. Click on the URL below to open the token generation interface",
                "2. Enter your database connection details",
                "3. Configure the required permissions: ${requestedOperations.joinToString(", ")}",
                "4. Copy the generated token",
                "5. Use the token in your database operations"
            ),
            "purpose" to purpose,
            "requested_operations" to requestedOperations
        )
    }

    private fun formatToolResult(result: Any): String {
        return when (result) {
            is Map<*, *> -> formatMapResult(result)
            is List<*> -> formatListResult(result)
            else -> result.toString()
        }
    }

    private fun formatMapResult(map: Map<*, *>): String {
        val sb = StringBuilder()
        for ((key, value) in map) {
            sb.append("$key: ")
            when (value) {
                is Map<*, *> -> sb.append("{\n${formatMapResult(value).prependIndent("  ")}\n}")
                is List<*> -> sb.append("[\n${formatListResult(value).prependIndent("  ")}\n]")
                else -> sb.append(value.toString())
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun formatListResult(list: List<*>): String {
        if (list.isEmpty()) return "[]"

        val sb = StringBuilder()
        list.forEachIndexed { index, item ->
            sb.append("[$index] ")
            when (item) {
                is Map<*, *> -> sb.append("{\n${formatMapResult(item).prependIndent("  ")}\n}")
                is List<*> -> sb.append("[\n${formatListResult(item).prependIndent("  ")}\n]")
                else -> sb.append(item.toString())
            }
            if (index < list.size - 1) sb.append("\n")
        }
        return sb.toString()
    }

    private fun errorResponse(id: String?, message: String, code: Int = -32000): McpResponse {
        return McpResponse(id = id, error = McpError(message, code))
    }
}

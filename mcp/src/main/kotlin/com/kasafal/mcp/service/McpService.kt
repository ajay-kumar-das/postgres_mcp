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
 * Main MCP service that handles all MCP protocol requests using session-based authentication.
 */
@Service
class McpService(
    private val sessionAuthenticationService: SessionAuthenticationService,
    private val queryExecutionService: QueryExecutionService,
    private val schemaDiscoveryService: SchemaDiscoveryService,
    private val sqlValidator: SqlValidator,
    @Value("\${server.port:8080}") private val serverPort: Int
) {

    private val tools = mapOf(
        "execute_query" to McpTool(
            name = "execute_query",
            description = "Execute a SELECT query using session authentication",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID from start_oauth_flow"),
                    "query" to mapOf("type" to "string", "description" to "SELECT query to execute"),
                    "limit" to mapOf("type" to "integer", "description" to "Maximum rows to return", "default" to 100)
                ),
                "required" to listOf("session_id", "query")
            )
        ),

        "sample_table_data" to McpTool(
            name = "sample_table_data",
            description = "Get random sample data from a table",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID from start_oauth_flow"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    // "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)"),
                    "sample_size" to mapOf("type" to "integer", "description" to "Number of sample rows", "default" to 10)
                ),
                "required" to listOf("session_id", "table_name")
            )
        ),

        "discover_schema" to McpTool(
            name = "discover_schema",
            description = "Discover database schema information",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID from start_oauth_flow")
                    // "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("session_id")
            )
        ),

        "list_tables" to McpTool(
            name = "list_tables",
            description = "List all tables in the database schema",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID from start_oauth_flow")
                    // "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("session_id")
            )
        ),

        "describe_table" to McpTool(
            name = "describe_table",
            description = "Get detailed information about a specific table",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID from start_oauth_flow"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name")
                    // "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("session_id", "table_name")
            )
        ),

        "find_duplicates" to McpTool(
            name = "find_duplicates",
            description = "Find duplicate records in a table",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID from start_oauth_flow"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "columns" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Columns to check for duplicates")
                    // "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("session_id", "table_name", "columns")
            )
        ),

        "analyze_data_quality" to McpTool(
            name = "analyze_data_quality",
            description = "Analyze data quality issues in a table",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID from start_oauth_flow"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name")
                    // "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("session_id", "table_name")
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

        "disconnect_session" to McpTool(
            name = "disconnect_session",
            description = "Invalidate a session and clean up resources",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "session_id" to mapOf("type" to "string", "description" to "Session ID to invalidate")
                ),
                "required" to listOf("session_id")
            )
        ),

        "start_oauth_flow" to McpTool(
            name = "start_oauth_flow",
            description = "Start OAuth-style authentication flow for database access. For Claude conversations, use 'claude-desktop' as the source.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "purpose" to mapOf(
                        "type" to "string",
                        "description" to "Purpose for database access",
                    ),
                    "source" to mapOf(
                        "type" to "string",
                        "description" to "Source of the request for tracking purposes. Examples: 'claude-desktop', 'vscode', 'api details', 'web-site-name', 'mobile-app-name', 'custom-integration-name'",
                        "examples" to listOf("claude-desktop", "vscode", "api details", "web site name")
                    )
                ),
                "required" to listOf("purpose","source")
            )
        )
    )

    fun handleRequest(request: McpRequest): McpResponse {
        logger.info { "Handling MCP request: ${request.method}" }

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

    fun listTools(): McpResponse {
        return McpResponse(id = "tools", result = mapOf("tools" to tools.values.toList()))
    }

    private fun handleInitialize(request: McpRequest): McpResponse {
        val result = mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf(
                "logging" to emptyMap<String, Any>(),
                "tools" to mapOf("listChanged" to true)
            ),
            "serverInfo" to mapOf(
                "name" to "PostgreSQL MCP Server",
                "version" to "2.0.0"
            )
        )
        return McpResponse(id = request.id, result = result)
    }

    private fun listTools(request: McpRequest): McpResponse {
        return McpResponse(id = request.id, result = mapOf("tools" to tools.values.toList()))
    }

    private fun callTool(request: McpRequest): McpResponse {
        val params = request.params ?: return errorResponse(request.id, "Missing parameters")
        val toolName = params["name"] as? String ?: return errorResponse(request.id, "Missing tool name")
        val arguments = params["arguments"] as? Map<String, Any> ?: emptyMap()

        logger.info { "Calling tool: $toolName" }

        return try {
            val result = when (toolName) {
                "execute_query" -> executeQuery(arguments)
                "sample_table_data" -> sampleTableData(arguments)
                "discover_schema" -> discoverSchema(arguments)
                "list_tables" -> listTables(arguments)
                "describe_table" -> describeTable(arguments)
                "find_duplicates" -> findDuplicates(arguments)
                "analyze_data_quality" -> analyzeDataQuality(arguments)
                "validate_sql" -> validateSql(arguments)
                "disconnect_session" -> disconnectSession(arguments)
                "start_oauth_flow" -> startOAuthFlow(arguments)
                else -> throw McpException("Unknown tool: $toolName")
            }

            McpResponse(
                id = request.id,
                result = mapOf(
                    "content" to listOf(
                        mapOf(
                            "type" to "text",
                            "text" to formatResult(result)
                        )
                    )
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error executing tool: $toolName" }
            errorResponse(request.id, "Tool execution failed: ${e.message}")
        }
    }

    private fun executeQuery(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        val query = arguments["query"] as? String ?: throw McpException("Missing query")
        val limit = (arguments["limit"] as? Number)?.toInt() ?: 100

        return queryExecutionService.executeSelectUsingSession(sessionId, query, limit)
    }

    private fun sampleTableData(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        val tableName = arguments["table_name"] as? String ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String
        val sampleSize = (arguments["sample_size"] as? Number)?.toInt() ?: 10

        return queryExecutionService.sampleTableDataUsingSession(sessionId, tableName, schemaName, sampleSize)
    }

    private fun discoverSchema(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.discoverDatabaseSchemaUsingSession(sessionId, schemaName)
    }

    private fun listTables(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.listTablesUsingSession(sessionId, schemaName)
    }

    private fun describeTable(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        val tableName = arguments["table_name"] as? String ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.describeTableUsingSession(sessionId, tableName, schemaName)
    }

    private fun findDuplicates(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        val tableName = arguments["table_name"] as? String ?: throw McpException("Missing table_name")
        val columns = arguments["columns"] as? List<String> ?: throw McpException("Missing columns")
        val schemaName = arguments["schema_name"] as? String

        return queryExecutionService.findDuplicatesUsingSession(sessionId, tableName, columns, schemaName)
    }

    private fun analyzeDataQuality(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        val tableName = arguments["table_name"] as? String ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return queryExecutionService.analyzeDataQualityUsingSession(sessionId, tableName, schemaName)
    }

    private fun validateSql(arguments: Map<String, Any>): Any {
        val query = arguments["query"] as? String ?: throw McpException("Missing query")
        return sqlValidator.validateQuery(query)
    }

    private fun disconnectSession(arguments: Map<String, Any>): Any {
        val sessionId = arguments["session_id"] as? String ?: throw McpException("Missing session_id")
        
        val success = sessionAuthenticationService.invalidateSession(sessionId)
        return mapOf(
            "success" to success,
            "message" to if (success) "Session invalidated successfully" else "Session not found",
            "session_id" to sessionId
        )
    }

    private fun startOAuthFlow(arguments: Map<String, Any>): Any {
        val purpose = arguments["purpose"] as? String ?: "database_access"
        val source = arguments["source"] as? String ?: "unknown"
        
        val sessionResponse = sessionAuthenticationService.createSession(purpose, source)
        val loginUrl = "http://localhost:$serverPort/api/auth/login?session_id=${sessionResponse.sessionId}&source=${source}"
        
        logger.info { "OAuth flow initiated for session: ${sessionResponse.sessionId} from source: $source" }
        
        return mapOf(
            "oauth_status" to "initiated",
            "session_id" to sessionResponse.sessionId,
            "login_url" to loginUrl,
            "purpose" to purpose,
            "source" to source,
            "message" to "Please visit the login URL to authenticate your database access.",
            "instructions" to listOf(
                "1. Click the login_url to open the authentication page in your browser",
                "2. Enter your PostgreSQL database credentials",
                "3. Submit the form to complete authentication",
                "4. Return here and use the session_id for database operations"
            ),
            "example_usage" to mapOf(
                "tool" to "execute_query",
                "arguments" to mapOf(
                    "session_id" to sessionResponse.sessionId,
                    "query" to "SELECT * FROM your_table LIMIT 10"
                )
            ),
            "tracking_info" to mapOf(
                "session_created_at" to java.time.LocalDateTime.now().toString(),
                "source_application" to source,
                "access_purpose" to purpose,
                "expires_at" to sessionResponse.expiresAt.toString()
            )
        )
    }

    private fun formatResult(result: Any): String {
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

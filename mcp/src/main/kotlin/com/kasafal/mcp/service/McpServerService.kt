package com.kasafal.mcp.service

import com.kasafal.mcp.exception.McpException
import com.kasafal.mcp.model.database.DatabaseConnectionDto
import com.kasafal.mcp.model.mcp.*
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class McpServerService(
    private val databaseService: DatabaseService,
    private val schemaDiscoveryService: SchemaDiscoveryService,
    private val queryExecutionService: QueryExecutionService,
    private val promptService: PromptService
) {

    private val tools = mapOf(
        "connect_database" to McpTool(
            name = "connect_database",
            description = "Connect to a PostgreSQL database to get connection id",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "Connection name")/*,
                    "host" to mapOf("type" to "string", "description" to "Database host"),
                    "port" to mapOf("type" to "integer", "description" to "Database port", "default" to 5432),
                    "database" to mapOf("type" to "string", "description" to "Database name"),
                    "username" to mapOf("type" to "string", "description" to "Database username"),
                    "password" to mapOf("type" to "string", "description" to "Database password"),
                    "schema" to mapOf("type" to "string", "description" to "Default schema", "default" to "public"),
                    "description" to mapOf("type" to "string", "description" to "Connection description")*/
                ),
                "required" to listOf("name")//, "host", "database", "username", "password")
            )
        ),

       /* "list_connections" to McpTool(
            name = "list_connections",
            description = "List all available database connections",
            inputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        ),*/

        "test_connection" to McpTool(
            name = "test_connection",
            description = "Test a database connection",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID")
                ),
                "required" to listOf("connection_id")
            )
        ),

        "get_database_schema" to McpTool(
            name = "get_database_schema",
            description = "Get complete database schema information including tables, views, and relationships",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("connection_id")
            )
        ),

        "list_tables" to McpTool(
            name = "list_tables",
            description = "List all tables in the database schema",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("connection_id")
            )
        ),

        "describe_table" to McpTool(
            name = "describe_table",
            description = "Get detailed information about a specific table including columns, constraints, and indexes",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("connection_id", "table_name")
            )
        ),

        "execute_select" to McpTool(
            name = "execute_select",
            description = "Execute a SELECT query safely with automatic limit protection",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "query" to mapOf("type" to "string", "description" to "SELECT query to execute"),
                    "limit" to mapOf("type" to "integer", "description" to "Maximum number of rows to return", "default" to 100)
                ),
                "required" to listOf("connection_id", "query")
            )
        ),

        "sample_data" to McpTool(
            name = "sample_data",
            description = "Get a random sample of data from a table to understand its content",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)"),
                    "sample_size" to mapOf("type" to "integer", "description" to "Number of sample rows", "default" to 10)
                ),
                "required" to listOf("connection_id", "table_name")
            )
        ),

        "get_table_stats" to McpTool(
            name = "get_table_stats",
            description = "Get statistical information about a table including row count and column statistics",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("connection_id", "table_name")
            )
        ),

        "explain_query" to McpTool(
            name = "explain_query",
            description = "Get the execution plan for a SELECT query to analyze performance",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "query" to mapOf("type" to "string", "description" to "SELECT query to explain")
                ),
                "required" to listOf("connection_id", "query")
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

        "find_duplicates" to McpTool(
            name = "find_duplicates",
            description = "Find duplicate records in a table based on specified columns",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "columns" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Columns to check for duplicates"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("connection_id", "table_name", "columns")
            )
        ),

        "analyze_data_quality" to McpTool(
            name = "analyze_data_quality",
            description = "Analyze data quality issues in a table such as null values, duplicates, and inconsistencies",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID"),
                    "table_name" to mapOf("type" to "string", "description" to "Table name"),
                    "schema_name" to mapOf("type" to "string", "description" to "Schema name (optional)")
                ),
                "required" to listOf("connection_id", "table_name")
            )
        ),

        "disconnect_database" to McpTool(
            name = "disconnect_database",
            description = "Disconnect a database connection by its ID",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "connection_id" to mapOf("type" to "integer", "description" to "Database connection ID")
                ),
                "required" to listOf("connection_id")
            )
        )
    )

    private val prompts = mapOf(
        "database_analysis" to McpPrompt(
            name = "database_analysis",
            description = "Get a comprehensive prompt for database analysis with schema context",
            arguments = listOf(
                McpPromptArgument("connection_id", "Database connection ID", true)
            )
        ),

        "query_optimization" to McpPrompt(
            name = "query_optimization",
            description = "Get guidance for optimizing database queries",
            arguments = listOf(
                McpPromptArgument("connection_id", "Database connection ID", true),
                McpPromptArgument("query", "SQL query to optimize", false)
            )
        ),

        "data_exploration" to McpPrompt(
            name = "data_exploration",
            description = "Get a prompt for exploring and understanding database data",
            arguments = listOf(
                McpPromptArgument("connection_id", "Database connection ID", true),
                McpPromptArgument("table_name", "Specific table to focus on", false)
            )
        )
    )

    fun handleMcpRequest(request: McpRequest): McpResponse {
        logger.info { "Handling MCP request: ${request.method}" }

        return try {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> listTools(request)
                "tools/call" -> callTool(request)
                "prompts/list" -> listPrompts(request)
                "prompts/get" -> getPrompt(request)
                "resources/list" -> listResources(request)
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
                "logging" to mapOf<String, Any>(),
                "prompts" to mapOf("listChanged" to true),
                "resources" to mapOf<String, Any>(),
                "tools" to mapOf("listChanged" to true)
            ),
            "serverInfo" to mapOf(
                "name" to "PostgreSQL MCP Server",
                "version" to "1.0.0"
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

        logger.info { "Calling tool: $toolName with arguments: $arguments" }

        return try {
            val result = when (toolName) {
                "connect_database" -> connectDatabase(arguments)
                // "list_connections" -> listConnections()
                "test_connection" -> testConnection(arguments)
                "get_database_schema" -> getDatabaseSchema(arguments)
                "list_tables" -> listTables(arguments)
                "describe_table" -> describeTable(arguments)
                "execute_select" -> executeSelectQuery(arguments)
                "sample_data" -> sampleData(arguments)
                "get_table_stats" -> getTableStats(arguments)
                "explain_query" -> explainQuery(arguments)
                "validate_sql" -> validateSql(arguments)
                "find_duplicates" -> findDuplicates(arguments)
                "analyze_data_quality" -> analyzeDataQuality(arguments)
                "disconnect_database" -> disconnectDatabase(arguments)
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
            logger.error(e.message) { "Error executing tool: $toolName" }
            errorResponse(request.id, "Tool execution failed: ${e.message}")
        }
    }

    private fun connectDatabase(arguments: Map<String, Any>): Any {
        val connection = databaseService.createConnection()
        return mapOf(
            "success" to true,
            "connection_id" to connection.id,
            "message" to "Database connection created successfully"
        )
    }

    private fun listConnections(): Any {
        val connections = databaseService.getAllConnections()
        return mapOf(
            "connections" to connections.map { conn ->
                mapOf(
                    "id" to conn.id,
                    "name" to conn.name,
                    "host" to conn.host,
                    "port" to conn.port,
                    "database" to conn.database,
                    "username" to conn.username,
                    "schema" to conn.schema,
                    "isActive" to conn.isActive,
                    "createdAt" to conn.createdAt.toString(),
                    "description" to conn.description
                )
            }
        )
    }

    private fun testConnection(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")

        val isValid = databaseService.testConnection(connectionId)
        return mapOf(
            "success" to isValid,
            "message" to if (isValid) "Connection is valid" else "Connection failed"
        )
    }

    private fun getDatabaseSchema(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.discoverDatabaseSchema(connectionId, schemaName)
    }

    private fun listTables(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.listTables(connectionId, schemaName)
    }

    private fun describeTable(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.describeTable(connectionId, tableName, schemaName)
    }

    private fun executeSelectQuery(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val query = arguments["query"] as? String
            ?: throw McpException("Missing query")
        val limit = (arguments["limit"] as? Number)?.toInt() ?: 100

        return queryExecutionService.executeSelectQuery(connectionId, query, limit)
    }

    private fun sampleData(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String
        val sampleSize = (arguments["sample_size"] as? Number)?.toInt() ?: 10

        return queryExecutionService.sampleTableData(connectionId, tableName, schemaName, sampleSize)
    }

    private fun getTableStats(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return schemaDiscoveryService.getTableStatistics(connectionId, tableName, schemaName)
    }

    private fun explainQuery(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val query = arguments["query"] as? String
            ?: throw McpException("Missing query")

        return queryExecutionService.explainQuery(connectionId, query)
    }

    private fun validateSql(arguments: Map<String, Any>): Any {
        val query = arguments["query"] as? String
            ?: throw McpException("Missing query")

        return queryExecutionService.validateQuery(query)
    }

    private fun findDuplicates(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val columns = arguments["columns"] as? List<String>
            ?: throw McpException("Missing columns")
        val schemaName = arguments["schema_name"] as? String

        return queryExecutionService.findDuplicates(connectionId, tableName, columns, schemaName)
    }

    private fun analyzeDataQuality(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val tableName = arguments["table_name"] as? String
            ?: throw McpException("Missing table_name")
        val schemaName = arguments["schema_name"] as? String

        return queryExecutionService.analyzeDataQuality(connectionId, tableName, schemaName)
    }

    private fun disconnectDatabase(arguments: Map<String, Any>): Any {
        val connectionId = (arguments["connection_id"] as? Number)?.toLong()
            ?: throw McpException("Missing connection_id")
        val success = databaseService.disconnectConnection(connectionId)
        val details = databaseService.getConnectionDetails(connectionId)
        return mapOf(
            "success" to success,
            "connection_id" to connectionId,
            "name" to details?.name,
            "message" to if (success) "Disconnected successfully" else "Disconnection failed"
        )
    }

    private fun listPrompts(request: McpRequest): McpResponse {
        return McpResponse(id = request.id, result = mapOf("prompts" to prompts.values.toList()))
    }

    private fun getPrompt(request: McpRequest): McpResponse {
        val params = request.params ?: return errorResponse(request.id, "Missing parameters")
        val promptName = params["name"] as? String ?: return errorResponse(request.id, "Missing prompt name")
        val arguments = params["arguments"] as? Map<String, Any> ?: emptyMap()

        val promptContent = when (promptName) {
            "database_analysis" -> {
                val connectionId = (arguments["connection_id"] as? Number)?.toLong()
                    ?: throw McpException("Missing connection_id")
                promptService.getDatabaseAnalysisPrompt(connectionId)
            }
            "query_optimization" -> {
                val connectionId = (arguments["connection_id"] as? Number)?.toLong()
                    ?: throw McpException("Missing connection_id")
                val query = arguments["query"] as? String
                promptService.getQueryOptimizationPrompt(connectionId, query)
            }
            "data_exploration" -> {
                val connectionId = (arguments["connection_id"] as? Number)?.toLong()
                    ?: throw McpException("Missing connection_id")
                val tableName = arguments["table_name"] as? String
                promptService.getDataExplorationPrompt(connectionId, tableName)
            }
            else -> throw McpException("Unknown prompt: $promptName")
        }

        return McpResponse(
            id = request.id,
            result = mapOf(
                "description" to prompts[promptName]?.description,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to mapOf(
                            "type" to "text",
                            "text" to promptContent
                        )
                    )
                )
            )
        )
    }

    private fun listResources(request: McpRequest): McpResponse {
        return McpResponse(id = request.id, result = mapOf("resources" to emptyList<McpResource>()))
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
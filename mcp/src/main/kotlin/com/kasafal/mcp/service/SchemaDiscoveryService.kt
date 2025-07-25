package com.kasafal.mcp.service

import com.kasafal.mcp.exception.DatabaseException
import com.kasafal.mcp.exception.InvalidSQlQueryException
import com.kasafal.mcp.model.database.*
import com.kasafal.mcp.model.session.DatabaseOperation
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SchemaDiscoveryService(
    private val databaseService: DatabaseService,
    private val sessionAuthenticationService: SessionAuthenticationService
) {

    
    // ==== Session-based methods ====
    
    /**
     * Discover database schema using session
     */
    fun discoverDatabaseSchemaUsingSession(sessionId: String, schemaName: String? = null): DatabaseSchemaInfo {
        logger.info { "Discovering schema using session, schema: $schemaName" }
        
        val sessionValidationResult = sessionAuthenticationService.validateAndUseSession(sessionId, DatabaseOperation.SCHEMA_DISCOVERY)

        if(!sessionValidationResult.isValid){
            throw InvalidSQlQueryException(sessionValidationResult.errorMessage?:"Session is invalid/expired")
        }
        val connectionInfo = sessionValidationResult.sessionAuth?.connectionInfo
            ?: throw DatabaseException("No connection info found for session")
        val dataSource = databaseService.getDataSourceByInfo(connectionInfo)
        val targetSchema = schemaName ?: connectionInfo.schema
        
        return dataSource.connection.use { connection ->
            val tables = discoverTables(connection, targetSchema)
            val views = discoverViews(connection, targetSchema)
            val functions = discoverFunctions(connection, targetSchema)
            val relationships = discoverRelationships(connection, targetSchema)

            DatabaseSchemaInfo(
                schemaName = targetSchema,
                tables = tables,
                views = views,
                functions = functions,
                relationships = relationships
            )
        }
    }
    
    /**
     * List tables using session
     */
    fun listTablesUsingSession(sessionId: String, schemaName: String? = null): List<TableInfo> {
        val sessionValidationResult = sessionAuthenticationService.validateAndUseSession(sessionId, DatabaseOperation.SCHEMA_DISCOVERY)

        if(!sessionValidationResult.isValid){
            throw InvalidSQlQueryException(sessionValidationResult.errorMessage?:"Session is invalid/expired")
        }
        val connectionInfo = sessionValidationResult.sessionAuth?.connectionInfo
            ?: throw DatabaseException("No connection info found for session")
        val dataSource = databaseService.getDataSourceByInfo(connectionInfo)
        val targetSchema = schemaName ?: connectionInfo.schema
        
        return dataSource.connection.use { connection ->
            val sql = """
                SELECT t.table_name, 
                       t.table_type,
                       obj_description(c.oid) as table_comment,
                       (SELECT COUNT(*) FROM information_schema.columns 
                        WHERE table_schema = t.table_schema AND table_name = t.table_name) as column_count
                FROM information_schema.tables t
                LEFT JOIN pg_class c ON c.relname = t.table_name
                LEFT JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = t.table_schema
                WHERE t.table_schema = ?
                  AND t.table_type = 'BASE TABLE'
                ORDER BY t.table_name
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, targetSchema)
                val resultSet = statement.executeQuery()

                val tables = mutableListOf<TableInfo>()
                while (resultSet.next()) {
                    tables.add(TableInfo(
                        name = resultSet.getString("table_name"),
                        type = resultSet.getString("table_type"),
                        comment = resultSet.getString("table_comment"),
                        columnCount = resultSet.getInt("column_count")
                    ))
                }
                tables
            }
        }
    }
    
    /**
     * Describe table using session
     */
    fun describeTableUsingSession(sessionId: String, tableName: String, schemaName: String? = null): TableSchema {
        val sessionValidationResult = sessionAuthenticationService.validateAndUseSession(sessionId, DatabaseOperation.SCHEMA_DISCOVERY)

        if(!sessionValidationResult.isValid){
            throw InvalidSQlQueryException(sessionValidationResult.errorMessage?:"Session is invalid/expired")
        }
        val connectionInfo = sessionValidationResult.sessionAuth?.connectionInfo
            ?: throw DatabaseException("No connection info found for session")
        val dataSource = databaseService.getDataSourceByInfo(connectionInfo)
        val targetSchema = schemaName ?: connectionInfo.schema
        
        return dataSource.connection.use { connection ->
            val columns = getTableColumns(connection, targetSchema, tableName)
            val primaryKeys = getPrimaryKeys(connection, targetSchema, tableName)
            val foreignKeys = getForeignKeys(connection, targetSchema, tableName)
            val indexes = getTableIndexes(connection, targetSchema, tableName)
            val rowCount = getTableRowCount(connection, targetSchema, tableName)
            val tableComment = getTableComment(connection, targetSchema, tableName)

            TableSchema(
                tableName = tableName,
                schemaName = targetSchema,
                columns = columns,
                primaryKeys = primaryKeys,
                foreignKeys = foreignKeys,
                indexes = indexes,
                rowCount = rowCount,
                tableComment = tableComment
            )
        }
    }
    
    /**
     * Get table statistics using session
     */
    fun getTableStatisticsUsingSession(sessionId: String, tableName: String, schemaName: String? = null): TableStats {
        val sessionValidationResult = sessionAuthenticationService.validateAndUseSession(sessionId, DatabaseOperation.SCHEMA_DISCOVERY)

        if(!sessionValidationResult.isValid){
            throw InvalidSQlQueryException(sessionValidationResult.errorMessage?:"Session is invalid/expired")
        }
        val connectionInfo = sessionValidationResult.sessionAuth?.connectionInfo
            ?: throw DatabaseException("No connection info found for session")
        val dataSource = databaseService.getDataSourceByInfo(connectionInfo)
        val targetSchema = schemaName ?: connectionInfo.schema
        
        return dataSource.connection.use { connection ->
            val rowCount = getTableRowCount(connection, targetSchema, tableName) ?: 0
            val sizeBytes = getTableSize(connection, targetSchema, tableName)
            val columnStats = getColumnStatistics(connection, targetSchema, tableName)

            TableStats(
                tableName = tableName,
                rowCount = rowCount,
                sizeBytes = sizeBytes,
                columnStats = columnStats
            )
        }
    }

    private fun discoverTables(connection: java.sql.Connection, schemaName: String): List<TableSchema> {
        val sql = """
            SELECT table_name 
            FROM information_schema.tables 
            WHERE table_schema = ? AND table_type = 'BASE TABLE'
            ORDER BY table_name
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            val resultSet = statement.executeQuery()

            val tables = mutableListOf<TableSchema>()
            while (resultSet.next()) {
                val tableName = resultSet.getString("table_name")
                try {
                    val tableSchema = buildTableSchema(connection, schemaName, tableName)
                    tables.add(tableSchema)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to discover schema for table: $tableName" }
                }
            }
            tables
        }
    }

    private fun buildTableSchema(connection: java.sql.Connection, schemaName: String, tableName: String): TableSchema {
        return TableSchema(
            tableName = tableName,
            schemaName = schemaName,
            columns = getTableColumns(connection, schemaName, tableName),
            primaryKeys = getPrimaryKeys(connection, schemaName, tableName),
            foreignKeys = getForeignKeys(connection, schemaName, tableName),
            indexes = getTableIndexes(connection, schemaName, tableName),
            rowCount = getTableRowCount(connection, schemaName, tableName),
            tableComment = getTableComment(connection, schemaName, tableName)
        )
    }

    private fun getTableColumns(connection: java.sql.Connection, schemaName: String, tableName: String): List<ColumnInfo> {
        val sql = """
            SELECT c.column_name, c.data_type, c.udt_name, c.is_nullable, c.column_default,
                   c.character_maximum_length, c.numeric_precision, c.numeric_scale,
                   c.ordinal_position, col_description(pgc.oid, c.ordinal_position) as column_comment,
                   CASE WHEN c.column_default LIKE 'nextval%' THEN true ELSE false END as is_auto_increment
            FROM information_schema.columns c
            LEFT JOIN pg_class pgc ON pgc.relname = c.table_name
            LEFT JOIN pg_namespace pgn ON pgn.oid = pgc.relnamespace AND pgn.nspname = c.table_schema
            WHERE c.table_schema = ? AND c.table_name = ?
            ORDER BY c.ordinal_position
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            val resultSet = statement.executeQuery()

            val columns = mutableListOf<ColumnInfo>()
            while (resultSet.next()) {
                columns.add(ColumnInfo(
                    name = resultSet.getString("column_name"),
                    dataType = resultSet.getString("data_type"),
                    postgresType = resultSet.getString("udt_name"),
                    isNullable = resultSet.getString("is_nullable") == "YES",
                    defaultValue = resultSet.getString("column_default"),
                    maxLength = resultSet.getObject("character_maximum_length") as? Int,
                    precision = resultSet.getObject("numeric_precision") as? Int,
                    scale = resultSet.getObject("numeric_scale") as? Int,
                    ordinalPosition = resultSet.getInt("ordinal_position"),
                    comment = resultSet.getString("column_comment"),
                    isAutoIncrement = resultSet.getBoolean("is_auto_increment")
                ))
            }
            columns
        }
    }

    private fun getPrimaryKeys(connection: java.sql.Connection, schemaName: String, tableName: String): List<String> {
        val sql = """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            WHERE tc.constraint_type = 'PRIMARY KEY'
              AND tc.table_schema = ? AND tc.table_name = ?
            ORDER BY kcu.ordinal_position
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            val resultSet = statement.executeQuery()

            val primaryKeys = mutableListOf<String>()
            while (resultSet.next()) {
                primaryKeys.add(resultSet.getString("column_name"))
            }
            primaryKeys
        }
    }

    private fun getForeignKeys(connection: java.sql.Connection, schemaName: String, tableName: String): List<ForeignKeyInfo> {
        val sql = """
            SELECT tc.constraint_name,
                   kcu.column_name as from_column,
                   ccu.table_name as to_table,
                   ccu.column_name as to_column,
                   rc.update_rule,
                   rc.delete_rule
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
                AND ccu.table_schema = tc.table_schema
            JOIN information_schema.referential_constraints rc ON rc.constraint_name = tc.constraint_name
                AND rc.constraint_schema = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema = ? AND tc.table_name = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            val resultSet = statement.executeQuery()

            val foreignKeys = mutableListOf<ForeignKeyInfo>()
            while (resultSet.next()) {
                foreignKeys.add(ForeignKeyInfo(
                    name = resultSet.getString("constraint_name"),
                    fromTable = tableName,
                    fromColumn = resultSet.getString("from_column"),
                    toTable = resultSet.getString("to_table"),
                    toColumn = resultSet.getString("to_column"),
                    updateRule = resultSet.getString("update_rule"),
                    deleteRule = resultSet.getString("delete_rule")
                ))
            }
            foreignKeys
        }
    }

    private fun getTableIndexes(connection: java.sql.Connection, schemaName: String, tableName: String): List<IndexInfo> {
        val sql = """
            SELECT i.indexname as index_name,
                   i.indexdef,
                   ix.indisunique as is_unique,
                   ix.indisprimary as is_primary,
                   am.amname as index_type,
                   string_agg(a.attname, ',' ORDER BY array_position(ix.indkey, a.attnum)) as columns
            FROM pg_indexes i
            JOIN pg_class c ON c.relname = i.tablename
            JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = i.schemaname
            JOIN pg_index ix ON ix.indexrelid = (
                SELECT oid FROM pg_class WHERE relname = i.indexname AND relnamespace = n.oid
            )
            JOIN pg_am am ON am.oid = (
                SELECT relam FROM pg_class WHERE oid = ix.indexrelid
            )
            JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ANY(ix.indkey)
            WHERE i.schemaname = ? AND i.tablename = ?
            GROUP BY i.indexname, i.indexdef, ix.indisunique, ix.indisprimary, am.amname
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            val resultSet = statement.executeQuery()

            val indexes = mutableListOf<IndexInfo>()
            while (resultSet.next()) {
                val columns = resultSet.getString("columns")?.split(",") ?: emptyList()
                indexes.add(IndexInfo(
                    name = resultSet.getString("index_name"),
                    tableName = tableName,
                    columns = columns,
                    isUnique = resultSet.getBoolean("is_unique"),
                    isPrimary = resultSet.getBoolean("is_primary"),
                    indexType = resultSet.getString("index_type")
                ))
            }
            indexes
        }
    }

    private fun getTableRowCount(connection: java.sql.Connection, schemaName: String, tableName: String): Long? {
        return try {
            val sql = """
                SELECT n_tup_ins - n_tup_del as row_count
                FROM pg_stat_user_tables 
                WHERE schemaname = ? AND relname = ?
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, schemaName)
                statement.setString(2, tableName)
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    resultSet.getLong("row_count")
                } else {
                    // Fallback to exact count for small tables
                    getExactRowCount(connection, schemaName, tableName)
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get row count for $schemaName.$tableName" }
            null
        }
    }

    private fun getExactRowCount(connection: java.sql.Connection, schemaName: String, tableName: String): Long? {
        return try {
            val sql = "SELECT COUNT(*) as row_count FROM \"$schemaName\".\"$tableName\""
            connection.prepareStatement(sql).use { statement ->
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    resultSet.getLong("row_count")
                } else null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get exact row count for $schemaName.$tableName" }
            null
        }
    }

    private fun getTableComment(connection: java.sql.Connection, schemaName: String, tableName: String): String? {
        val sql = """
            SELECT obj_description(c.oid) as table_comment
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = ? AND c.relname = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                resultSet.getString("table_comment")
            } else null
        }
    }

    private fun getTableSize(connection: java.sql.Connection, schemaName: String, tableName: String): Long {
        val sql = """
            SELECT pg_total_relation_size(c.oid) as size_bytes
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = ? AND c.relname = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                resultSet.getLong("size_bytes")
            } else 0L
        }
    }

    private fun getColumnStatistics(connection: java.sql.Connection, schemaName: String, tableName: String): Map<String, ColumnStats> {
        val columnStats = mutableMapOf<String, ColumnStats>()

        try {
            val columns = getTableColumns(connection, schemaName, tableName)

            for (column in columns) {
                val stats = getColumnStats(connection, schemaName, tableName, column.name, column.dataType)
                columnStats[column.name] = stats
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get column statistics for $schemaName.$tableName" }
        }

        return columnStats
    }

    private fun getColumnStats(connection: java.sql.Connection, schemaName: String, tableName: String,
                               columnName: String, dataType: String): ColumnStats {

        val tablePath = "\"$schemaName\".\"$tableName\""
        val columnPath = "\"$columnName\""

        return try {
            val sql = when {
                dataType in listOf("integer", "bigint", "decimal", "numeric", "real", "double precision") -> {
                    """
                    SELECT 
                        COUNT(*) as total_count,
                        COUNT($columnPath) as non_null_count,
                        COUNT(DISTINCT $columnPath) as distinct_count,
                        MIN($columnPath) as min_value,
                        MAX($columnPath) as max_value,
                        AVG($columnPath::numeric) as avg_value
                    FROM $tablePath
                    """.trimIndent()
                }
                else -> {
                    """
                    SELECT 
                        COUNT(*) as total_count,
                        COUNT($columnPath) as non_null_count,
                        COUNT(DISTINCT $columnPath) as distinct_count
                    FROM $tablePath
                    """.trimIndent()
                }
            }

            connection.prepareStatement(sql).use { statement ->
                val resultSet = statement.executeQuery()

                if (resultSet.next()) {
                    val totalCount = resultSet.getLong("total_count")
                    val nonNullCount = resultSet.getLong("non_null_count")
                    val distinctCount = resultSet.getLong("distinct_count")

                    ColumnStats(
                        nullCount = totalCount - nonNullCount,
                        distinctCount = distinctCount,
                        minValue = resultSet.getObject("min_value"),
                        maxValue = resultSet.getObject("max_value"),
                        avgValue = resultSet.getObject("avg_value") as? Double,
                        mostCommonValues = getMostCommonValues(connection, schemaName, tableName, columnName)
                    )
                } else {
                    ColumnStats(0, 0, null, null, null, emptyList())
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get statistics for column $columnName" }
            ColumnStats(0, 0, null, null, null, emptyList())
        }
    }

    private fun getMostCommonValues(connection: java.sql.Connection, schemaName: String,
                                    tableName: String, columnName: String): List<Pair<Any?, Long>> {
        return try {
            val sql = """
                SELECT $columnName as value, COUNT(*) as count
                FROM "$schemaName"."$tableName"
                WHERE $columnName IS NOT NULL
                GROUP BY $columnName
                ORDER BY COUNT(*) DESC
                LIMIT 5
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                val resultSet = statement.executeQuery()

                val values = mutableListOf<Pair<Any?, Long>>()
                while (resultSet.next()) {
                    values.add(Pair(resultSet.getObject("value"), resultSet.getLong("count")))
                }
                values
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get most common values for column $columnName" }
            emptyList()
        }
    }

    private fun discoverViews(connection: java.sql.Connection, schemaName: String): List<ViewInfo> {
        val sql = """
            SELECT table_name, view_definition
            FROM information_schema.views
            WHERE table_schema = ?
            ORDER BY table_name
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            val resultSet = statement.executeQuery()

            val views = mutableListOf<ViewInfo>()
            while (resultSet.next()) {
                val viewName = resultSet.getString("table_name")
                val definition = resultSet.getString("view_definition")
                val columns = getTableColumns(connection, schemaName, viewName)

                views.add(ViewInfo(
                    name = viewName,
                    definition = definition,
                    columns = columns
                ))
            }
            views
        }
    }

    private fun discoverFunctions(connection: java.sql.Connection, schemaName: String): List<FunctionInfo> {
        val sql = """
            SELECT routine_name, data_type as return_type, external_language
            FROM information_schema.routines
            WHERE routine_schema = ? AND routine_type = 'FUNCTION'
            ORDER BY routine_name
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            val resultSet = statement.executeQuery()

            val functions = mutableListOf<FunctionInfo>()
            while (resultSet.next()) {
                val functionName = resultSet.getString("routine_name")
                val returnType = resultSet.getString("return_type")
                val language = resultSet.getString("external_language")
                val parameters = getFunctionParameters(connection, schemaName, functionName)

                functions.add(FunctionInfo(
                    name = functionName,
                    returnType = returnType,
                    parameters = parameters,
                    language = language
                ))
            }
            functions
        }
    }

    private fun getFunctionParameters(connection: java.sql.Connection, schemaName: String,
                                      functionName: String): List<ParameterInfo> {
        val sql = """
            SELECT parameter_name, data_type, parameter_mode
            FROM information_schema.parameters
            WHERE specific_schema = ? AND specific_name = ?
            ORDER BY ordinal_position
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, functionName)
            val resultSet = statement.executeQuery()

            val parameters = mutableListOf<ParameterInfo>()
            while (resultSet.next()) {
                parameters.add(ParameterInfo(
                    name = resultSet.getString("parameter_name") ?: "",
                    dataType = resultSet.getString("data_type"),
                    mode = resultSet.getString("parameter_mode") ?: "IN"
                ))
            }
            parameters
        }
    }

    private fun discoverRelationships(connection: java.sql.Connection, schemaName: String): List<Relationship> {
        val sql = """
            SELECT 
                tc.table_name as from_table,
                kcu.column_name as from_column,
                ccu.table_name as to_table,
                ccu.column_name as to_column
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
                AND ccu.table_schema = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            val resultSet = statement.executeQuery()

            val relationships = mutableListOf<Relationship>()
            while (resultSet.next()) {
                relationships.add(Relationship(
                    fromTable = resultSet.getString("from_table"),
                    fromColumn = resultSet.getString("from_column"),
                    toTable = resultSet.getString("to_table"),
                    toColumn = resultSet.getString("to_column"),
                    relationshipType = "many-to-one" // Default, could be enhanced
                ))
            }
            relationships
        }
    }
}

data class TableInfo(
    val name: String,
    val type: String,
    val comment: String?,
    val columnCount: Int
)
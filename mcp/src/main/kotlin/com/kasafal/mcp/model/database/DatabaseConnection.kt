package com.kasafal.mcp.model.database

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDateTime

/**
 * Session-based database connection configuration.
 * This is not persisted but used for temporary session management.
 */

data class DatabaseConnectionDto(
    val name: String,
    val host: String,
    val port: Int = 5432,
    val database: String,
    val username: String,
    val password: String,
    val schema: String = "public",
    val description: String? = null
)

data class TableSchema(
    val tableName: String,
    val schemaName: String,
    val columns: List<ColumnInfo>,
    val primaryKeys: List<String>,
    val foreignKeys: List<ForeignKeyInfo>,
    val indexes: List<IndexInfo>,
    val rowCount: Long? = null,
    val tableComment: String? = null
)

data class ColumnInfo(
    val name: String,
    val dataType: String,
    val postgresType: String,
    val isNullable: Boolean,
    val defaultValue: String?,
    val maxLength: Int?,
    val precision: Int?,
    val scale: Int?,
    val comment: String? = null,
    val isAutoIncrement: Boolean = false,
    val ordinalPosition: Int
)

data class ForeignKeyInfo(
    val name: String,
    val fromTable: String,
    val fromColumn: String,
    val toTable: String,
    val toColumn: String,
    val updateRule: String,
    val deleteRule: String
)

data class IndexInfo(
    val name: String,
    val tableName: String,
    val columns: List<String>,
    val isUnique: Boolean,
    val isPrimary: Boolean,
    val indexType: String
)

data class DatabaseSchemaInfo(
    val schemaName: String,
    val tables: List<TableSchema>,
    val views: List<ViewInfo>,
    val functions: List<FunctionInfo>,
    val relationships: List<Relationship>
)

data class ViewInfo(
    val name: String,
    val definition: String,
    val columns: List<ColumnInfo>
)

data class FunctionInfo(
    val name: String,
    val returnType: String,
    val parameters: List<ParameterInfo>,
    val language: String
)

data class ParameterInfo(
    val name: String,
    val dataType: String,
    val mode: String // IN, OUT, INOUT
)

data class Relationship(
    val fromTable: String,
    val fromColumn: String,
    val toTable: String,
    val toColumn: String,
    val relationshipType: String // "one-to-one", "one-to-many", "many-to-many"
)

data class QueryResult(
    val columns: List<String>,
    val rows: List<Map<String, Any?>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val affectedRows: Int = 0
)

data class TableStats(
    val tableName: String,
    val rowCount: Long,
    val sizeBytes: Long,
    val columnStats: Map<String, ColumnStats>
)

data class ColumnStats(
    val nullCount: Long,
    val distinctCount: Long?,
    val minValue: Any?,
    val maxValue: Any?,
    val avgValue: Double?,
    val mostCommonValues: List<Pair<Any?, Long>>?
)

data class ExplainResult(
    val executionPlan: String,
    val actualRows: Long?,
    val planningTime: Double?,
    val executionTime: Double?,
    val totalCost: Double?
)

data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val suggestions: List<String> = emptyList()
)

data class DataQualityReport(
    val tableName: String,
    val totalRows: Long,
    val issues: List<DataQualityIssue>
)

data class DataQualityIssue(
    val type: String, // "null_values", "duplicates", "invalid_format", etc.
    val column: String,
    val count: Long,
    val percentage: Double,
    val description: String
)
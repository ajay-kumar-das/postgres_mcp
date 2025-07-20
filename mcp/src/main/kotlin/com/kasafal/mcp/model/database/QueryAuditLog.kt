// FIXED DATABASE MODELS - Handling PostgreSQL Reserved Keywords
package com.kasafal.mcp.model.database

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

// ============================================================================
// FIXED MODELS WITH PROPER COLUMN ANNOTATIONS
// ============================================================================

@Entity
@Table(name = "query_audit_log")
data class QueryAuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotNull
    @Column(name = "connection_id")
    val connectionId: Long,

    @NotBlank
    @Column(name = "sql_query", length = 10000)
    val query: String,

    @NotNull
    @Column(name = "executed_at")
    val executedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "execution_time_ms")
    val executionTimeMs: Long,

    @Column(name = "rows_affected")
    val rowsAffected: Int = 0,

    @Column(name = "rows_returned")
    val rowsReturned: Int = 0,

    @Column(name = "is_success")
    val success: Boolean = true,

    @Column(name = "error_message", length = 1000)
    val errorMessage: String? = null,

    @Column(name = "executed_by", length = 100)
    val executedBy: String? = null,

    @Column(name = "query_type", length = 50)
    val queryType: String,

    @Column(name = "query_hash", length = 2000)
    val queryHash: String? = null
)

@Entity
@Table(name = "connection_usage_stats")
data class ConnectionUsageStats(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotNull
    @Column(name = "connection_id")
    val connectionId: Long,

    @NotNull
    @Column(name = "stats_date")
    val date: LocalDateTime = LocalDateTime.now(),

    @Column(name = "total_queries")
    val totalQueries: Long = 0,

    @Column(name = "successful_queries")
    val successfulQueries: Long = 0,

    @Column(name = "failed_queries")
    val failedQueries: Long = 0,

    @Column(name = "avg_execution_time_ms")
    val avgExecutionTimeMs: Double = 0.0,

    @Column(name = "total_rows_returned")
    val totalRowsReturned: Long = 0,

    @Column(name = "unique_tables_accessed")
    val uniqueTablesAccessed: Int = 0,

    @Column(name = "peak_concurrent_connections")
    val peakConcurrentConnections: Int = 0
)

@Entity
@Table(name = "schema_cache")
data class SchemaCache(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotNull
    @Column(name = "connection_id")
    val connectionId: Long,

    @NotBlank
    @Column(name = "schema_name")
    val schemaName: String,

    @NotNull
    @Column(name = "schema_data", length = 50000)
    val schemaData: String,

    @NotNull
    @Column(name = "cached_at")
    val cachedAt: LocalDateTime = LocalDateTime.now(),

    @NotNull
    @Column(name = "expires_at")
    val expiresAt: LocalDateTime,

    @Column(name = "is_valid")
    val isValid: Boolean = true,

    @Column(name = "schema_version", length = 100)
    val schemaVersion: String? = null,

    @Column(name = "table_count")
    val tableCount: Int = 0,

    @Column(name = "last_modified")
    val lastModified: LocalDateTime? = null
)

@Entity
@Table(name = "saved_queries")
data class SavedQuery(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotBlank
    @Column(name = "query_name")
    val name: String,

    @Column(name = "description", length = 500)
    val description: String? = null,

    @NotBlank
    @Column(name = "sql_query", length = 10000)
    val query: String,

    @NotNull
    @Column(name = "connection_id")
    val connectionId: Long,

    @NotBlank
    @Column(name = "category")
    val category: String = "general",

    @Column(name = "is_template")
    val isTemplate: Boolean = false,

    @Column(name = "parameters", length = 2000)
    val parameters: String? = null,

    @Column(name = "is_public")
    val isPublic: Boolean = false,

    @NotNull
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_used")
    val lastUsed: LocalDateTime? = null,

    @Column(name = "use_count")
    val useCount: Long = 0,

    @Column(name = "avg_execution_time_ms")
    val avgExecutionTimeMs: Double? = null
)

@Entity
@Table(name = "table_performance_stats")
data class TablePerformanceStats(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotNull
    @Column(name = "connection_id")
    val connectionId: Long,

    @NotBlank
    @Column(name = "schema_name")
    val schemaName: String,

    @NotBlank
    @Column(name = "table_name")
    val tableName: String,

    @NotNull
    @Column(name = "captured_at")
    val capturedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "seq_scan")
    val seqScan: Long = 0,

    @Column(name = "seq_tup_read")
    val seqTupRead: Long = 0,

    @Column(name = "idx_scan")
    val idxScan: Long = 0,

    @Column(name = "idx_tup_fetch")
    val idxTupFetch: Long = 0,

    @Column(name = "n_tup_ins")
    val nTupIns: Long = 0,

    @Column(name = "n_tup_upd")
    val nTupUpd: Long = 0,

    @Column(name = "n_tup_del")
    val nTupDel: Long = 0,

    @Column(name = "n_tup_hot_upd")
    val nTupHotUpd: Long = 0,

    @Column(name = "n_live_tup")
    val nLiveTup: Long = 0,

    @Column(name = "n_dead_tup")
    val nDeadTup: Long = 0,

    @Column(name = "last_vacuum")
    val lastVacuum: LocalDateTime? = null,

    @Column(name = "last_autovacuum")
    val lastAutovacuum: LocalDateTime? = null,

    @Column(name = "last_analyze")
    val lastAnalyze: LocalDateTime? = null,

    @Column(name = "last_autoanalyze")
    val lastAutoanalyze: LocalDateTime? = null,

    @Column(name = "vacuum_count")
    val vacuumCount: Long = 0,

    @Column(name = "autovacuum_count")
    val autovacuumCount: Long = 0,

    @Column(name = "analyze_count")
    val analyzeCount: Long = 0,

    @Column(name = "autoanalyze_count")
    val autoanalyzeCount: Long = 0
)

// ============================================================================
// ADDITIONAL FIXES FOR RESERVED KEYWORDS
// ============================================================================

// Fix for any other models that might have reserved keyword issues
@Entity
@Table(name = "database_triggers")
data class TriggerInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotBlank
    @Column(name = "trigger_name")
    val name: String,

    @NotBlank
    @Column(name = "table_name")
    val tableName: String,

    @NotBlank
    @Column(name = "trigger_event")
    val event: String,

    @NotBlank
    @Column(name = "trigger_timing")
    val timing: String,

    @NotBlank
    @Column(name = "orientation")
    val orientation: String,

    @Column(name = "trigger_condition")
    val condition: String? = null,

    @Column(name = "trigger_definition", length = 5000)
    val definition: String,

    @Column(name = "is_enabled")
    val isEnabled: Boolean = true,

    @Column(name = "trigger_language")
    val language: String? = null,

    @Column(name = "function_name")
    val functionName: String? = null
)

@Entity
@Table(name = "database_sequences")
data class SequenceInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotBlank
    @Column(name = "sequence_name")
    val name: String,

    @NotBlank
    @Column(name = "data_type")
    val dataType: String,

    @Column(name = "start_value")
    val startValue: Long,

    @Column(name = "min_value")
    val minValue: Long,

    @Column(name = "max_value")
    val maxValue: Long,

    @Column(name = "increment_by")
    val increment: Long,

    @Column(name = "is_cyclic")
    val isCyclic: Boolean,

    @Column(name = "cache_size")
    val cacheSize: Long,

    @Column(name = "last_value")
    val lastValue: Long?,

    @Column(name = "owned_by_table")
    val ownedByTable: String?,

    @Column(name = "owned_by_column")
    val ownedByColumn: String?
)

@Entity
@Table(name = "custom_types")
data class CustomTypeInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotBlank
    @Column(name = "type_name")
    val name: String,

    @NotBlank
    @Column(name = "type_schema")
    val schema: String,

    @NotBlank
    @Column(name = "type_category")
    val type: String,

    @Column(name = "base_type")
    val baseType: String? = null,

    @Column(name = "enum_values", length = 2000)
    val enumValuesJson: String? = null, // Store as JSON string

    @Column(name = "composite_attributes", length = 5000)
    val compositeAttributesJson: String? = null, // Store as JSON string

    @Column(name = "default_value")
    val defaultValue: String? = null,

    @Column(name = "check_constraint")
    val checkConstraint: String? = null,

    @Column(name = "is_not_null")
    val isNotNull: Boolean = false
)

@Entity
@Table(name = "schema_permissions")
data class SchemaPermission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotBlank
    @Column(name = "schema_name")
    val schemaName: String,

    @NotBlank
    @Column(name = "role_name")
    val roleName: String,

    @Column(name = "privileges", length = 500)
    val privilegesJson: String, // Store as JSON string

    @Column(name = "is_grantable")
    val isGrantable: Boolean = false,

    @Column(name = "granted_by")
    val grantedBy: String? = null
)

@Entity
@Table(name = "table_permissions")
data class TablePermission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotBlank
    @Column(name = "table_name")
    val tableName: String,

    @NotBlank
    @Column(name = "role_name")
    val roleName: String,

    @Column(name = "privileges", length = 500)
    val privilegesJson: String, // Store as JSON string

    @Column(name = "is_grantable")
    val isGrantable: Boolean = false,

    @Column(name = "granted_by")
    val grantedBy: String? = null,

    @Column(name = "column_privileges", length = 2000)
    val columnPrivilegesJson: String? = null // Store as JSON string
)

// ============================================================================
// CONSTRAINT INFORMATION
// ============================================================================

@Entity
@Table(name = "table_constraints")
data class ConstraintInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @NotBlank
    @Column(name = "constraint_name")
    val name: String,

    @NotBlank
    @Column(name = "constraint_type")
    val type: String,

    @NotBlank
    @Column(name = "table_name")
    val tableName: String,

    @Column(name = "column_names", length = 1000)
    val columnsJson: String, // Store as JSON array

    @Column(name = "constraint_definition", length = 2000)
    val definition: String? = null,

    @Column(name = "is_deferrable")
    val isDeferrable: Boolean = false,

    @Column(name = "is_deferred")
    val isDeferred: Boolean = false,

    @Column(name = "is_valid")
    val isValid: Boolean = true,

    @Column(name = "referenced_table")
    val referencedTable: String? = null,

    @Column(name = "referenced_columns", length = 1000)
    val referencedColumnsJson: String? = null, // Store as JSON array

    @Column(name = "update_action")
    val updateAction: String? = null,

    @Column(name = "delete_action")
    val deleteAction: String? = null,

    @Column(name = "match_type")
    val matchType: String? = null
)
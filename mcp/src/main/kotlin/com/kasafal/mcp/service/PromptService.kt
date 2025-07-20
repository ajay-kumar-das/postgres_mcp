package com.kasafal.mcp.service

import org.springframework.stereotype.Service

@Service
class PromptService(
    private val schemaDiscoveryService: SchemaDiscoveryService,
    private val databaseService: DatabaseService
) {

    fun getDatabaseAnalysisPrompt(connectionId: Long): String {
        val schema = schemaDiscoveryService.discoverDatabaseSchema(connectionId)
        val connectionInfo = databaseService.getConnectionInfo(connectionId)

        return """
# Database Analysis Context

You are connected to a PostgreSQL database with the following details:

## Connection Information
- **Database**: ${connectionInfo["database"]}
- **Host**: ${connectionInfo["host"]}:${connectionInfo["port"]}
- **Schema**: ${connectionInfo["schema"]}
- **Description**: ${connectionInfo["description"] ?: "No description"}

## Database Schema Overview
The database contains **${schema.tables.size} tables**, **${schema.views.size} views**, and **${schema.functions.size} functions**.

## Tables

${schema.tables.joinToString("\n\n") { table ->
            """
### ${table.tableName}
${if (table.tableComment != null) "**Description**: ${table.tableComment}\n" else ""}
- **Columns**: ${table.columns.size}
- **Primary Keys**: ${table.primaryKeys.joinToString(", ").ifEmpty { "None" }}
- **Foreign Keys**: ${table.foreignKeys.size}
- **Indexes**: ${table.indexes.size}
- **Estimated Rows**: ${table.rowCount?.let { if (it > 1000) "${it / 1000}K" else it.toString() } ?: "Unknown"}

**Column Details**:
${table.columns.take(10).joinToString("\n") { col ->
                "  - **${col.name}** (${col.dataType}${if (col.maxLength != null) "(${col.maxLength})" else ""}) ${if (col.isNullable) "NULL" else "NOT NULL"}${if (col.defaultValue != null) " DEFAULT ${col.defaultValue}" else ""}${if (col.comment != null) " - ${col.comment}" else ""}"
            }}${if (table.columns.size > 10) "\n  ... and ${table.columns.size - 10} more columns" else ""}
    """.trimIndent()
        }}

## Table Relationships

${if (schema.relationships.isNotEmpty()) {
            schema.relationships.joinToString("\n") { rel ->
                "- **${rel.fromTable}.${rel.fromColumn}** → **${rel.toTable}.${rel.toColumn}** (${rel.relationshipType})"
            }
        } else {
            "No foreign key relationships found."
        }}

${if (schema.views.isNotEmpty()) {
            """
## Views

${schema.views.joinToString("\n") { view ->
                "- **${view.name}** (${view.columns.size} columns)"
            }}
    """.trimIndent()
        } else ""}

${if (schema.functions.isNotEmpty()) {
            """
## Functions

${schema.functions.joinToString("\n") { func ->
                "- **${func.name}** → ${func.returnType} (${func.language})"
            }}
    """.trimIndent()
        } else ""}

## Available Tools

You have access to the following database analysis tools:

### Data Exploration
- **`execute_select`**: Run SELECT queries (automatically limited for safety)
- **`sample_data`**: Get sample rows from any table to understand the data
- **`get_table_stats`**: Get comprehensive statistics about table data
- **`describe_table`**: Get detailed table schema including all constraints

### Query Analysis
- **`explain_query`**: Get query execution plan and performance metrics
- **`validate_sql`**: Validate SQL syntax and get optimization suggestions

### Data Quality
- **`find_duplicates`**: Identify duplicate records based on specified columns
- **`analyze_data_quality`**: Comprehensive data quality analysis

## Analysis Guidelines

1. **Start with Understanding**: Use `sample_data` to see actual data in key tables
2. **Check Data Quality**: Use `analyze_data_quality` on important tables
3. **Understand Relationships**: Look at foreign keys to understand data flow
4. **Performance Awareness**: Use `explain_query` for complex queries
5. **Safety First**: All queries are automatically validated and limited

## Query Best Practices

- Always use `execute_select` for data queries
- Start with small limits when exploring large tables
- Use table statistics to understand data distribution
- Consider indexes when writing WHERE clauses
- Be mindful of query performance on large tables

## Common Analysis Patterns

### Data Exploration
```sql
-- Get overview of a table
SELECT COUNT(*) as total_rows FROM schema.table_name;

-- Sample data
SELECT * FROM schema.table_name LIMIT 10;

-- Check for nulls
SELECT column_name, COUNT(*) as null_count 
FROM schema.table_name 
WHERE column_name IS NULL;
```

### Relationship Analysis
```sql
-- Check referential integrity
SELECT COUNT(*) FROM parent_table p
LEFT JOIN child_table c ON p.id = c.parent_id
WHERE c.parent_id IS NULL;
```

You're now ready to analyze this database! Start by exploring the key tables and understanding the data patterns.
        """.trimIndent()
    }

    fun getQueryOptimizationPrompt(connectionId: Long, query: String?): String {
        val connectionInfo = databaseService.getConnectionInfo(connectionId)

        return """
# Query Optimization Guide

You are working with a PostgreSQL database to optimize SQL queries.

## Connection Information
- **Database**: ${connectionInfo["database"]}
- **Host**: ${connectionInfo["host"]}:${connectionInfo["port"]}
- **Schema**: ${connectionInfo["schema"]}

${if (query != null) {
            """
## Query to Optimize
```sql
$query
```

Use the `explain_query` tool to analyze this query's execution plan.
    """.trimIndent()
        } else ""}

## Optimization Tools Available

### Performance Analysis
- **`explain_query`**: Get detailed execution plan with timing and cost information
- **`get_table_stats`**: Understand table size and data distribution
- **`describe_table`**: Check available indexes and constraints

### Query Validation
- **`validate_sql`**: Check for syntax issues and get optimization suggestions

## PostgreSQL Optimization Strategies

### Index Optimization
1. **Check WHERE clause columns**: Ensure frequently filtered columns have indexes
2. **Composite indexes**: Consider multi-column indexes for complex WHERE clauses
3. **Join optimization**: Verify foreign key columns are indexed

### Query Structure
1. **SELECT specificity**: Avoid `SELECT *`, choose only needed columns
2. **WHERE clause order**: Put most selective conditions first
3. **JOIN order**: PostgreSQL optimizer usually handles this, but be aware of join types

### Common Performance Issues
- **Missing indexes** on WHERE/JOIN columns
- **Full table scans** on large tables
- **Unnecessary sorting** without proper indexes
- **Inefficient subqueries** that could be JOINs

## Analysis Process

1. **Run EXPLAIN**: Use `explain_query` to see the execution plan
2. **Check statistics**: Use `get_table_stats` for tables involved
3. **Verify indexes**: Use `describe_table` to see available indexes
4. **Test alternatives**: Try different query structures

## Key EXPLAIN Metrics

- **Cost**: Lower is better (planning cost → execution cost)
- **Rows**: Estimated vs actual row counts
- **Time**: Planning time + execution time
- **Buffers**: Memory usage information

## Example Optimization Patterns

### Before (Slow)
```sql
SELECT * FROM large_table WHERE unindexed_column = 'value';
```

### After (Fast)
```sql
-- Add index first: CREATE INDEX idx_column ON large_table(unindexed_column);
SELECT specific_columns FROM large_table WHERE indexed_column = 'value';
```

Ready to optimize your queries! Start with `explain_query` to understand current performance.
        """.trimIndent()
    }

    fun getDataExplorationPrompt(connectionId: Long, tableName: String?): String {
        val connectionInfo = databaseService.getConnectionInfo(connectionId)
        val schema = schemaDiscoveryService.discoverDatabaseSchema(connectionId)

        val focusTable = if (tableName != null) {
            schema.tables.find { it.tableName.equals(tableName, ignoreCase = true) }
        } else null

        return """
# Data Exploration Guide

You are exploring data in a PostgreSQL database to understand patterns, quality, and insights.

## Connection Information
- **Database**: ${connectionInfo["database"]}
- **Host**: ${connectionInfo["host"]}:${connectionInfo["port"]}
- **Schema**: ${connectionInfo["schema"]}

${if (focusTable != null) {
            """
## Focus Table: ${focusTable.tableName}

${if (focusTable.tableComment != null) "**Purpose**: ${focusTable.tableComment}\n" else ""}
- **Columns**: ${focusTable.columns.size}
- **Estimated Rows**: ${focusTable.rowCount?.let { if (it > 1000) "${it / 1000}K+" else it.toString() } ?: "Unknown"}
- **Primary Key**: ${focusTable.primaryKeys.joinToString(", ").ifEmpty { "None" }}

### Column Overview
${focusTable.columns.joinToString("\n") { col ->
                "- **${col.name}**: ${col.dataType}${if (col.maxLength != null) "(${col.maxLength})" else ""} ${if (col.isNullable) "NULLABLE" else "NOT NULL"}${if (col.comment != null) " - ${col.comment}" else ""}"
            }}

### Related Tables
${schema.relationships.filter { it.fromTable == focusTable.tableName || it.toTable == focusTable.tableName }.let { relations ->
                if (relations.isNotEmpty()) {
                    relations.joinToString("\n") { rel ->
                        if (rel.fromTable == focusTable.tableName) {
                            "- **${rel.fromColumn}** → **${rel.toTable}.${rel.toColumn}** (references)"
                        } else {
                            "- **${rel.fromTable}.${rel.fromColumn}** → **${rel.toColumn}** (referenced by)"
                        }
                    }
                } else {
                    "No direct relationships found."
                }
            }}
    """.trimIndent()
        } else {
            """
## Available Tables

${schema.tables.take(20).joinToString("\n") { table ->
                "- **${table.tableName}** (${table.columns.size} columns, ~${table.rowCount?.let { if (it > 1000) "${it / 1000}K" else it.toString() } ?: "?"} rows)"
            }}${if (schema.tables.size > 20) "\n... and ${schema.tables.size - 20} more tables" else ""}
    """.trimIndent()
        }}

## Data Exploration Tools

### Quick Data Sampling
- **`sample_data`**: Get random sample rows to understand data patterns
- **`get_table_stats`**: Get comprehensive statistics about data distribution
- **`execute_select`**: Run custom queries to explore specific patterns

### Data Quality Analysis
- **`analyze_data_quality`**: Comprehensive analysis of data quality issues
- **`find_duplicates`**: Identify duplicate records
- **`describe_table`**: Understand table structure and constraints

## Exploration Strategy

### 1. Start with Sampling
```sql
-- Get a feel for the data
SELECT * FROM table_name LIMIT 10;

-- Check data types and patterns
SELECT column_name, COUNT(*), COUNT(DISTINCT column_name) 
FROM table_name 
GROUP BY column_name;
```

### 2. Understand Data Distribution
```sql
-- Numeric columns
SELECT MIN(numeric_col), MAX(numeric_col), AVG(numeric_col) 
FROM table_name;

-- Categorical columns
SELECT category_col, COUNT(*) 
FROM table_name 
GROUP BY category_col 
ORDER BY COUNT(*) DESC 
LIMIT 10;
```

### 3. Check Data Quality
```sql
-- Null analysis
SELECT 
  COUNT(*) as total_rows,
  COUNT(column1) as non_null_col1,
  COUNT(column2) as non_null_col2
FROM table_name;

-- Duplicate detection
SELECT column1, column2, COUNT(*) 
FROM table_name 
GROUP BY column1, column2 
HAVING COUNT(*) > 1;
```

### 4. Explore Relationships
```sql
-- Foreign key validation
SELECT COUNT(*) FROM parent_table p
LEFT JOIN child_table c ON p.id = c.parent_id
WHERE c.parent_id IS NULL;

-- Data consistency
SELECT status, COUNT(*) 
FROM table_name 
GROUP BY status;
```

## Common Data Patterns to Look For

### Temporal Patterns
- Date ranges and gaps
- Seasonal trends
- Growth patterns over time

### Categorical Data
- Value distributions
- Unusual or unexpected categories
- Missing standardization

### Numerical Data
- Outliers and anomalies
- Distribution shapes
- Correlation between columns

### Data Quality Issues
- Missing values (NULLs)
- Duplicate records
- Inconsistent formatting
- Invalid values

## Analysis Questions to Answer

1. **What does this data represent?** (Use `sample_data`)
2. **How much data is there?** (Use `get_table_stats`)
3. **What's the data quality like?** (Use `analyze_data_quality`)
4. **Are there any patterns or trends?** (Use custom queries)
5. **How do tables relate to each other?** (Explore foreign keys)

Start your exploration! Use `sample_data` on key tables to get a feel for the data, then dive deeper with targeted queries.
        """.trimIndent()
    }
}
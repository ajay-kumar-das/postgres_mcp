# Legacy Methods Deprecation Summary

## Overview
As part of the migration from connection ID-based operations to secure token-based operations, the following legacy methods have been marked as `@Deprecated` and now throw `UnsupportedOperationException` with migration guidance.

## Deprecated Methods

### QueryExecutionService

1. **`executeQuery(connectionId: Long, query: String): QueryResult`**
   - **Replacement**: `executeQueryUsingToken(tokenId: String, query: String, requiredOperation: DatabaseOperation): QueryResult`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

2. **`explainQuery(connectionId: Long, query: String): ExplainResult`**
   - **Replacement**: `explainQueryUsingToken(tokenId: String, query: String): ExplainResult`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

3. **`sampleTableData(connectionId: Long, tableName: String, schemaName: String?, sampleSize: Int): List<Map<String, Any?>>`**
   - **Replacement**: `sampleTableDataUsingToken(tokenId: String, tableName: String, schemaName: String?, sampleSize: Int): List<Map<String, Any?>>`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

4. **`findDuplicates(connectionId: Long, tableName: String, columns: List<String>, schemaName: String?): List<Map<String, Any?>>`**
   - **Replacement**: `findDuplicatesUsingToken(tokenId: String, tableName: String, columns: List<String>, schemaName: String?): List<Map<String, Any?>>`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

5. **`analyzeDataQuality(connectionId: Long, tableName: String, schemaName: String?): DataQualityReport`**
   - **Replacement**: `analyzeDataQualityUsingToken(tokenId: String, tableName: String, schemaName: String?): DataQualityReport`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

6. **`analyzeColumnQuality(connectionId: Long, schemaName: String, tableName: String, column: ColumnInfo, totalRows: Long): List<DataQualityIssue>`** (private method)
   - **Replacement**: `analyzeColumnQualityUsingToken(...)`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

### SchemaDiscoveryService

1. **`discoverDatabaseSchema(connectionId: Long, schemaName: String?): DatabaseSchemaInfo`**
   - **Replacement**: `discoverDatabaseSchemaUsingToken(tokenId: String, schemaName: String?): DatabaseSchemaInfo`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

2. **`listTables(connectionId: Long, schemaName: String?): List<TableInfo>`**
   - **Replacement**: `listTablesUsingToken(tokenId: String, schemaName: String?): List<TableInfo>`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

3. **`describeTable(connectionId: Long, tableName: String, schemaName: String?): TableSchema`**
   - **Replacement**: `describeTableUsingToken(tokenId: String, tableName: String, schemaName: String?): TableSchema`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

4. **`getTableStatistics(connectionId: Long, tableName: String, schemaName: String?): TableStats`**
   - **Replacement**: `getTableStatisticsUsingToken(tokenId: String, tableName: String, schemaName: String?): TableStats`
   - **Migration Message**: "Legacy connection ID method not supported. Use token-based methods instead."

## Migration Approach

All deprecated methods follow this pattern:

```kotlin
@Deprecated("Use <newMethodName> instead", ReplaceWith("<newMethodName>(tokenId, ...)"))
fun <legacyMethodName>(connectionId: Long, ...): ReturnType {
    throw UnsupportedOperationException("Legacy connection ID method not supported. Use token-based methods instead.")
}
```

## Benefits of Token-Based Methods

1. **Enhanced Security**: Session tokens with configurable expiration and usage limits
2. **Operation-Specific Authorization**: Different permissions for different database operations  
3. **Improved Audit Trail**: Better tracking of database access and operations
4. **Resource Management**: Connection pooling and cleanup based on token lifecycle
5. **Backward Compatibility**: Legacy methods remain available with deprecation warnings

## Next Steps

1. Update client code to use token-based methods
2. Test thoroughly with the new token-based approach
3. Monitor deprecation warnings in logs
4. Plan removal of deprecated methods in a future major version

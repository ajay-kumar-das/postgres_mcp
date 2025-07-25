# Legacy ConnectionId-Based Methods Analysis

## Overview
This document analyzes all methods in `QueryExecutionService` that use the deprecated `connectionId: Long` parameter and identifies the missing `DatabaseService` methods they attempt to call.

## Legacy Methods in QueryExecutionService

### 1. `explainQuery(connectionId: Long, query: String): ExplainResult`
- **Location**: Lines 130-147
- **Status**: Active legacy method (not marked as deprecated)
- **Functionality**: Executes EXPLAIN queries with analysis and buffer information
- **Missing DatabaseService calls**:
  - `databaseService.executeQuery(connectionId, explainQuery)` (line 141)

### 2. `sampleTableData(connectionId: Long, tableName: String, schemaName: String?, sampleSize: Int): List<Map<String, Any?>>`
- **Location**: Lines 153-163
- **Status**: Active legacy method (not marked as deprecated)
- **Functionality**: Retrieves sample data from a specified table
- **Missing DatabaseService calls**:
  - `databaseService.getConnection(connectionId)` (line 155)
  - `databaseService.getDataSource(connectionId)` (line 157)

### 3. `findDuplicates(connectionId: Long, tableName: String, columns: List<String>, schemaName: String?): List<Map<String, Any?>>`
- **Location**: Lines 165-175
- **Status**: Active legacy method (not marked as deprecated)
- **Functionality**: Finds duplicate records based on specified columns
- **Missing DatabaseService calls**:
  - `databaseService.getConnection(connectionId)` (line 167)
  - `databaseService.getDataSource(connectionId)` (line 169)

### 4. `analyzeDataQuality(connectionId: Long, tableName: String, schemaName: String?): DataQualityReport`
- **Location**: Lines 177-201
- **Status**: Active legacy method (not marked as deprecated)
- **Functionality**: Performs comprehensive data quality analysis on a table
- **Missing DatabaseService calls**:
  - `databaseService.getConnection(connectionId)` (line 178)
  - `databaseService.executeQuery(connectionId, totalRowsQuery)` (line 183)

### 5. `analyzeColumnQuality(connectionId: Long, schemaName: String, tableName: String, column: ColumnInfo, totalRows: Long): List<DataQualityIssue>`
- **Location**: Lines 203-282
- **Status**: Private method, active legacy method
- **Functionality**: Analyzes individual column data quality issues
- **Missing DatabaseService calls**:
  - `databaseService.executeQuery(connectionId, nullQuery)` (line 216)
  - `databaseService.executeQuery(connectionId, emptyQuery)` (line 239)
  - `databaseService.executeQuery(connectionId, duplicateQuery)` (line 262)

### 6. `executeQuery(connectionId: Long, query: String): QueryResult` 
- **Location**: Lines 74-76
- **Status**: Deprecated and throws UnsupportedOperationException
- **Functionality**: Legacy query execution method (no longer functional)
- **Note**: This method is properly deprecated and throws an exception

## Missing DatabaseService Methods

Based on the analysis, the following methods are being called on `DatabaseService` but do not exist in the current implementation:

### 1. `getConnection(connectionId: Long)`
- **Called by**: 
  - `sampleTableData()` (line 155)
  - `findDuplicates()` (line 167) 
  - `analyzeDataQuality()` (line 178)
- **Expected return**: Some form of database connection object with schema and queryTimeout properties
- **Current alternative**: `getDataSourceByInfo(connectionInfo: DatabaseConnectionInfo)`

### 2. `getDataSource(connectionId: Long)`
- **Called by**:
  - `sampleTableData()` (line 157)
  - `findDuplicates()` (line 169)
- **Expected return**: DataSource object for the connection
- **Current alternative**: `getDataSourceByInfo(connectionInfo: DatabaseConnectionInfo)`

### 3. `executeQuery(connectionId: Long, query: String)`
- **Called by**:
  - `explainQuery()` (line 141)
  - `analyzeDataQuality()` (line 183)
  - `analyzeColumnQuality()` (multiple calls on lines 216, 239, 262)
- **Expected return**: QueryResult object
- **Current alternative**: `executeQuery(dataSource: DataSource, query: String, timeoutSeconds: Int)`

## Current DatabaseService Methods

The current `DatabaseService` implementation provides:

1. `testConnection(dto: DatabaseConnectionDto): Boolean`
2. `getDataSourceByInfo(connectionInfo: DatabaseConnectionInfo): DataSource`
3. `executeQuery(dataSource: DataSource, query: String, timeoutSeconds: Int): QueryResult`
4. `executeUpdate(dataSource: DataSource, query: String, timeoutSeconds: Int): QueryResult`
5. `cleanupDataSource(connectionInfo: DatabaseConnectionInfo)`
6. `cleanupAllDataSources()`

## Impact Analysis

### Non-functional Methods
The following methods in `QueryExecutionService` are currently **non-functional** due to missing `DatabaseService` methods:

1. ✅ `executeQuery(connectionId: Long, query: String)` - Properly deprecated and throws exception
2. ❌ `explainQuery(connectionId: Long, query: String)` - Will fail at runtime
3. ❌ `sampleTableData(connectionId: Long, ...)` - Will fail at runtime
4. ❌ `findDuplicates(connectionId: Long, ...)` - Will fail at runtime
5. ❌ `analyzeDataQuality(connectionId: Long, ...)` - Will fail at runtime
6. ❌ `analyzeColumnQuality(connectionId: Long, ...)` - Will fail at runtime (private method)

### Token-based Alternatives

The service provides modern token-based alternatives:
- `executeSelectUsingToken(tokenId: String, query: String, limit: Int)`
- `executeQueryUsingToken(tokenId: String, query: String, requiredOperation: DatabaseOperation)`

## Recommendations

1. **Deprecate remaining legacy methods**: Mark methods 2-5 as `@Deprecated` with appropriate replacement suggestions
2. **Implement token-based alternatives**: Create token-based versions of the data analysis methods
3. **Update method signatures**: Migrate from `connectionId: Long` to `tokenId: String` parameters
4. **Remove dead code**: The missing `DatabaseService` methods suggest these legacy methods are no longer intended to work

## Architecture Notes

The current architecture has moved from:
- **Legacy**: Direct connection ID → DatabaseService with connectionId methods
- **Modern**: Session token → DatabaseConnectionInfo → DataSource-based operations

This represents a security and architecture improvement, moving away from long-lived connection IDs to session-based, time-limited tokens with proper credential encryption.

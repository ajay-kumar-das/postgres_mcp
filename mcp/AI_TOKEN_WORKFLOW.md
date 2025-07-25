# AI Token-Based Database Access Workflow

## Overview
This document explains how AI systems interact with the PostgreSQL MCP Server using secure session tokens instead of direct database credentials.

## Security Architecture

### Before (Legacy - INSECURE)
```
AI → Direct DB Credentials → Database Connection → Query Execution
```
**Problems:**
- AI has permanent access to database credentials
- No usage limits or expiration
- Difficult to revoke access
- Security risk if AI is compromised

### After (Token-Based - SECURE)  
```
Admin → Token Creation API → Session Token → AI → MCP Tools → Database
```
**Benefits:**
- AI never sees database credentials
- Tokens have expiration and usage limits
- Fine-grained permission control
- Easy to revoke/invalidate tokens

## Token Generation Workflow

### Step 1: Admin Creates Token
An administrator or authorized system creates a session token via REST API:

```bash
POST /api/session/tokens
Content-Type: application/json

{
  "name": "AI-Analysis-Session",
  "host": "postgres.company.com",
  "port": 5432,
  "database": "analytics",
  "username": "readonly_user",
  "password": "secure_password",
  "schema": "public",
  "expirationMinutes": 120,
  "maxUsages": 50,
  "allowedOperations": [
    "SCHEMA_DISCOVERY",
    "SELECT_QUERIES", 
    "TABLE_SAMPLING"
  ]
}
```

**Response:**
```json
{
  "tokenId": "tok_7f8a9b2c-3d4e-5f6g-7h8i-9j0k1l2m3n4o",
  "expiresAt": "2024-07-25T08:25:00",
  "maxUsages": 50,
  "allowedOperations": ["SCHEMA_DISCOVERY", "SELECT_QUERIES", "TABLE_SAMPLING"],
  "connectionName": "AI-Analysis-Session",
  "message": "Token created successfully. Keep this token secure and use it for database operations."
}
```

### Step 2: Token Distribution to AI
The token is securely provided to the AI system through:

**Option A: Environment Variable (Recommended)**
```bash
export DB_SESSION_TOKEN="tok_7f8a9b2c-3d4e-5f6g-7h8i-9j0k1l2m3n4o"
```

**Option B: Configuration File**
```yaml
database:
  session_token: "tok_7f8a9b2c-3d4e-5f6g-7h8i-9j0k1l2m3n4o"
```

**Option C: Secure API Call**
```bash
# AI makes authenticated request to get token
GET /api/ai/session-token
Authorization: Bearer <AI_API_KEY>
```

### Step 3: AI Uses Token for Database Operations
The AI uses the token with MCP tools:

```json
{
  "method": "tools/call",
  "params": {
    "name": "execute_query",
    "arguments": {
      "token_id": "tok_7f8a9b2c-3d4e-5f6g-7h8i-9j0k1l2m3n4o",
      "query": "SELECT COUNT(*) FROM users WHERE created_date > '2024-01-01'",
      "limit": 100
    }
  }
}
```

## Available MCP Tools with Tokens

### 1. Query Execution
```json
{
  "name": "execute_query",
  "arguments": {
    "token_id": "your-token-here",
    "query": "SELECT * FROM products WHERE price > 100",
    "limit": 50
  }
}
```

### 2. Schema Discovery
```json
{
  "name": "discover_schema", 
  "arguments": {
    "token_id": "your-token-here",
    "schema_name": "public"
  }
}
```

### 3. Table Sampling
```json
{
  "name": "sample_table_data",
  "arguments": {
    "token_id": "your-token-here",
    "table_name": "customers",
    "sample_size": 10
  }
}
```

### 4. Data Quality Analysis
```json
{
  "name": "analyze_data_quality",
  "arguments": {
    "token_id": "your-token-here", 
    "table_name": "orders"
  }
}
```

### 5. Find Duplicates
```json
{
  "name": "find_duplicates",
  "arguments": {
    "token_id": "your-token-here",
    "table_name": "users",
    "columns": ["email", "phone"]
  }
}
```

## Token Management APIs

### Check Token Status
```bash
GET /api/session/tokens/{tokenId}/stats
```

### Invalidate Token
```bash
DELETE /api/session/tokens/{tokenId}
```

### System Health
```bash
GET /api/session/health
```

## Security Best Practices

### 1. Token Lifecycle Management
- **Create tokens with minimal required permissions**
- **Set appropriate expiration times (1-4 hours for AI sessions)**
- **Monitor token usage via stats API**
- **Invalidate tokens immediately after use when possible**

### 2. Permission Scoping
```json
{
  "allowedOperations": [
    "SCHEMA_DISCOVERY",    // Allow schema exploration
    "SELECT_QUERIES",      // Allow SELECT queries only
    "TABLE_SAMPLING"       // Allow data sampling
  ]
  // Exclude: "CONNECTION_TEST", "EXPLAIN_QUERIES", etc.
}
```

### 3. Usage Limits
```json
{
  "expirationMinutes": 60,  // 1 hour maximum
  "maxUsages": 25          // Limited query count
}
```

### 4. Monitoring and Alerting
- Log all token creation and usage
- Alert on suspicious usage patterns
- Track token expiration and renewal

## Integration Examples

### Claude/ChatGPT Integration
```python
import os
import requests

class DatabaseAI:
    def __init__(self):
        self.token = os.environ.get('DB_SESSION_TOKEN')
        self.mcp_url = "http://localhost:8080"
    
    def query_database(self, sql_query):
        payload = {
            "method": "tools/call",
            "params": {
                "name": "execute_query", 
                "arguments": {
                    "token_id": self.token,
                    "query": sql_query,
                    "limit": 100
                }
            }
        }
        response = requests.post(f"{self.mcp_url}/mcp", json=payload)
        return response.json()
```

### Langchain Integration
```python
from langchain.tools import BaseTool

class PostgreSQLTool(BaseTool):
    name = "postgresql_query"
    description = "Execute SQL queries on PostgreSQL database using secure tokens"
    
    def _run(self, query: str) -> str:
        # Use session token instead of direct DB connection
        return self.execute_with_token(query)
```

## Deployment Scenarios

### Development Environment
```bash
# Developer creates short-lived token for testing
curl -X POST http://localhost:8080/api/session/tokens \
  -H "Content-Type: application/json" \
  -d '{
    "name": "dev-test",
    "host": "localhost", 
    "port": 5432,
    "database": "testdb",
    "username": "dev_user",
    "password": "dev_pass",
    "expirationMinutes": 30,
    "maxUsages": 10
  }'
```

### Production Environment
```bash
# Admin creates tokens with strict limits
curl -X POST https://mcp.company.com/api/session/tokens \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ai-prod-readonly",
    "host": "prod-db.internal",
    "port": 5432,
    "database": "analytics",
    "username": "readonly_ai", 
    "password": "$ENCRYPTED_PASSWORD",
    "expirationMinutes": 240,
    "maxUsages": 100,
    "allowedOperations": ["SCHEMA_DISCOVERY", "SELECT_QUERIES"]
  }'
```

## Error Handling

### Token Validation Errors
```json
{
  "error": {
    "message": "Token validation failed: Token has expired",
    "code": -32000
  }
}
```

### Permission Errors  
```json
{
  "error": {
    "message": "Operation SELECT_QUERIES is not allowed for this token",
    "code": -32000
  }
}
```

### Usage Limit Errors
```json
{
  "error": {
    "message": "Token usage limit exceeded",
    "code": -32000
  }
}
```

## Migration from Legacy System

### Phase 1: Dual Mode
- Keep legacy connection-based system running
- Add token-based system alongside
- Gradually migrate AI clients to tokens

### Phase 2: Token-Only
- Deprecate legacy connection endpoints  
- All AI access must use tokens
- Enhanced monitoring and security

### Phase 3: Advanced Features
- Automatic token rotation
- Role-based token creation
- Integration with identity providers
- Advanced usage analytics

## Conclusion

The token-based approach provides:
- ✅ **Security**: No direct credential exposure to AI
- ✅ **Control**: Fine-grained permissions and limits
- ✅ **Auditability**: Complete usage tracking
- ✅ **Flexibility**: Easy to revoke and manage access
- ✅ **Scalability**: Support for multiple AI clients with different permissions

This architecture ensures that AI systems can safely interact with databases while maintaining strict security boundaries and administrative control.

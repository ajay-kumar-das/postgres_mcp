# PostgreSQL MCP Server

A comprehensive Model Context Protocol (MCP) server that enables AI assistants like Claude to automatically discover, understand, and interact with PostgreSQL databases.

## Features

🔧 **Complete MCP Protocol Support**
- Full implementation of MCP 2024-11-05 specification
- 15+ specialized database tools for comprehensive database operations
- Rich prompts for AI context and guidance

🛡️ **Security First**
- SQL injection protection with comprehensive validation
- Query sanitization and safe execution
- Credential encryption and secure storage
- Read-only operations by default with configurable permissions

🔍 **Intelligent Discovery**
- Automatic schema discovery and mapping
- Relationship analysis and foreign key detection
- Data profiling and statistical analysis
- Table and column metadata extraction

📊 **Rich Toolset**
- Database connection management
- Schema exploration and documentation
- Query execution with safety limits
- Data sampling and quality analysis
- Query optimization and performance analysis

🚀 **Production Ready**
- Docker containerization with health checks
- Comprehensive monitoring and metrics
- Structured logging and error handling
- Configurable security and performance settings

## Quick Start

### Prerequisites
- Java 17+
- Docker and Docker Compose
- PostgreSQL (or use the included Docker setup)

### 1. Clone and Build
```bash
git clone <repository-url>
cd postgres-mcp-server
chmod +x build.sh run.sh stop.sh
./build.sh
```

### 2. Start Development Environment
```bash
./run.sh development
```

This starts:
- PostgreSQL MCP Server on `http://localhost:8080`
- PostgreSQL database on `localhost:5433`
- Sample data for testing

### 3. Test the Installation
```bash
# Health check
curl http://localhost:8080/mcp/health

# List available tools
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list"
  }'
```

## MCP Tools Available (Token-Based)

### Session Token Management
- **Session Tokens**: All operations require secure session tokens instead of connection IDs
- **Permissions**: Fine-grained control over allowed operations per token
- **Expiration**: Automatic token expiration with configurable timeouts
- **Audit Trail**: Complete logging of token usage and operations

### Core Database Tools
- **`execute_query`** - Execute SELECT queries safely with token authentication
- **`discover_schema`** - Get complete database schema information
- **`list_tables`** - List all tables in a specific schema
- **`describe_table`** - Get detailed table structure and metadata
- **`sample_table_data`** - Get random sample data from tables

### Data Analysis Tools
- **`analyze_data_quality`** - Comprehensive data quality analysis
- **`find_duplicates`** - Identify duplicate records in tables
- **`validate_sql`** - Validate SQL syntax before execution
- **`explain_query`** - Get query execution plans and performance insights

## Token-Based Usage Examples

**Important**: All database operations now require session tokens for enhanced security. The AI never has direct access to database credentials.

### New Security Features
- ✅ **Token-Free Initialization**: Claude can initialize and list tools without any credentials
- ✅ **Smart Token Redirect**: When database access is needed, Claude automatically redirects users to a secure token generation UI
- ✅ **No Config Credentials**: No database credentials are stored in Claude's configuration
- ✅ **Seamless UX**: Users generate tokens via a user-friendly web interface when needed

### Create Session Token First
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "POST",
  "url": "/api/session/tokens",
  "body": {
    "name": "Analysis Session",
    "host": "localhost",
    "port": 5432,
    "database": "mydb",
    "username": "user",
    "password": "password",
    "expirationMinutes": 60,
    "allowedOperations": ["SCHEMA_DISCOVERY", "SELECT_QUERIES"]
  }
}
```

### Explore Database Schema
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "discover_schema",
    "arguments": {
      "token_id": "tok_a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    }
  }
}
```

### Execute Safe Query
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "execute_query",
    "arguments": {
      "token_id": "tok_a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "query": "SELECT * FROM users WHERE active = true",
      "limit": 10
    }
  }
}
```

## Claude Integration

The server provides rich contextual prompts that give Claude complete understanding of your database:

### Database Analysis with Token
```json
{
  "jsonrpc": "2.0",
  "id": "4",
  "method": "tools/call",
  "params": {
    "name": "analyze_data_quality",
    "arguments": {
      "token_id": "tok_a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "table_name": "users"
    }
  }
}
```

This provides Claude with:
- Complete schema overview with tables, columns, and relationships
- Data statistics and patterns
- Available tools and their usage
- Best practices and guidelines
- Sample queries and analysis patterns

## Configuration

### Environment Variables
```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=mcp_server
DB_USERNAME=mcp_user
DB_PASSWORD=your_password

# Security
ENCRYPTION_PASSWORD=your_encryption_key
ENCRYPTION_SALT=your_salt

# Application
SPRING_PROFILES_ACTIVE=production
```

### Application Settings
See `application.yml` for detailed configuration options:
- Database connection pools
- Query limits and timeouts
- Security settings
- Logging configuration

## Development

### Running Tests
```bash
./gradlew test
```

### Development Mode
```bash
./run.sh development
```

Includes:
- Debug port (5005) for remote debugging
- Detailed logging
- Hot reload capabilities
- Development database

### Adding New Tools
1. Define tool schema in `McpServerService`
2. Implement tool logic
3. Add validation and error handling
4. Update documentation

## Production Deployment

### Docker Compose
```bash
./run.sh production
```

Includes:
- PostgreSQL MCP Server
- PostgreSQL database with persistence
- PgAdmin for database management
- Prometheus for monitoring
- Health checks and auto-restart

### Kubernetes
Example Kubernetes manifests available in `k8s/` directory:
- Deployment with health checks
- Service and ingress configuration
- ConfigMap and Secret management
- Persistent volume claims

### Monitoring
- Health endpoints: `/mcp/health`
- Metrics: `/actuator/prometheus`
- Logs: Structured JSON logging
- Database metrics via PostgreSQL exporter

## Security Considerations

### SQL Injection Protection
- Comprehensive query validation
- Parameterized queries only
- Keyword blacklisting
- Pattern-based injection detection

### Access Control
- Encrypted credential storage
- Connection-based isolation
- Query timeouts and limits
- Audit logging

### Network Security
- HTTPS/TLS support
- CORS configuration
- Rate limiting
- IP allowlisting

## API Reference

### MCP Protocol Endpoints
- `POST /mcp` - Main MCP protocol endpoint
- `GET /mcp/health` - Health check

### Session Token API Endpoints
- `POST /api/session/tokens` - Create new session token
- `GET /api/session/tokens/{tokenId}/stats` - Get token usage statistics
- `DELETE /api/session/tokens/{tokenId}` - Invalidate specific token
- `DELETE /api/session/tokens` - Emergency: Invalidate all tokens
- `GET /api/session/stats` - Get system token statistics
- `GET /api/session/health` - Session token system health check

### Monitoring Endpoints
- `GET /actuator/health` - Application health
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Troubleshooting

### Common Issues

**Connection Timeouts**
- Check database connectivity
- Verify firewall settings
- Increase connection timeout values

**Query Validation Errors**
- Review SQL syntax
- Check for prohibited keywords
- Ensure proper parameterization

**Memory Issues**
- Reduce query result limits
- Increase JVM heap size
- Check for connection leaks

### Logs
```bash
# Application logs
docker-compose logs postgres-mcp

# Database logs
docker-compose logs postgres

# Follow logs
docker-compose logs -f postgres-mcp
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

### Code Style
- Kotlin coding conventions
- Comprehensive unit tests
- Clear documentation
- Error handling and logging

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- GitHub Issues: [Repository Issues](https://github.com/yourorg/postgres-mcp-server/issues)
- Documentation: [Wiki](https://github.com/yourorg/postgres-mcp-server/wiki)
- Email: support@yourorg.com

---

Built with ❤️ for the AI and database communities.
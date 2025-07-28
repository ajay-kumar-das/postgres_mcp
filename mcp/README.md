# PostgreSQL MCP Server

A comprehensive Model Context Protocol (MCP) server that enables AI assistants like Claude to securely connect to and interact with PostgreSQL databases through an OAuth-style session-based authentication system.

## Features

🔧 **Complete MCP Protocol Support**
- Full implementation of MCP 2024-11-05 specification
- 10 specialized database tools for comprehensive database operations
- Rich tool descriptions and contextual guidance for AI assistants

🛡️ **Security First**
- OAuth-style session-based authentication with web UI
- SQL injection protection with comprehensive validation
- Query sanitization and safe execution patterns
- Credential encryption and secure in-memory storage
- Granular permission system with 18 database operations across 5 categories

🔍 **Intelligent Discovery**
- Automatic schema discovery and relationship mapping
- Foreign key detection and constraint analysis
- Data profiling and statistical analysis
- Table and column metadata extraction with type information

📊 **Rich Database Toolset**
- Session-based connection management with cleanup
- Interactive schema exploration and documentation
- Safe query execution with configurable limits
- Data sampling and quality analysis tools
- SQL validation and security checking

🌐 **Modern Web Interface**
- Responsive authentication UI with Bootstrap design
- Real-time connection testing and validation
- Granular permission selection by category
- Mobile-friendly responsive design

🚀 **Production Ready**
- Docker containerization with health checks
- Prometheus monitoring and metrics collection
- Comprehensive structured logging
- Thread-safe operations with atomic session management
- HikariCP connection pooling for performance

## Quick Start

### Prerequisites
- Java 17+
- Docker and Docker Compose
- PostgreSQL database (or use the included Docker setup)

### 1. Clone and Build
```bash
git clone <repository-url>
cd postgres-mcp
chmod +x build.sh run.sh stop.sh
./build.sh
```

### 2. Start Development Environment
```bash
./run.sh development
```

This starts:
- PostgreSQL MCP Server on `http://localhost:8081`
- PostgreSQL database on `localhost:5433`
- PgAdmin on `http://localhost:5050`
- Prometheus monitoring on `http://localhost:9090`

### 3. Test the Installation
```bash
# Health check
curl http://localhost:8081/mcp/health

# List available tools
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list"
  }'
```

## MCP Tools Available

### Authentication & Session Management
- **`start_oauth_flow`** - Initiate OAuth-style authentication flow with source tracking
- **`disconnect_session`** - Invalidate session and clean up resources

### Schema Discovery Tools
- **`discover_schema`** - Get complete database schema with relationships
- **`list_tables`** - List all tables in the specified schema
- **`describe_table`** - Get detailed table structure, columns, and constraints

### Data Access Tools
- **`execute_query`** - Execute SELECT queries safely with session authentication
- **`sample_table_data`** - Get random sample data from specified tables

### Analysis & Quality Tools
- **`analyze_data_quality`** - Comprehensive data quality analysis with statistics
- **`find_duplicates`** - Identify duplicate records based on specified columns
- **`validate_sql`** - Validate SQL syntax and check for security issues (no session required)

## Session-Based Authentication Flow

**Important**: This server uses OAuth-style session-based authentication. AI assistants never need database credentials directly.

### 1. Start Authentication Flow
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "start_oauth_flow",
    "arguments": {
      "purpose": "Database analysis for Claude",
      "source": "claude-desktop"
    }
  }
}
```

**Response includes:**
- Session ID for tracking
- Login URL for web authentication
- Instructions for the user
- Example usage patterns

### 2. User Authentication
Users visit the provided login URL and authenticate via a modern web interface:
- Enter database connection details
- Select granular permissions by category
- Test connection in real-time
- Receive session confirmation

### 3. Use Session for Database Operations
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "discover_schema",
    "arguments": {
      "session_id": "abc123-def456-ghi789"
    }
  }
}
```

## Permission System

### Granular Database Operations (18 Permissions)

**Schema & Structure**
- Schema Discovery - View database schemas and structure
- Table Listing - List tables and views
- Table Description - Get detailed column information

**Data Access**
- Execute Queries - Run SELECT statements
- Table Sampling - Get sample data from tables
- View Data - Browse table contents with pagination

**Analysis & Quality**
- Data Quality Analysis - Analyze data quality issues
- Duplicate Detection - Find duplicate records
- Data Profiling - Generate statistical profiles
- Column Analysis - Analyze distributions and patterns

**Performance & Diagnostics**
- Query Explanation - Analyze execution plans
- Performance Monitoring - Monitor query metrics
- Index Analysis - Analyze indexes and recommendations

**Security & Validation**
- SQL Validation - Validate syntax and security
- Connection Test - Test database connectivity
- Query Auditing - Log and audit operations

**Advanced Features**
- Custom Functions - Execute database functions
- Metadata Access - Access system metadata

## Usage Examples

### Complete Workflow Example
```json
// 1. Start authentication
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "start_oauth_flow",
    "arguments": {
      "purpose": "Data analysis and optimization",
      "source": "claude-desktop"
    }
  }
}

// 2. After user authenticates via web UI, explore schema
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "discover_schema",
    "arguments": {
      "session_id": "your-session-id"
    }
  }
}

// 3. Analyze specific table
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "analyze_data_quality",
    "arguments": {
      "session_id": "your-session-id",
      "table_name": "users"
    }
  }
}

// 4. Execute safe queries
{
  "jsonrpc": "2.0",
  "id": "4",
  "method": "tools/call",
  "params": {
    "name": "execute_query",
    "arguments": {
      "session_id": "your-session-id",
      "query": "SELECT COUNT(*) as total_users, AVG(age) as avg_age FROM users WHERE active = true",
      "limit": 100
    }
  }
}
```

## Configuration

### Environment Variables
```bash
# Database (for Docker setup)
POSTGRES_DB=mcp_demo
POSTGRES_USER=mcp_user
POSTGRES_PASSWORD=mcp_password

# Security
ENCRYPTION_PASSWORD=your_encryption_key_change_in_production
ENCRYPTION_SALT=your_salt_change_in_production

# Application
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8081
```

### Application Settings (application.yml)
```yaml
server:
  port: 8081
  tomcat:
    threads:
      max: 100          # Support 100 concurrent AI requests
      min-spare: 10     # Always keep 10 threads ready
      accept-count: 200 # Queue requests when busy
      max-connections: 8192

spring:
  application:
    name: postgres-mcp-server

# Security settings
security:
  encryption:
    password: ${ENCRYPTION_PASSWORD:default_key_change_in_production}
    salt: ${ENCRYPTION_SALT:default_salt_change_in_production}

# Database connection settings
database:
  query:
    timeout-seconds: 30
    max-rows: 1000
  connection:
    max-pool-size: 10
    min-idle: 2
    connection-timeout: 30000
```

## Development

### Running Tests
```bash
./gradlew test
```

### Development Mode
```bash
./run.sh development
```

Features:
- Debug port (5005) for remote debugging
- Detailed logging output
- Hot reload capabilities
- Development database with sample data

### Project Structure
```
src/main/kotlin/com/kasafal/mcp/
├── config/           # Security and application configuration
├── controller/       # REST controllers for authentication
├── exception/        # Custom exception handling
├── model/           # Data models and DTOs
│   ├── mcp/         # MCP protocol models
│   └── session/     # Session and authentication models
├── service/         # Business logic services
├── util/            # Utility classes (SQL validation, etc.)
└── McpApplication.kt # Main application class
```

## Production Deployment

### Docker Compose
```bash
./run.sh production
```

Includes:
- PostgreSQL MCP Server (8081)
- PostgreSQL database with persistence
- PgAdmin for database management (5050)
- Prometheus for monitoring (9090)
- Health checks and auto-restart policies

### Manual Deployment
```bash
# Build JAR
./gradlew bootJar

# Run with production profile
java -jar build/libs/mcp-*.jar --spring.profiles.active=production
```

### Monitoring Endpoints
- Health check: `GET /mcp/health`
- Application health: `GET /actuator/health`
- Prometheus metrics: `GET /actuator/prometheus`
- Session statistics: `GET /api/auth/stats`

## Security Considerations

### Session Management
- Sessions stored in thread-safe in-memory storage
- Automatic expiration after 2 hours or 100 operations
- Atomic operations prevent race conditions
- Session cleanup on expiration or invalidation

### SQL Security
- Comprehensive SQL injection protection
- Query pattern analysis and validation
- Parameterized queries enforcement
- Suspicious query detection and logging

### Network Security
- CORS configuration for allowed origins
- CSRF protection with token repository
- BCrypt password encoding for credentials
- Encrypted credential storage in memory

### Access Control
- Granular permission system per session
- Operation-level access control
- Query timeouts and result limits
- Complete audit logging

## API Reference

### MCP Protocol Endpoints
- `POST /mcp` - Main MCP protocol endpoint (JSON-RPC 2.0)
- `GET /mcp/health` - Health check with status

### Session Authentication UI
- `GET /api/auth/login?session_id={id}&source={source}` - Authentication page
- `POST /api/auth/authenticate` - Process authentication form
- `GET /api/auth/permissions` - Get available permissions

### Session Management API
- `GET /api/auth/sessions/{sessionId}/status` - Session status
- `GET /api/auth/sessions/{sessionId}/stats` - Session statistics
- `DELETE /api/auth/sessions/{sessionId}` - Invalidate session
- `DELETE /api/auth/sessions` - Emergency: invalidate all sessions

### System Monitoring
- `GET /api/auth/stats` - System session statistics
- `GET /api/auth/health` - Session system health
- `GET /actuator/health` - Spring Boot health
- `GET /actuator/prometheus` - Prometheus metrics

## Troubleshooting

### Common Issues

**Connection Timeouts**
- Verify PostgreSQL is running and accessible
- Check firewall settings and port availability
- Increase connection timeout in application.yml

**Session Authentication Failures**
- Ensure database credentials are correct
- Check that the database allows connections from the server
- Verify encryption settings are consistent

**Memory Issues**
- Reduce max-rows limit in configuration
- Increase JVM heap size: `-Xmx2g`
- Monitor session cleanup and connection pooling

### Debug Logging
```bash
# View application logs
docker-compose logs postgres-mcp

# Follow real-time logs
docker-compose logs -f postgres-mcp

# View specific service logs
docker-compose logs postgres
```

### Session Debugging
Add `?debug=true` to login URL for enhanced debugging:
```
http://localhost:8081/api/auth/login?session_id=test&source=debug&debug=true
```

## Claude Integration Guide

### Recommended Claude Configuration
```json
{
  "mcpServers": {
    "postgres": {
      "command": "curl",
      "args": [
        "-X", "POST",
        "http://localhost:8081/mcp",
        "-H", "Content-Type: application/json",
        "-d", "@-"
      ]
    }
  }
}
```

### Best Practices for AI Assistants
1. **Always start with `start_oauth_flow`** when database access is needed
2. **Use `discover_schema`** to understand database structure first
3. **Validate SQL** before execution using `validate_sql`
4. **Respect session limits** - sessions expire after 100 operations
5. **Clean up sessions** using `disconnect_session` when done

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Add tests for new functionality
4. Ensure all tests pass (`./gradlew test`)
5. Commit changes (`git commit -m 'Add amazing feature'`)
6. Push to branch (`git push origin feature/amazing-feature`)
7. Submit a pull request

### Code Standards
- Kotlin coding conventions
- Comprehensive unit and integration tests
- Clear documentation and comments
- Proper error handling and logging
- Thread-safe operations where applicable

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- GitHub Issues: Create an issue for bugs or feature requests
- Documentation: Check the code comments and examples
- Security Issues: Report privately via email

---

**Built for AI-powered database interactions** 🤖 💾

*Enabling secure, intelligent database access for the next generation of AI assistants.*
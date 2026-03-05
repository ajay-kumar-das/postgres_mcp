# PostgreSQL MCP Server

A powerful, zero-configuration Model Context Protocol (MCP) server that instantly transforms any PostgreSQL database into an AI-accessible resource. Simply provide database credentials, and AI assistants like Claude can automatically discover, understand, and intelligently interact with your data without requiring any database-specific code or schemas.

## Key Advantages & Benefits

🎯 **Zero Configuration Required**
- **No Code Needed**: Works with any existing PostgreSQL database instantly
- **Universal Compatibility**: No database-specific configurations or schemas required
- **Plug & Play**: Just provide connection details - AI does the rest
- **Legacy Support**: Works with databases from any era or version
- **Multi-Database**: Connect to multiple databases with different schemas seamlessly

🧠 **AI-Native Database Intelligence**
- **Autonomous Discovery**: AI automatically maps your entire database structure
- **Context-Aware Queries**: AI understands relationships, constraints, and data patterns
- **Intelligent Recommendations**: Suggests optimal queries and identifies data quality issues
- **Natural Language Interface**: Ask questions in plain English, get precise SQL results
- **Domain Understanding**: AI learns your business logic from schema and data patterns

🔧 **Complete MCP Protocol Support**
- Full implementation of MCP 2024-11-05 specification
- 10 specialized database tools for comprehensive database operations
- Rich contextual prompts that give AI complete database understanding
- Standardized interface for any MCP-compatible AI assistant

🛡️ **Enterprise-Grade Security**
- **OAuth-Style Authentication**: Secure web-based credential flow
- **Zero-Trust Architecture**: AI never stores or sees database credentials
- **SQL Injection Prevention**: Multi-layer protection with comprehensive validation
- **Granular Permissions**: 18 fine-grained database operations across 5 security categories
- **Session Isolation**: Each AI interaction runs in its own secure session
- **Audit Trail**: Complete logging of all AI database interactions

🔍 **Intelligent Database Discovery**
- **Automatic Schema Mapping**: Discovers tables, columns, relationships, and constraints
- **Relationship Intelligence**: Identifies foreign keys, indexes, and data dependencies
- **Data Pattern Analysis**: Understands data types, distributions, and quality metrics
- **Business Logic Inference**: Learns naming conventions and domain patterns
- **Real-Time Analysis**: Live data profiling and statistical insights

📊 **Advanced AI Database Capabilities**
- **Smart Query Generation**: AI writes optimized queries based on natural language requests
- **Data Quality Assessment**: Automated detection of inconsistencies, duplicates, and anomalies
- **Performance Analysis**: Query optimization suggestions and execution plan analysis
- **Predictive Insights**: AI identifies trends and patterns in your data
- **Cross-Table Intelligence**: Understands complex multi-table relationships automatically

🌐 **Modern User Experience**
- **Responsive Web Interface**: Beautiful, mobile-friendly authentication UI
- **Real-Time Validation**: Instant connection testing and credential verification
- **Visual Permission Control**: Intuitive category-based access management
- **Progress Tracking**: Live feedback during database discovery and analysis
- **Developer-Friendly**: Clean APIs with comprehensive error handling

🚀 **Production-Ready Architecture**
- **Horizontal Scalability**: Handles multiple concurrent AI sessions efficiently
- **Docker Native**: Complete containerization with health checks and monitoring
- **Enterprise Monitoring**: Prometheus metrics, structured logging, and alerting
- **Connection Pooling**: Optimized database connections with automatic cleanup
- **Memory Efficient**: Smart session management with automatic resource cleanup
- **High Availability**: Graceful degradation and automatic recovery

🔄 **AI Workflow Integration**
- **Seamless Claude Integration**: Purpose-built for Claude Desktop and API
- **Multi-Assistant Support**: Works with any MCP-compatible AI tool
- **Workflow Automation**: AI can perform complex multi-step database tasks
- **Data Pipeline Ready**: Integrates with AI-driven ETL and analysis workflows
- **Knowledge Building**: AI builds cumulative understanding of your data over time

💡 **Business Value Proposition**
- **Instant Database Accessibility**: Transform any database into an AI-queryable resource in minutes
- **No Development Overhead**: Zero custom code or schema modifications required
- **Universal Data Access**: One tool works across all your PostgreSQL databases
- **AI-Powered Insights**: Unlock hidden patterns and relationships in your data
- **Democratized Analytics**: Enable non-technical users to query databases through AI
- **Rapid Prototyping**: Instantly explore new databases and datasets with AI assistance
- **Legacy Modernization**: Bring old databases into the AI era without migration

## Why This Approach Is Revolutionary

### Traditional Database AI Integration vs. PostgreSQL MCP Server

**❌ Traditional Approach:**
- Requires custom code for each database schema
- Manual API development for data access
- Hard-coded SQL queries and business logic
- Database-specific integration points
- Weeks/months of development time
- Ongoing maintenance for schema changes

**✅ PostgreSQL MCP Server Approach:**
- **Zero Code Required**: Works with any PostgreSQL database immediately
- **AI Self-Discovery**: Claude automatically learns your database structure
- **Universal Interface**: One installation works across all your databases
- **Instant Results**: From connection to AI queries in under 5 minutes
- **Future-Proof**: Automatically adapts to schema changes

### Real-World Use Cases

**🏢 Enterprise Data Analysis**
```
Scenario: "I need to analyze our customer churn patterns"
Traditional: Weeks of development, custom dashboards, rigid reports
MCP Server: Connect Claude to your CRM database, ask in natural language
Result: Instant insights with interactive follow-up questions
```

**📊 Business Intelligence**
```
Scenario: "What are our top-performing products this quarter?"
Traditional: Pre-built BI tools with limited flexibility
MCP Server: Claude discovers product tables, sales data, and relationships
Result: Dynamic analysis with contextual insights and recommendations
```

**🔍 Database Exploration**
```
Scenario: "I inherited a legacy database - what data do we have?"
Traditional: Manual documentation, schema browsing, guesswork
MCP Server: Claude maps entire database, explains relationships
Result: Complete understanding in minutes, not hours
```

**🚀 Rapid Prototyping**
```
Scenario: "Can we quickly test a hypothesis with our user data?"
Traditional: Write scripts, ETL processes, custom queries
MCP Server: Ask Claude to analyze patterns directly
Result: Immediate insights without infrastructure setup
```

### The Power of AI-Native Database Access

Instead of building rigid integrations, PostgreSQL MCP Server enables **conversational database interaction**:

- **"Show me all tables related to user authentication"** → Claude discovers and maps auth-related tables
- **"Find anomalies in our order data from last month"** → Claude analyzes patterns and flags issues  
- **"What's the relationship between customers and purchases?"** → Claude maps foreign keys and explains business logic
- **"Generate a report on product performance"** → Claude creates dynamic analysis with insights

This transforms databases from static data stores into **interactive knowledge sources** that AI can explore, understand, and analyze in real-time.

## Architecture Overview

### Simple 4-Step Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   1. AI Request │───▶│  2. User Auth   │───▶│ 3. AI Discovery │───▶│  4. Database    │
│                 │    │                 │    │                 │    │     Results     │
│  Claude asks    │    │ User provides   │    │ Claude maps     │    │ Dynamic query   │
│  for database   │    │ credentials     │    │ schema & data   │    │ results with    │
│  access         │    │ via secure UI   │    │ automatically   │    │ insights        │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
```





























### Detailed Architecture Flow

```
┌─────────────────┐                ┌─────────────────┐                ┌─────────────────┐
│   AI Assistant  │                │  PostgreSQL     │                │ User Browser /  │
│    (Claude)     │                │  MCP Server     │                │   Database      │
└─────────────────┘                └─────────────────┘                └─────────────────┘
         │                                   │                                   │
         │ ───────1. start_oauth_flow────────▶                                   │
         │                                   │                                   │
         │ ◀──────2. Session + Login URL──────                                   │
         │                                   │                                   │
         │ "Please visit login URL"          │                                   │
         │                                   │ ◀───3.User visits login URL───────│
         │                                   │                                   │
         │                                   │ ───4.Show authentication form────▶
         │                                   │                                   │
         │                                   │ ◀────5.User submits credential────│
         │                                   │                                   │
         │                                   │ ──────6.Test DB connection───────▶
         │                                   │                                   │
         │                                   │ ◀──────7.Connection success───────│
         │                                   │                                   │
         │                                   │ ─────8.Session authenticated─────▶
         │                                   │                                   │
         │ ────────9.discover_schema ────────▶                                   │
         │                                   │                                   │
         │                                   │ ────10.Query database schema──────▶
         │                                   │                                   │
         │                                   │ ◀─────11.Schema+relationships─────│
         │                                   │                                   │
         │ ◀───12.Complete database map──────                                    │
         │                                   │                                   │
         │ ───────13.execute_query──────────▶                                    │
         │                                   │                                   │
         │                                   │ ───────14.Validate & execute──────▶
         │                                   │                                   │
         │                                   │ ◀───────15.Query results──────────│
         │                                   │                                   │
         │ ◀──────16.AI results+insights─────                                    │
         │                                   │                                   │





```

### What Happens in Each Step

**Steps 1-2: Session Initiation**
- Claude requests database access via `start_oauth_flow`
- Server creates secure session and returns login URL

**Steps 3-8: User Authentication**
- User opens login URL in browser
- Fills out database connection form with credentials
- Server tests connection and creates authenticated session

**Steps 9-12: AI Database Discovery**
- Claude calls `discover_schema` with session ID
- Server automatically maps all tables, relationships, and constraints
- Returns complete database structure to Claude

**Steps 13-16: Query Execution**
- Claude executes queries using session authentication
- Server validates SQL and applies security limits
- Returns formatted results with insights

### Key Security Features

```
┌─────────────────────────────────────────────────────────────────┐
│                        Security Model                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  🔐 Zero-Trust Architecture                                    │
│  ├─ AI never stores database credentials                       │
│  ├─ Session-based authentication only                          │
│  └─ Automatic session expiration                               │
│                                                                 │
│  🛡️ Multi-Layer Protection                                     │
│  ├─ SQL injection prevention                                   │
│  ├─ Query validation and sanitization                          │
│  ├─ Read-only query enforcement                                │
│  └─ Result size and timeout limits                             │
│                                                                 │
│  🔒 Session Isolation                                          │
│  ├─ Each AI session is completely isolated                     │
│  ├─ Encrypted credential storage in memory                     │
│  ├─ Granular permission system (18 operations)                 │
│  └─ Complete audit trail                                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Security & Session Management

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Zero-Trust Security Model                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │ AI Assistant│    │   Session   │    │  Database   │    │  User Auth  │  │
│  │   (Claude)  │    │  Manager    │    │  Security   │    │     UI      │  │
│  └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘  │
│         │                   │                   │                   │       │
│         │ No DB Credentials │ Encrypted Storage │ SQL Injection     │ OAuth │
│         │ Ever Stored       │ Session Isolation │ Prevention        │ Style │
│         │                   │ Auto Expiration   │ Query Validation  │ Flow  │
│         │                   │ Atomic Operations │ Result Limits     │       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

> 📖 **For complete architecture details, security model, and component diagrams, see [ARCHITECTURE.md](./ARCHITECTURE.md)**

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

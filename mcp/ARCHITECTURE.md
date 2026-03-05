# PostgreSQL MCP Server - Architecture Flow

## High-Level Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   AI Assistant  │    │  PostgreSQL     │    │   Web Browser   │    │   PostgreSQL    │
│   (Claude)      │    │  MCP Server     │    │   (User Auth)   │    │   Database      │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
         │                        │                        │                        │
         │                        │                        │                        │
    ┌────▼────┐              ┌────▼────┐              ┌────▼────┐              ┌────▼────┐
    │ MCP     │              │ Session │              │ OAuth   │              │ Schema  │
    │ Client  │              │ Manager │              │ Web UI  │              │ & Data  │
    └─────────┘              └─────────┘              └─────────┘              └─────────┘
```

## Detailed Architecture Flow

### Phase 1: AI-Initiated Database Access Request

```
AI Assistant                    PostgreSQL MCP Server
     │                                    │
     │ 1. start_oauth_flow               │
     │ ─────────────────────────────────▶ │
     │   {                               │
     │     "purpose": "data_analysis",   │ ┌─────────────────────┐
     │     "source": "claude-desktop"    │ │  SessionAuth        │
     │   }                               │ │  ┌───────────────┐  │
     │                                   │ │  │ UUID Session │  │
     │ 2. Session Created + Login URL    │ │  │ Status: PENDING│  │
     │ ◀───────────────────────────────── │ │  │ Expires: 2hrs │  │
     │   {                               │ │  │ MaxUse: 100   │  │
     │     "session_id": "abc123...",    │ │  └───────────────┘  │
     │     "login_url": "http://...",    │ │  ConcurrentHashMap  │
     │     "instructions": [...]         │ └─────────────────────┘
     │   }                               │
     │                                   │
```

### Phase 2: User Authentication Flow

```
User Browser                     Web Authentication UI                 Database Validation
     │                                    │                                    │
     │ 1. Visit Login URL                │                                    │
     │ ─────────────────────────────────▶ │                                    │
     │                                   │                                    │
     │ 2. Responsive Auth Form           │                                    │
     │ ◀───────────────────────────────── │                                    │
     │   ┌─────────────────────────────┐ │                                    │
     │   │ Connection Details          │ │                                    │
     │   │ ├─ Host, Port, Database     │ │                                    │
     │   │ ├─ Username, Password       │ │                                    │
     │   │ └─ Schema                   │ │                                    │
     │   │                             │ │                                    │
     │   │ Database Permissions        │ │                                    │
     │   │ ├─ Schema Discovery ☑       │ │                                    │
     │   │ ├─ Table Listing ☑          │ │                                    │
     │   │ ├─ Execute Queries ☑        │ │                                    │
     │   │ └─ Data Analysis ☑          │ │                                    │
     │   └─────────────────────────────┘ │                                    │
     │                                   │                                    │
     │ 3. Submit Credentials             │ 4. Test DB Connection              │
     │ ─────────────────────────────────▶ │ ─────────────────────────────────▶ │
     │                                   │                                    │
     │                                   │ 5. Connection Success              │
     │                                   │ ◀───────────────────────────────── │
     │ 6. Authentication Success         │                                    │
     │ ◀───────────────────────────────── │                                    │
     │                                   │                                    │
```

### Phase 3: Session Establishment & Security

```
Authentication Service           Session Manager                    Database Service
         │                           │                                   │
         │ 1. Encrypt Credentials    │                                   │
         │ ─────────────────────────▶ │                                   │
         │                           │                                   │
         │                           │ 2. Create Connection Pool         │
         │                           │ ─────────────────────────────────▶ │
         │                           │                                   │
         │                           │ ┌─────────────────────────────────┐ │
         │                           │ │ HikariCP Connection Pool       │ │
         │                           │ │ ├─ Max Connections: 10         │ │
         │                           │ │ ├─ Connection Timeout: 30s     │ │
         │                           │ │ ├─ Query Timeout: 30s          │ │
         │                           │ │ └─ Auto Cleanup on Session End │ │
         │                           │ └─────────────────────────────────┘ │
         │                           │                                   │
         │ 3. Session Authenticated  │                                   │
         │ ◀─────────────────────────── │                                   │
         │                           │                                   │
         │                           │ ┌─────────────────────────────────┐ │
         │                           │ │ SessionAuth (Thread-Safe)      │ │
         │                           │ │ ├─ Status: AUTHENTICATED       │ │
         │                           │ │ ├─ Allowed Operations: [...]   │ │
         │                           │ │ ├─ Encrypted Credentials       │ │
         │                           │ │ ├─ Usage Count: AtomicInteger  │ │
         │                           │ │ └─ Connection Info             │ │
         │                           │ └─────────────────────────────────┘ │
```

### Phase 4: AI Database Discovery & Analysis

```
AI Assistant                 MCP Server Tools                    Database Discovery
     │                            │                                    │
     │ 1. discover_schema         │                                    │
     │ ─────────────────────────▶ │ 2. Validate Session & Permissions │
     │   {                       │ ─────────────────────────────────▶ │
     │     "session_id": "abc123" │                                   │
     │   }                       │                                   │
     │                            │ 3. Schema Discovery Query         │
     │                            │ ─────────────────────────────────▶ │
     │                            │   SELECT table_name,              │
     │                            │          column_name,             │
     │                            │          data_type,               │
     │                            │          is_nullable,             │
     │                            │          column_default           │
     │                            │   FROM information_schema.columns │
     │                            │   WHERE table_schema = 'public'   │
     │                            │                                   │
     │                            │ 4. Foreign Key Analysis           │
     │                            │ ─────────────────────────────────▶ │
     │                            │   SELECT tc.table_name,           │
     │                            │          kcu.column_name,         │
     │                            │          ccu.table_name AS        │
     │                            │          foreign_table_name       │
     │                            │   FROM information_schema.        │
     │                            │        table_constraints tc...    │
     │                            │                                   │
     │ 5. Complete Schema Map     │ 5. Rich Database Context          │
     │ ◀─────────────────────────── │ ◀───────────────────────────────── │
     │   {                       │                                   │
     │     "tables": [...],      │                                   │
     │     "relationships": [...], │                                   │
     │     "constraints": [...], │                                   │
     │     "indexes": [...]      │                                   │
     │   }                       │                                   │
```

### Phase 5: AI-Powered Query Execution

```
AI Assistant              SQL Validator                Query Executor               Database
     │                         │                            │                        │
     │ 1. execute_query        │                            │                        │
     │ ─────────────────────▶  │                            │                        │
     │   {                    │                            │                        │
     │     "session_id": "...", │                            │                        │
     │     "query": "SELECT...", │                            │                        │
     │     "limit": 100       │                            │                        │
     │   }                    │                            │                        │
     │                         │                            │                        │
     │                         │ 2. SQL Injection Check    │                        │
     │                         │ ─────────────────────────▶ │                        │
     │                         │   ├─ Pattern Analysis     │                        │
     │                         │   ├─ Keyword Validation   │                        │
     │                         │   ├─ Suspicious Detection │                        │
     │                         │   └─ Parameterization     │                        │
     │                         │                            │                        │
     │                         │ 3. Query Approved         │                        │
     │                         │ ◀─────────────────────────── │                        │
     │                         │                            │                        │
     │                         │                            │ 4. Execute with Limits │
     │                         │                            │ ─────────────────────▶ │
     │                         │                            │   ├─ Timeout: 30s     │
     │                         │                            │   ├─ Max Rows: 1000   │
     │                         │                            │   └─ Read-Only Mode   │
     │                         │                            │                        │
     │ 5. Formatted Results    │                            │ 5. Raw Results        │
     │ ◀───────────────────────────────────────────────────── │ ◀─────────────────────── │
     │   {                    │                            │                        │
     │     "data": [...],     │                            │                        │
     │     "columns": [...],  │                            │                        │
     │     "row_count": 42,   │                            │                        │
     │     "execution_time": "120ms" │                      │                        │
     │   }                    │                            │                        │
```

## Security Architecture

### Multi-Layer Security Model

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              Security Layers                                        │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Layer 1: Network Security                                                          │
│ ├─ CORS Configuration (Allowed Origins)                                            │
│ ├─ CSRF Protection with Token Repository                                           │
│ └─ HTTPS/TLS Support                                                               │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Layer 2: Authentication & Session Security                                         │
│ ├─ OAuth-Style Web Authentication                                                  │
│ ├─ Zero-Trust Architecture (AI never sees credentials)                            │
│ ├─ BCrypt Password Encoding                                                        │
│ ├─ Session Isolation with Atomic Operations                                        │
│ └─ Automatic Session Expiration (2hrs or 100 operations)                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Layer 3: Query Security                                                            │
│ ├─ SQL Injection Prevention (Multi-pattern Detection)                              │
│ ├─ Query Validation & Sanitization                                                 │
│ ├─ Read-Only Query Enforcement                                                      │
│ ├─ Query Timeout & Result Limits                                                   │
│ └─ Suspicious Query Logging                                                        │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Layer 4: Access Control                                                            │
│ ├─ Granular Permission System (18 Operations)                                      │
│ ├─ Operation-Level Authorization                                                    │
│ ├─ Session-Based Resource Isolation                                                │
│ └─ Audit Trail for All Operations                                                  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Layer 5: Data Protection                                                           │
│ ├─ Encrypted Credential Storage in Memory                                          │
│ ├─ Connection Pool Security                                                         │
│ ├─ Automatic Resource Cleanup                                                       │
│ └─ No Persistent Credential Storage                                                 │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Component Architecture

### Core Components & Responsibilities

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           PostgreSQL MCP Server                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐                │
│  │   MCP Protocol  │    │ Session Manager │    │ Database Service│                │
│  │   ┌───────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │                │
│  │   │ McpService│ │    │ │SessionAuth  │ │    │ │ HikariCP    │ │                │
│  │   │ ├─ Tools  │ │    │ │Service      │ │    │ │ Pools       │ │                │
│  │   │ ├─ Schema │ │    │ │ ├─ Create   │ │    │ │ ├─ Connect  │ │                │
│  │   │ └─ Format │ │    │ │ ├─ Auth     │ │    │ │ ├─ Execute  │ │                │
│  │   └───────────┘ │    │ │ ├─ Validate │ │    │ │ └─ Cleanup  │ │                │
│  └─────────────────┘    │ │ └─ Cleanup  │ │    │ └─────────────┘ │                │
│                         │ └─────────────┘ │    └─────────────────┘                │
│  ┌─────────────────┐    └─────────────────┘                                       │
│  │  Security Layer │                                                               │
│  │  ┌───────────┐  │    ┌─────────────────┐    ┌─────────────────┐                │
│  │  │SqlValidator│  │    │ Web Controllers │    │ Discovery Service│               │
│  │  │ ├─ Inject  │  │    │ ┌─────────────┐ │    │ ┌─────────────┐ │                │
│  │  │ ├─ Pattern │  │    │ │SessionAuth  │ │    │ │ Schema      │ │                │
│  │  │ └─ Audit   │  │    │ │Controller   │ │    │ │ Discovery   │ │                │
│  │  └───────────┘  │    │ │ ├─ Login UI │ │    │ │ ├─ Tables    │ │                │
│  └─────────────────┘    │ │ ├─ Auth API │ │    │ │ ├─ Relations │ │                │
│                         │ │ └─ Permissions│ │    │ │ └─ Metadata │ │                │
│                         │ └─────────────┘ │    │ └─────────────┘ │                │
│                         └─────────────────┘    └─────────────────┘                │
│                                                                                     │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                          Infrastructure Layer                                      │
│                                                                                     │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐                │
│  │   Monitoring    │    │   Configuration │    │    Deployment   │                │
│  │ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │                │
│  │ │ Prometheus  │ │    │ │Spring Boot  │ │    │ │   Docker    │ │                │
│  │ │ ├─ Metrics  │ │    │ │Config       │ │    │ │ ├─ Multi-   │ │                │
│  │ │ ├─ Health   │ │    │ │ ├─ Database │ │    │ │ │   Service  │ │                │
│  │ │ └─ Alerts   │ │    │ │ ├─ Security │ │    │ │ ├─ Networks │ │                │
│  │ └─────────────┘ │    │ │ └─ Threads  │ │    │ │ └─ Volumes  │ │                │
│  └─────────────────┘    │ └─────────────┘ │    │ └─────────────┘ │                │
│                         └─────────────────┘    └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Data Flow Sequence

### Complete Request-Response Cycle

```
Sequence: AI asks "What tables contain user data?"

1. AI → MCP Server: start_oauth_flow
2. MCP Server → SessionManager: createSession()
3. SessionManager → Memory: store(SessionAuth)
4. MCP Server → AI: {session_id, login_url}

5. User → Browser: visit login_url
6. Browser → AuthController: display login form
7. User → Browser: submit credentials + permissions
8. Browser → AuthController: POST /authenticate

9. AuthController → Database: test connection
10. AuthController → CredentialService: encrypt(password)
11. AuthController → SessionManager: authenticateSession()
12. SessionManager → Memory: update session status
13. SessionManager → DatabaseService: createConnectionPool()

14. AI → MCP Server: discover_schema(session_id)
15. MCP Server → SessionManager: validateSession()
16. SessionManager → Memory: check session + increment usage
17. MCP Server → SchemaDiscovery: discoverDatabaseSchema()
18. SchemaDiscovery → Database: information_schema queries
19. Database → SchemaDiscovery: table/column metadata
20. SchemaDiscovery → MCP Server: structured schema
21. MCP Server → AI: formatted schema with relationships

22. AI → MCP Server: execute_query(session_id, "SELECT table_name FROM information_schema.tables WHERE table_name LIKE '%user%'")
23. MCP Server → SqlValidator: validateQuery()
24. SqlValidator → QueryExecutor: approved query
25. QueryExecutor → Database: execute with limits
26. Database → QueryExecutor: results
27. QueryExecutor → MCP Server: formatted results
28. MCP Server → AI: user-related tables found
```

## Technology Stack

```
┌─────────────────────────────────────────────────────────────────┐
│                        Technology Stack                        │
├─────────────────────────────────────────────────────────────────┤
│ Language & Framework                                            │
│ ├─ Kotlin (Primary Language)                                   │
│ ├─ Spring Boot 3.2.0 (Application Framework)                  │
│ ├─ Spring Security (Authentication & Authorization)            │
│ └─ Spring Web (REST Controllers)                               │
├─────────────────────────────────────────────────────────────────┤
│ Database & Connection Management                                │
│ ├─ PostgreSQL (Target Database)                                │
│ ├─ HikariCP (Connection Pooling)                               │
│ ├─ JDBC (Database Connectivity)                                │
│ └─ Information Schema (Metadata Discovery)                     │
├─────────────────────────────────────────────────────────────────┤
│ Frontend & UI                                                   │
│ ├─ Thymeleaf (Server-Side Templating)                         │
│ ├─ Bootstrap 5.3 (CSS Framework)                              │
│ ├─ Font Awesome (Icons)                                        │
│ └─ Responsive Design (Mobile-First)                            │
├─────────────────────────────────────────────────────────────────┤
│ Protocol & Communication                                        │
│ ├─ JSON-RPC 2.0 (MCP Protocol)                                │
│ ├─ REST APIs (Authentication & Management)                     │
│ ├─ HTTP/HTTPS (Transport Layer)                               │
│ └─ Jackson (JSON Processing)                                   │
├─────────────────────────────────────────────────────────────────┤
│ Security & Encryption                                           │
│ ├─ BCrypt (Password Hashing)                                  │
│ ├─ AES (Credential Encryption)                                │
│ ├─ CSRF Protection (Spring Security)                          │
│ └─ CORS (Cross-Origin Resource Sharing)                       │
├─────────────────────────────────────────────────────────────────┤
│ Monitoring & Operations                                         │
│ ├─ Prometheus (Metrics Collection)                            │
│ ├─ Spring Actuator (Health Checks)                            │
│ ├─ SLF4J + Logback (Structured Logging)                       │
│ └─ Micrometer (Application Metrics)                            │
├─────────────────────────────────────────────────────────────────┤
│ Deployment & Containerization                                   │
│ ├─ Docker (Containerization)                                  │
│ ├─ Docker Compose (Multi-Service Orchestration)               │
│ ├─ Gradle (Build Tool)                                        │
│ └─ OpenJDK 17 (Runtime Environment)                           │
└─────────────────────────────────────────────────────────────────┘
```

## Performance & Scalability

### Concurrent Session Handling

```
Load Balancing Strategy:
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ AI Assistant 1  │    │ AI Assistant 2  │    │ AI Assistant N  │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│               PostgreSQL MCP Server                            │
│                                                                 │
│  Thread Pool (Tomcat)     Session Manager (Memory)             │
│  ├─ Max: 100 threads     ├─ ConcurrentHashMap                  │
│  ├─ Min: 10 spare        ├─ Atomic Operations                  │
│  ├─ Queue: 200 requests  ├─ Thread-Safe Access                 │
│  └─ Auto-scaling         └─ Session Cleanup                    │
│                                                                 │
│  Connection Pools (HikariCP)                                   │
│  ├─ Pool per Database                                           │
│  ├─ Max Connections: 10                                        │
│  ├─ Connection Timeout: 30s                                    │
│  └─ Auto-cleanup on Session End                                │
└─────────────────────────────────────────────────────────────────┘
          │                      │                      │
          ▼                      ▼                      ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  PostgreSQL     │    │  PostgreSQL     │    │  PostgreSQL     │
│  Database 1     │    │  Database 2     │    │  Database N     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

This architecture provides a comprehensive view of how the PostgreSQL MCP Server enables zero-configuration AI database access while maintaining enterprise-grade security and performance.
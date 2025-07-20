package com.kasafal.mcp.config


import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.mcp")
data class McpConfig(
    var serverName: String = "PostgreSQL MCP Server",
    var serverVersion: String = "1.0.0",
    var protocolVersion: String = "2024-11-05",
    var maxToolCalls: Int = 100,
    var enableDetailedErrors: Boolean = false
)
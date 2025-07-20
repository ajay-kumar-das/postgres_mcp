package com.kasafal.mcp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.database")
data class DatabaseConfig(
    var maxConnections: Int = 10,
    var defaultQueryTimeout: Int = 30,
    var maxResultRows: Int = 1000,
    var enableQueryLogging: Boolean = false
)

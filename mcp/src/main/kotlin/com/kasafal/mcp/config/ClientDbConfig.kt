package com.kasafal.mcp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "client.database")
class ClientDbConfig {
    lateinit var name: String
    lateinit var host: String
    lateinit var database: String
    lateinit var username: String
    lateinit var password: String
    var port: Int = 5432
    var schema: String = "public"
    lateinit var description: String
}
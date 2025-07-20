package com.kasafal.mcp.model.mcp

import com.fasterxml.jackson.annotation.JsonProperty

data class McpTool(
    val name: String,
    val description: String,
    @JsonProperty("inputSchema")
    val inputSchema: Map<String, Any>
)
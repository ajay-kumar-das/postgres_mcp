package com.kasafal.mcp.model.mcp

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: Any? = null,
    val error: McpError? = null
)
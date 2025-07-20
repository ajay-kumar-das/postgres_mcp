package com.kasafal.mcp.model.mcp

data class McpError(
    val message: String,
    val code: Int,
    val data: Any? = null
)
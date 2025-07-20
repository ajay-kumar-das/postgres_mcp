package com.kasafal.mcp.model.mcp

data class McpPrompt(
    val name: String,
    val description: String,
    val arguments: List<McpPromptArgument>? = null
)

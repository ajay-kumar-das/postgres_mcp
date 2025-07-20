package com.kasafal.mcp.model.mcp

data class McpPromptArgument(
    val name: String,
    val description: String,
    val required: Boolean = false
)

data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any>
)

data class ToolResult(
    val content: List<Content>,
    val isError: Boolean = false
)

sealed class Content {
    data class TextContent(val text: String) : Content()
    data class ImageContent(val data: String, val mimeType: String) : Content()
    data class ResourceContent(val resource: McpResource) : Content()
}

// Server capabilities
data class ServerCapabilities(
    val logging: Map<String, Any> = emptyMap(), // Default to empty, not null
    val prompts: Map<String, Any>? = mapOf("listChanged" to true),
    val resources: Map<String, Any> = emptyMap(), // Default to empty, not null
    val tools: Map<String, Any>? = mapOf("listChanged" to true)
)

data class InitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

data class ServerInfo(
    val name: String,
    val version: String
)
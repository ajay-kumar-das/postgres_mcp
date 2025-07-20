package com.kasafal.mcp.controller

import com.kasafal.mcp.model.mcp.McpRequest
import com.kasafal.mcp.model.mcp.McpResponse
import com.kasafal.mcp.service.McpServerService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/mcp")
@CrossOrigin
class McpController(
    private val mcpServerService: McpServerService
) {

    private val logger = KotlinLogging.logger {}

    @PostMapping
    fun handleMcpRequest(@RequestBody request: McpRequest): ResponseEntity<McpResponse> {
        logger.info { "Received MCP request: ${request.method}" }

        val response = mcpServerService.handleMcpRequest(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun getToolsList(): ResponseEntity<McpResponse> {
        logger.info { "Get tool list" }

        val response = mcpServerService.listTools()
        return ResponseEntity.ok(response)
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf(
            "status" to "healthy",
            "service" to "PostgreSQL MCP Server",
            "version" to "1.0.0"
        ))
    }
}

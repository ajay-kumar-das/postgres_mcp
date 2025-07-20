package com.kasafal.mcp.exception

import com.kasafal.mcp.model.mcp.McpError
import com.kasafal.mcp.model.mcp.McpResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.sql.SQLException

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(McpException::class)
    fun handleMcpException(e: McpException): ResponseEntity<McpResponse> {
        logger.error(e) { "MCP Exception: ${e.message}" }

        val response = McpResponse(
            id = "error",
            error = McpError(e.message ?: "Unknown MCP error", -32000)
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(DatabaseException::class)
    fun handleDatabaseException(e: DatabaseException): ResponseEntity<Map<String, Any>> {
        logger.error(e) { "Database Exception: ${e.message}" }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
            "error" to "Database error",
            "message" to (e.message ?: "Unknown database error"),
            "timestamp" to System.currentTimeMillis()
        ))
    }

    @ExceptionHandler(SQLException::class)
    fun handleSqlException(e: SQLException): ResponseEntity<Map<String, Any>> {
        logger.error(e) { "SQL Exception: ${e.message}" }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
            "error" to "SQL error",
            "message" to (e.message ?: "Unknown SQL error"),
            "sqlState" to e.sqlState,
            "errorCode" to e.errorCode,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<Map<String, Any>> {
        logger.error(e) { "Validation error: ${e.message}" }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
            "error" to "Validation error",
            "message" to (e.message ?: "Invalid argument"),
            "timestamp" to System.currentTimeMillis()
        ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<Map<String, Any>> {
        logger.error(e) { "Unexpected error: ${e.message}" }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
            "error" to "Internal server error",
            "message" to "An unexpected error occurred",
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
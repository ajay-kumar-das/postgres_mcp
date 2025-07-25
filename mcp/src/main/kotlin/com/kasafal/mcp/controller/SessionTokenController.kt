package com.kasafal.mcp.controller

import com.kasafal.mcp.model.session.*
import com.kasafal.mcp.service.SessionTokenService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

private val logger = KotlinLogging.logger {}

/**
 * Controller for managing session tokens.
 * Provides secure endpoints for creating, validating, and invalidating database session tokens.
 * 
 * Security Notes:
 * - These endpoints should be behind proper authentication in production
 * - Consider rate limiting for token creation
 * - Use HTTPS in production to protect credentials in transit
 */
@RestController
@RequestMapping("/api/session")
class SessionTokenController(
    private val sessionTokenService: SessionTokenService
) {

    /**
     * Create a new session token with database credentials.
     * This endpoint accepts database credentials and returns a secure token.
     * 
     * POST /api/session/tokens
     */
    @PostMapping("/tokens")
    fun createSessionToken(
        @Valid @RequestBody request: CreateSessionTokenRequest
    ): ResponseEntity<*> {
        return try {
            logger.info { "Creating session token for database connection: ${request.name}" }
            logger.debug { "Request details - Host: ${request.host}:${request.port}, Database: ${request.database}, User: ${request.username}" }
            
            val response = sessionTokenService.createSessionToken(request)
            
            logger.info { "Session token created successfully: ${response.tokenId}" }
            ResponseEntity.ok(response)
            
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Invalid request for session token creation: ${e.message}" }
            val errorResponse = mapOf(
                "error" to "INVALID_REQUEST",
                "message" to (e.message ?: "Invalid request parameters"),
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            ResponseEntity.badRequest().body(errorResponse)
            
        } catch (e: java.sql.SQLException) {
            logger.error(e) { "Database connection error: ${e.message}" }
            val errorResponse = mapOf(
                "error" to "DATABASE_CONNECTION_ERROR",
                "message" to "Cannot connect to database: ${e.message}",
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            ResponseEntity.badRequest().body(errorResponse)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to create session token: ${e.message}" }
            val errorResponse = mapOf(
                "error" to "INTERNAL_SERVER_ERROR",
                "message" to "An unexpected error occurred while creating the token",
                "details" to (e.message ?: "Unknown error"),
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

    /**
     * Get token usage statistics.
     * Returns non-sensitive information about token usage.
     * 
     * GET /api/session/tokens/{tokenId}/stats
     */
    @GetMapping("/tokens/{tokenId}/stats")
    fun getTokenStats(
        @PathVariable tokenId: String
    ): ResponseEntity<TokenUsageStats> {
        return try {
            val stats = sessionTokenService.getTokenStats(tokenId)
            
            if (stats != null) {
                ResponseEntity.ok(stats)
            } else {
                ResponseEntity.notFound().build()
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get token stats for: $tokenId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Invalidate a session token immediately.
     * Use this when you're done with database operations or in case of security concerns.
     * 
     * DELETE /api/session/tokens/{tokenId}
     */
    @DeleteMapping("/tokens/{tokenId}")
    fun invalidateToken(
        @PathVariable tokenId: String
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val wasInvalidated = sessionTokenService.invalidateToken(tokenId)
            
            val response = if (wasInvalidated) {
                mapOf(
                    "success" to true,
                    "message" to "Token invalidated successfully",
                    "tokenId" to tokenId
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "Token not found or already invalidated",
                    "tokenId" to tokenId
                )
            }
            
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to invalidate token: $tokenId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Get system statistics about active tokens.
     * Useful for monitoring and administration.
     * 
     * GET /api/session/stats
     */
    @GetMapping("/stats")
    fun getSystemStats(): ResponseEntity<Map<String, Any>> {
        return try {
            val activeTokenCount = sessionTokenService.getActiveTokenCount()
            
            val stats = mapOf(
                "activeTokens" to activeTokenCount,
                "timestamp" to java.time.LocalDateTime.now(),
                "message" to "Session token system statistics"
            )
            
            ResponseEntity.ok(stats)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get system stats" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Emergency endpoint to invalidate all active tokens.
     * Should be used only in security incidents or system maintenance.
     * 
     * DELETE /api/session/tokens
     */
    @DeleteMapping("/tokens")
    fun invalidateAllTokens(): ResponseEntity<Map<String, Any>> {
        return try {
            logger.warn { "Emergency invalidation of all tokens requested" }
            
            val invalidatedCount = sessionTokenService.invalidateAllTokens()
            
            val response = mapOf(
                "success" to true,
                "message" to "All tokens have been invalidated",
                "invalidatedCount" to invalidatedCount,
                "timestamp" to java.time.LocalDateTime.now()
            )
            
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to invalidate all tokens" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Health check endpoint for the session token system
     * 
     * GET /api/session/health
     */
    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return try {
            val activeTokenCount = sessionTokenService.getActiveTokenCount()
            
            val health = mapOf<String, Any>(
                "status" to "healthy",
                "service" to "Session Token Management",
                "activeTokens" to activeTokenCount,
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            
            ResponseEntity.ok(health)
            
        } catch (e: Exception) {
            logger.error(e) { "Session token system health check failed" }
            
            val health = mapOf<String, Any>(
                "status" to "unhealthy",
                "service" to "Session Token Management",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health)
        }
    }

    /**
     * Debug endpoint to test API connectivity and request format
     * 
     * POST /api/session/debug
     */
    @PostMapping("/debug")
    fun debugRequest(
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        logger.info { "Debug request received with keys: ${request.keys}" }
        
        val response = mapOf(
            "status" to "debug_success",
            "message" to "API endpoint is reachable",
            "receivedKeys" to request.keys.toList(),
            "timestamp" to java.time.LocalDateTime.now().toString()
        )
        
        return ResponseEntity.ok(response)
    }

    /**
     * Test database connection directly without token creation
     * 
     * POST /api/session/test-connection
     */
    @PostMapping("/test-connection")
    fun testConnection(
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val host = request["host"] as? String ?: "localhost"
            val port = (request["port"] as? Int) ?: 5432
            val database = request["database"] as? String ?: "postgres"
            val username = request["username"] as? String ?: "postgres"
            val password = request["password"] as? String ?: ""
            
            logger.info { "Testing connection to $host:$port/$database as $username" }
            
            val url = "jdbc:postgresql://$host:$port/$database"
            Class.forName("org.postgresql.Driver")
            
            java.sql.DriverManager.getConnection(url, username, password).use { connection ->
                val isValid = connection.isValid(5)
                val metadata = connection.metaData
                
val response = mapOf<String, Any>(
                    "status" to "connection_success",
                    "message" to "Database connection successful",
                    "url" to url,
                    "username" to username,
                    "isValid" to isValid,
                    "databaseProductName" to metadata.databaseProductName,
                    "databaseProductVersion" to metadata.databaseProductVersion,
                    "timestamp" to java.time.LocalDateTime.now().toString()
                )
                
                ResponseEntity.ok(response)
            }
        } catch (e: java.sql.SQLException) {
            logger.error(e) { "SQL Exception during connection test: ${e.sqlState} - ${e.message}" }
val errorResponse = mapOf<String, Any>(
                "status" to "connection_failed",
                "error" to "SQL_EXCEPTION",
                "message" to (e.message ?: "Unknown SQL error"),
                "sqlState" to e.sqlState,
                "errorCode" to e.errorCode,
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            ResponseEntity.badRequest().body(errorResponse)
        } catch (e: ClassNotFoundException) {
            logger.error(e) { "PostgreSQL JDBC driver not found" }
val errorResponse = mapOf<String, Any>(
                "status" to "connection_failed",
                "error" to "DRIVER_NOT_FOUND",
                "message" to "PostgreSQL JDBC driver not available",
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during connection test" }
val errorResponse = mapOf<String, Any>(
                "status" to "connection_failed",
                "error" to "UNEXPECTED_ERROR",
                "message" to (e.message ?: "Unknown error"),
                "type" to e.javaClass.simpleName,
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }
}

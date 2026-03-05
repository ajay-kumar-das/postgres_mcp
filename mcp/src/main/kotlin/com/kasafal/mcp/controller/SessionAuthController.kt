package com.kasafal.mcp.controller

import com.kasafal.mcp.model.session.*
import com.kasafal.mcp.service.SessionAuthenticationService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

private val logger = KotlinLogging.logger {}

/**
 * Controller for session-based authentication (no tokens).
 * Handles session creation, authentication, and status management.
 */
@Controller
@RequestMapping("/api/auth")
class SessionAuthController(
    private val sessionAuthService: SessionAuthenticationService
) {
    
    /**
     * Generate login URL for a session
     * GET /api/auth/login-url?session_id=xxx
     */
    @GetMapping("/login-url")
    @ResponseBody
    fun generateLoginUrl(
        @RequestParam("session_id") sessionId: String,
        @RequestParam(defaultValue = "database_access") purpose: String
    ): ResponseEntity<Map<String, Any>> {
        
        val loginUrl = "http://localhost:8081/api/auth/login?session_id=$sessionId"
        
        return ResponseEntity.ok(mapOf(
            "login_url" to loginUrl,
            "session_id" to sessionId,
            "purpose" to purpose,
            "message" to "Please visit the login URL to authenticate"
        ))
    }
    
    /**
     * Get available database operations for permission selection
     * GET /api/auth/permissions
     */
    @GetMapping("/permissions")
    @ResponseBody
    fun getAvailablePermissions(): ResponseEntity<Map<String, Any>> {
        val groupedPermissions = DatabaseOperation.values()
            .groupBy { it.category }
            .mapValues { (_, operations) ->
                operations.map { op ->
                    mapOf(
                        "operation" to op.name,
                        "displayName" to op.displayName,
                        "description" to op.description,
                        "category" to op.category
                    )
                }
            }
        
        return ResponseEntity.ok(mapOf(
            "categories" to groupedPermissions,
            "defaultPermissions" to listOf(
                DatabaseOperation.SCHEMA_DISCOVERY.name,
                DatabaseOperation.TABLE_LISTING.name,
                DatabaseOperation.SELECT_QUERIES.name,
                DatabaseOperation.SQL_VALIDATION.name
            ),
            "allPermissions" to DatabaseOperation.values().map { it.name }
        ))
    }

    /**
     * Show login page for a session with permission selection
     * GET /api/auth/login?session_id=xxx&source=xxx
     */
    @GetMapping("/login")
    fun loginPage(
        @RequestParam("session_id") sessionId: String,
        @RequestParam(value = "source", defaultValue = "unknown") source: String,
        model: Model
    ): String {
        // Check if session exists
        val sessionStatus = sessionAuthService.getSessionStatus(sessionId)
        
        if (sessionStatus == null) {
            model.addAttribute("error", "Session not found: $sessionId")
            return "auth-error"
        }
        
        if (sessionStatus.status == AuthStatus.EXPIRED) {
            model.addAttribute("error", "Session has expired: $sessionId")
            return "auth-error"
        }
        
        if (sessionStatus.status == AuthStatus.AUTHENTICATED) {
            model.addAttribute("message", "Session is already authenticated")
            model.addAttribute("sessionId", sessionId)
            model.addAttribute("allowedOperations", sessionStatus.allowedOperations.map { it.displayName })
            return "claude-auth-complete"
        }
        
        // Add permissions data for the UI
        val groupedPermissions = DatabaseOperation.values()
            .groupBy { it.category }
            .mapValues { (_, operations) ->
                operations.map { op ->
                    mapOf(
                        "operation" to op.name,
                        "displayName" to op.displayName,
                        "description" to op.description,
                        "checked" to (op in setOf(
                            DatabaseOperation.SCHEMA_DISCOVERY,
                            DatabaseOperation.TABLE_LISTING,
                            DatabaseOperation.SELECT_QUERIES,
                            DatabaseOperation.SQL_VALIDATION
                        ))
                    )
                }
            }
        
        model.addAttribute("sessionId", sessionId)
        model.addAttribute("purpose", "database_access")
        model.addAttribute("permissionCategories", groupedPermissions)
        model.addAttribute("source", source)
        return "auth-login"
    }
    
    /**
     * Authenticate a session with database credentials
     * POST /api/auth/authenticate
     */
    @PostMapping("/authenticate")
    @ResponseBody
    fun authenticate(@Valid @RequestBody request: AuthenticateSessionRequest): ResponseEntity<*> {
        logger.info { "Authentication attempt for session: ${request.sessionId}" }
        
        return try {
            val response = sessionAuthService.authenticateSession(request)
            
            logger.info { "Authentication successful for session: ${request.sessionId}" }
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "sessionId" to response.sessionId,
                "status" to response.status.name,
                "message" to response.message,
                "connectionName" to response.connectionName,
                "expiresAt" to response.expiresAt.toString()
            ))
            
        } catch (e: Exception) {
            logger.error(e.message) { "Authentication failed for session: ${request.sessionId}" }
            
            val errorResponse = mapOf(
                "success" to false,
                "error" to "AUTHENTICATION_FAILED",
                "message" to (e.message ?: "Authentication failed"),
                "timestamp" to java.time.LocalDateTime.now().toString()
            )
            ResponseEntity.badRequest().body(errorResponse)
        }
    }
    
    /**
     * Get session status
     * GET /api/auth/sessions/{sessionId}/status
     */
    @GetMapping("/sessions/{sessionId}/status")
    @ResponseBody
    fun getSessionStatus(@PathVariable sessionId: String): ResponseEntity<*> {
        val sessionStatus = sessionAuthService.getSessionStatus(sessionId)
        
        return if (sessionStatus != null) {
            ResponseEntity.ok(sessionStatus)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to "SESSION_NOT_FOUND",
                "message" to "Session not found: $sessionId",
                "timestamp" to java.time.LocalDateTime.now().toString()
            ))
        }
    }
    
    /**
     * Get session statistics
     * GET /api/auth/sessions/{sessionId}/stats
     */
    @GetMapping("/sessions/{sessionId}/stats")
    @ResponseBody
    fun getSessionStats(@PathVariable sessionId: String): ResponseEntity<*> {
        val stats = sessionAuthService.getSessionStats(sessionId)
        
        return if (stats != null) {
            ResponseEntity.ok(stats)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to "SESSION_NOT_FOUND",
                "message" to "Session not found: $sessionId",
                "timestamp" to java.time.LocalDateTime.now().toString()
            ))
        }
    }
    
    /**
     * Invalidate a session
     * DELETE /api/auth/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseBody
    fun invalidateSession(@PathVariable sessionId: String): ResponseEntity<Map<String, Any>> {
        val wasInvalidated = sessionAuthService.invalidateSession(sessionId)
        
        return if (wasInvalidated) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Session invalidated successfully",
                "sessionId" to sessionId
            ))
        } else {
            ResponseEntity.ok(mapOf(
                "success" to false,
                "message" to "Session not found or already invalidated",
                "sessionId" to sessionId
            ))
        }
    }
    
    /**
     * Get system statistics
     * GET /api/auth/stats
     */
    @GetMapping("/stats")
    @ResponseBody
    fun getSystemStats(): ResponseEntity<Map<String, Any>> {
        return try {
            val stats = sessionAuthService.getAllSessionStats()
            val response = mapOf(
                "sessionStats" to stats,
                "activeSessionCount" to sessionAuthService.getActiveSessionCount(),
                "timestamp" to java.time.LocalDateTime.now(),
                "message" to "Session authentication system statistics"
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get system stats" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to "INTERNAL_SERVER_ERROR",
                "message" to "Failed to retrieve system statistics",
                "timestamp" to java.time.LocalDateTime.now().toString()
            ))
        }
    }
    
    /**
     * Emergency endpoint to invalidate all sessions
     * DELETE /api/auth/sessions
     */
    @DeleteMapping("/sessions")
    @ResponseBody
    fun invalidateAllSessions(): ResponseEntity<Map<String, Any>> {
        return try {
            logger.warn { "Emergency invalidation of all sessions requested" }
            
            val invalidatedCount = sessionAuthService.invalidateAllSessions()
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "All sessions have been invalidated",
                "invalidatedCount" to invalidatedCount,
                "timestamp" to java.time.LocalDateTime.now()
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to invalidate all sessions" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to "INTERNAL_SERVER_ERROR",
                "message" to "Failed to invalidate all sessions",
                "timestamp" to java.time.LocalDateTime.now().toString()
            ))
        }
    }
    
    /**
     * Health check endpoint
     * GET /api/auth/health
     */
    @GetMapping("/health")
    @ResponseBody
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return try {
            val activeSessionCount = sessionAuthService.getActiveSessionCount()
            
            ResponseEntity.ok(mapOf(
                "status" to "healthy",
                "service" to "Session Authentication",
                "activeSessionCount" to activeSessionCount,
                "timestamp" to java.time.LocalDateTime.now().toString()
            ))
        } catch (e: Exception) {
            logger.error(e) { "Session authentication system health check failed" }
            
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf(
                "status" to "unhealthy",
                "service" to "Session Authentication",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to java.time.LocalDateTime.now().toString()
            ))
        }
    }
    
    /**
     * Test endpoint for connectivity
     * GET /api/auth/test
     */
    @GetMapping("/test")
    @ResponseBody
    fun test(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "working",
            "message" to "Session authentication controller is functioning",
            "timestamp" to java.time.LocalDateTime.now().toString()
        ))
    }
}

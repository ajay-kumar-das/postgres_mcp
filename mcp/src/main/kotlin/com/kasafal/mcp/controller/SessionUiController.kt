package com.kasafal.mcp.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Controller for serving the session authentication UI.
 * This provides a simple web interface for users to create and manage database sessions.
 */
@Controller
class SessionUiController {

    @GetMapping("/session-ui")
    fun sessionAuthenticationUI(
        @RequestParam(required = false) purpose: String?,
        @RequestParam(required = false) operations: String?,
        model: Model
    ): String {
        model.addAttribute("purpose", purpose ?: "Database access")
        model.addAttribute("requestedOperations", operations?.split(",") ?: listOf("SELECT_QUERIES", "SCHEMA_DISCOVERY"))
        
        return "session-ui"
    }
}

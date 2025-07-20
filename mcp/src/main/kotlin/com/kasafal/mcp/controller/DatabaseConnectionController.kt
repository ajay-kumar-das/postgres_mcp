package com.kasafal.mcp.controller

import com.kasafal.mcp.model.database.DatabaseConnection
import com.kasafal.mcp.model.database.DatabaseConnectionDto
import com.kasafal.mcp.service.DatabaseService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/connections")
@CrossOrigin
class DatabaseConnectionController(
    private val databaseService: DatabaseService
) {

    @GetMapping
    fun getAllConnections(): ResponseEntity<List<DatabaseConnection>> {
        return ResponseEntity.ok(databaseService.getAllConnections())
    }

    @GetMapping("/{id}")
    fun getConnection(@PathVariable id: Long): ResponseEntity<DatabaseConnection> {
        return ResponseEntity.ok(databaseService.getConnection(id))
    }

    @PostMapping
    fun createConnection(@Valid @RequestBody dto: DatabaseConnectionDto): ResponseEntity<DatabaseConnection> {
        val connection = databaseService.createConnection(dto)
        return ResponseEntity.status(HttpStatus.CREATED).body(connection)
    }

    @DeleteMapping("/{id}")
    fun deleteConnection(@PathVariable id: Long): ResponseEntity<Void> {
        databaseService.deleteConnection(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/test")
    fun testConnection(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        val isValid = databaseService.testConnection(id)
        return ResponseEntity.ok(mapOf(
            "connectionId" to id,
            "isValid" to isValid,
            "message" to if (isValid) "Connection successful" else "Connection failed"
        ))
    }
}
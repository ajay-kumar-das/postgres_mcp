package com.kasafal.mcp.repository

import com.kasafal.mcp.model.database.DatabaseConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DatabaseConnectionRepository : JpaRepository<DatabaseConnection, Long> {
    fun findByIsActiveTrue(): List<DatabaseConnection>
    fun findByNameIgnoreCase(name: String): DatabaseConnection?
    fun findByNameAndHostAndPortAndDatabaseAndUsernameAndSchema(
        name: String,
        host: String,
        port: Int,
        database: String,
        username: String,
        schema: String
    ): DatabaseConnection?
}
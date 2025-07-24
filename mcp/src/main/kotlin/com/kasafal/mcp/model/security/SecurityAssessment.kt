package com.kasafal.mcp.model.security

import java.time.LocalDateTime

data class SecurityAssessment(
    var entropyScore: Double = 0.0,
    var mlRiskScore: Double = 0.0,
    var complexityScore: Double = 0.0,
    var patternMatchScore: Double = 0.0,
    private val violations: MutableList<String> = mutableListOf(),
    private val warnings: MutableList<String> = mutableListOf(),
    private val detectedTechniques: MutableList<String> = mutableListOf()
) {
    fun addViolation(violation: String) = violations.add(violation)
    fun addWarning(warning: String) = warnings.add(warning)
    fun addDetectedTechnique(technique: String) = detectedTechniques.add(technique)

    fun hasViolations() = violations.isNotEmpty()
    fun hasHighRisk() = mlRiskScore > 0.8 || entropyScore > 4.5 || violations.isNotEmpty()

    fun getViolations() = violations.toList()
    fun getWarnings() = warnings.toList()
    fun getDetectedTechniques() = detectedTechniques.toList()

    fun getRiskLevel(): RiskLevel {
        return when {
            hasViolations() -> RiskLevel.CRITICAL
            mlRiskScore > 0.7 || entropyScore > 4.0 -> RiskLevel.HIGH
            mlRiskScore > 0.5 || entropyScore > 3.0 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
}

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class QueryExecutionContext(
    val connectionId: Long,
    val originalQuery: String,
    val clientInfo: String? = null,
    val sessionId: String? = null,
    val requestTimestamp: LocalDateTime = LocalDateTime.now(),
    val maxRows: Int = 1000,
    val timeoutSeconds: Int = 30
)

data class SecurityViolationEvent(
    val query: String,
    val violationType: String,
    val riskLevel: RiskLevel,
    val details: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val connectionId: Long,
    val clientInfo: String? = null
)

data class QueryFingerprint(
    val normalizedQuery: String,
    val hash: String,
    val firstSeen: LocalDateTime = LocalDateTime.now(),
    var attemptCount: Int = 1,
    var lastSeen: LocalDateTime = LocalDateTime.now()
)

data class SqlParseResult(
    val isValid: Boolean,
    val statement: Any? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class SecureQueryResult(
    val originalQuery: String,
    val rewrittenQuery: String,
    val executionResult: Any,
    val securityAssessment: SecurityAssessment,
    val executionTimeMs: Long,
    val rowsReturned: Int
)
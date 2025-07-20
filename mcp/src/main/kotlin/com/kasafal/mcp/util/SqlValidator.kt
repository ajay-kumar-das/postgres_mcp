package com.kasafal.mcp.util

import com.kasafal.mcp.model.database.ValidationResult
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class SqlValidator {

    private val dangerousKeywords = setOf(
        "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "TRUNCATE",
        "EXEC", "EXECUTE", "CALL", "GRANT", "REVOKE", "MERGE", "REPLACE",
        "LOCK", "UNLOCK", "SET", "RESET", "SHUTDOWN", "KILL"
    )

    private val allowedSelectKeywords = setOf(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER",
        "ON", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN", "LIKE", "ILIKE",
        "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "UNION", "INTERSECT",
        "EXCEPT", "DISTINCT", "ALL", "AS", "CASE", "WHEN", "THEN", "ELSE", "END",
        "CAST", "EXTRACT", "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE",
        "NULLIF", "SUBSTRING", "LOWER", "UPPER", "TRIM", "LENGTH", "CONCAT",
        "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "WITH"
    )

    private val sqlInjectionPatterns = listOf(
        // More specific patterns to avoid false positives
        Pattern.compile("(?i).*;\\s*(drop|delete|update|insert|alter|create|truncate)\\s+", Pattern.MULTILINE),
        Pattern.compile("(?i)\\bunion\\s+select\\b.*\\bfrom\\b", Pattern.MULTILINE),
        Pattern.compile("(?i)\\bor\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?", Pattern.MULTILINE),
        Pattern.compile("(?i)\\band\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?", Pattern.MULTILINE),
        Pattern.compile("(?i)'\\s*(or|and)\\s+", Pattern.MULTILINE)
    )

    fun validateSelectQuery(query: String): ValidationResult {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isEmpty()) {
            return ValidationResult(false, "Query cannot be empty")
        }

        // Check basic structure
        val basicValidation = validateBasicStructure(trimmedQuery)
        if (!basicValidation.isValid) {
            return basicValidation
        }

        // Check if it's a SELECT query
        if (!isSelectQuery(trimmedQuery)) {
            return ValidationResult(false, "Only SELECT queries are allowed in this context")
        }

        // Check for dangerous keywords
        val dangerousCheck = checkForDangerousKeywords(trimmedQuery)
        if (!dangerousCheck.isValid) {
            return dangerousCheck
        }

        // Check for SQL injection patterns
        val injectionCheck = checkForSqlInjection(trimmedQuery)
        if (!injectionCheck.isValid) {
            return injectionCheck
        }

        // Validate parentheses balance
        val parenthesesCheck = validateParenthesesBalance(trimmedQuery)
        if (!parenthesesCheck.isValid) {
            return parenthesesCheck
        }

        // Check for reasonable complexity
        val complexityCheck = validateComplexity(trimmedQuery)
        if (!complexityCheck.isValid) {
            return complexityCheck
        }

        return ValidationResult(true, "Query is valid", generateSuggestions(trimmedQuery))
    }

    fun validateQuery(query: String): ValidationResult {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isEmpty()) {
            return ValidationResult(false, "Query cannot be empty")
        }

        // Check basic structure
        val basicValidation = validateBasicStructure(trimmedQuery)
        if (!basicValidation.isValid) {
            return basicValidation
        }

        // For non-SELECT queries, be more restrictive
        if (!isSelectQuery(trimmedQuery)) {
            return ValidationResult(false, "Only SELECT queries are currently supported for security reasons")
        }

        return validateSelectQuery(trimmedQuery)
    }

    fun isSelectQuery(query: String): Boolean {
        val normalizedQuery = query.trim().uppercase()
        return normalizedQuery.startsWith("SELECT") || normalizedQuery.startsWith("WITH")
    }

    private fun validateBasicStructure(query: String): ValidationResult {
        // Check for minimum length
        if (query.length < 6) {
            return ValidationResult(false, "Query is too short to be valid")
        }

        // Check for maximum length (prevent DoS)
        if (query.length > 10000) {
            return ValidationResult(false, "Query is too long (maximum 10,000 characters)")
        }

        // Check for basic SQL structure
        val normalizedQuery = query.uppercase()
        val hasValidStart = normalizedQuery.startsWith("SELECT") ||
                normalizedQuery.startsWith("WITH") ||
                normalizedQuery.startsWith("EXPLAIN")

        if (!hasValidStart) {
            return ValidationResult(false, "Query must start with SELECT, WITH, or EXPLAIN")
        }

        return ValidationResult(true, "Basic structure is valid")
    }

    private fun checkForDangerousKeywords(query: String): ValidationResult {
        val normalizedQuery = query.uppercase().trim()

        // Split by word boundaries to avoid matching partial words
        val words = normalizedQuery.split("\\W+".toRegex())

        for (word in words) {
            val cleanWord = word.trim()
            if (dangerousKeywords.contains(cleanWord)) {
                // Additional check: make sure it's not part of a table/column name
                if (isStandaloneKeyword(normalizedQuery, cleanWord)) {
                    return ValidationResult(false, "Query contains prohibited keyword: $cleanWord")
                }
            }
        }

        return ValidationResult(true, "No dangerous keywords found")
    }

    private fun isStandaloneKeyword(query: String, keyword: String): Boolean {
        // Check if the keyword appears as a standalone SQL keyword, not as part of a name
        val pattern = "\\b$keyword\\b(?!_)".toRegex(RegexOption.IGNORE_CASE)
        val matches = pattern.findAll(query)

        for (match in matches) {
            val position = match.range.first
            val beforeChar = if (position > 0) query[position - 1] else ' '
            val afterPosition = match.range.last + 1
            val afterChar = if (afterPosition < query.length) query[afterPosition] else ' '

            // Check if it's actually a SQL keyword (preceded/followed by whitespace or operators)
            if ((beforeChar.isWhitespace() || beforeChar in "(),;") &&
                (afterChar.isWhitespace() || afterChar in "(),;")) {
                return true
            }
        }
        return false
    }

    private fun checkForSqlInjection(query: String): ValidationResult {
        // Skip injection checks for simple SELECT queries without WHERE clauses
        val normalizedQuery = query.trim().uppercase()
        if (normalizedQuery.matches("SELECT\\s+.*\\s+FROM\\s+\\w+\\s*$".toRegex(RegexOption.IGNORE_CASE))) {
            return ValidationResult(true, "Simple SELECT query - no injection patterns detected")
        }

        for (pattern in sqlInjectionPatterns) {
            if (pattern.matcher(query).find()) {
                return ValidationResult(false, "Query contains potential SQL injection pattern")
            }
        }

        // Check for suspicious comment patterns (but allow -- in strings)
        if (query.contains("--") || query.contains("/*")) {
            // More sophisticated check - ignore comments in string literals
            if (containsSuspiciousComments(query)) {
                return ValidationResult(false, "SQL comments are not allowed for security reasons")
            }
        }

        return ValidationResult(true, "No SQL injection patterns detected")
    }

    private fun containsSuspiciousComments(query: String): Boolean {
        var inString = false
        var stringChar = '\u0000'
        var i = 0

        while (i < query.length - 1) {
            val char = query[i]
            val nextChar = query[i + 1]

            when {
                !inString && (char == '\'' || char == '"') -> {
                    inString = true
                    stringChar = char
                }
                inString && char == stringChar -> {
                    inString = false
                    stringChar = '\u0000'
                }
                !inString && char == '-' && nextChar == '-' -> {
                    return true // Found suspicious comment outside string
                }
                !inString && char == '/' && nextChar == '*' -> {
                    return true // Found suspicious comment outside string
                }
            }
            i++
        }

        return false
    }

    private fun validateParenthesesBalance(query: String): ValidationResult {
        var balance = 0
        var inString = false
        var stringChar = '\u0000'

        for (char in query) {
            when {
                !inString && (char == '\'' || char == '"') -> {
                    inString = true
                    stringChar = char
                }
                inString && char == stringChar -> {
                    inString = false
                    stringChar = '\u0000'
                }
                !inString && char == '(' -> balance++
                !inString && char == ')' -> balance--
            }

            if (balance < 0) {
                return ValidationResult(false, "Unmatched closing parenthesis")
            }
        }

        if (balance != 0) {
            return ValidationResult(false, "Unmatched opening parenthesis")
        }

        return ValidationResult(true, "Parentheses are balanced")
    }

    private fun validateComplexity(query: String): ValidationResult {
        val normalizedQuery = query.uppercase()

        // Count subqueries
        val subqueryCount = normalizedQuery.split("SELECT").size - 1
        if (subqueryCount > 5) {
            return ValidationResult(false, "Query is too complex (too many subqueries)")
        }

        // Count JOINs
        val joinCount = listOf("JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN")
            .sumOf { normalizedQuery.split(it).size - 1 }
        if (joinCount > 10) {
            return ValidationResult(false, "Query is too complex (too many JOINs)")
        }

        // Check for reasonable number of conditions
        val conditionCount = normalizedQuery.split(" AND ", " OR ").size - 1
        if (conditionCount > 20) {
            return ValidationResult(false, "Query is too complex (too many conditions)")
        }

        return ValidationResult(true, "Query complexity is acceptable")
    }

    private fun generateSuggestions(query: String): List<String> {
        val suggestions = mutableListOf<String>()
        val normalizedQuery = query.uppercase()

        // Suggest LIMIT if not present
        if (!normalizedQuery.contains("LIMIT")) {
            suggestions.add("Consider adding a LIMIT clause to control result set size")
        }

        // Suggest using indexes for WHERE clauses
        if (normalizedQuery.contains("WHERE") && !normalizedQuery.contains("INDEX")) {
            suggestions.add("Ensure WHERE clause columns are indexed for better performance")
        }

        // Suggest EXPLAIN for complex queries
        if (normalizedQuery.contains("JOIN") && !normalizedQuery.startsWith("EXPLAIN")) {
            suggestions.add("Consider using EXPLAIN to analyze query performance")
        }

        // Suggest avoiding SELECT *
        if (normalizedQuery.contains("SELECT *")) {
            suggestions.add("Consider selecting specific columns instead of using SELECT *")
        }

        return suggestions
    }

    fun sanitizeTableName(tableName: String): String {
        // Remove any potentially dangerous characters
        val sanitized = tableName.replace(Regex("[^a-zA-Z0-9_]"), "")

        if (sanitized.isEmpty()) {
            throw IllegalArgumentException("Invalid table name: $tableName")
        }

        return sanitized
    }

    fun sanitizeColumnName(columnName: String): String {
        // Remove any potentially dangerous characters
        val sanitized = columnName.replace(Regex("[^a-zA-Z0-9_]"), "")

        if (sanitized.isEmpty()) {
            throw IllegalArgumentException("Invalid column name: $columnName")
        }

        return sanitized
    }

    fun sanitizeSchemaName(schemaName: String): String {
        // Remove any potentially dangerous characters
        val sanitized = schemaName.replace(Regex("[^a-zA-Z0-9_]"), "")

        if (sanitized.isEmpty()) {
            throw IllegalArgumentException("Invalid schema name: $schemaName")
        }

        return sanitized
    }

    fun validateLimit(limit: Int): ValidationResult {
        return when {
            limit < 1 -> ValidationResult(false, "Limit must be at least 1")
            limit > 10000 -> ValidationResult(false, "Limit cannot exceed 10,000 for performance reasons")
            else -> ValidationResult(true, "Limit is valid")
        }
    }

    fun validateOffset(offset: Int): ValidationResult {
        return when {
            offset < 0 -> ValidationResult(false, "Offset cannot be negative")
            offset > 1000000 -> ValidationResult(false, "Offset cannot exceed 1,000,000 for performance reasons")
            else -> ValidationResult(true, "Offset is valid")
        }
    }
}
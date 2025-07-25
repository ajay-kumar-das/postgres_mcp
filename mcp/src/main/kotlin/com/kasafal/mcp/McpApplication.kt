package com.kasafal.mcp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class McpApplication

fun main(args: Array<String>) {
	runApplication<McpApplication>(*args)
}

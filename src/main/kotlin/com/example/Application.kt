package com.example

import com.example.models.ApiResponse
import com.example.models.VersionInfo
import com.example.templates.IndexPage
import com.example.templates.welcomeContent
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.html.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.sql.Connection

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize database
    initDatabase()

    // Install ContentNegotiation feature
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    // Configure routing
    configureRouting()
}

fun Application.initDatabase() {
    val config = environment.config.config("database")
    val driverClassName = config.property("driverClassName").getString()
    val jdbcURL = config.property("jdbcURL").getString()
    val username = config.property("username").getString()
    val password = config.property("password").getString()

    val hikariConfig = HikariConfig().apply {
        this.driverClassName = driverClassName
        this.jdbcUrl = jdbcURL
        this.username = username
        this.password = password
        this.maximumPoolSize = 10
        this.isAutoCommit = false
        this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        this.validate()
    }

    val dataSource = HikariDataSource(hikariConfig)
    Database.connect(dataSource)

    log.info("Database initialized with $jdbcURL")
}

fun Application.configureRouting() {
    routing {
        // HTML routes
        get("/") {
            call.respondHtmlTemplate(IndexPage()) {
                content {
                    welcomeContent()
                }
            }
        }

        // API routes
        route("/api") {
            get("/health") {
                call.respond(
                    ApiResponse(
                        status = "success",
                        message = "Service is healthy",
                        data = mapOf("status" to "UP")
                    )
                )
            }

            get("/version") {
                call.respond(VersionInfo())
            }

            get("/info") {
                call.respond(
                    ApiResponse(
                        status = "success",
                        message = "Application information",
                        data = mapOf(
                            "name" to "Ktor Web App",
                            "description" to "A simple Ktor web application with up-to-date dependencies"
                        )
                    )
                )
            }
        }
    }
}

package com.example

import com.example.models.ApiResponse
import com.example.models.InputDataEntity
import com.example.models.InputDataTable
import com.example.models.VersionInfo
import com.example.templates.IndexPage
import com.example.templates.importPageContent
import com.example.templates.welcomeContent
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import com.typesafe.config.ConfigFactory
import kotlinx.html.*
import kotlinx.serialization.json.Json
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.charset.StandardCharsets
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
    val config = HoconApplicationConfig(ConfigFactory.load()).config("database")
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

    // Create tables if they don't exist
    transaction {
        SchemaUtils.create(InputDataTable)
    }

    log.info("Database initialized with $jdbcURL")
}

fun Application.configureRouting() {
    routing {
        // HTML routes
        get("/") {
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "home"
                content {
                    welcomeContent()
                }
            }
        }

        // Import page routes
        get("/import") {
            val dataCount = transaction {
                InputDataEntity.all().count()
            }
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "import"
                content {
                    importPageContent(dataCount)
                }
            }
        }

        post("/import") {
            try {
                val importResult = importDataFromCsv()
                val dataCount = transaction {
                    InputDataEntity.all().count()
                }
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "import"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Import Successful!" }
                            p { +"Successfully imported ${importResult.count} records." }
                            hr {}
                            p("mb-0") { +"The data has been stored in the database." }
                        }
                        importPageContent(dataCount)
                    }
                }
            } catch (e: Exception) {
                val dataCount = transaction {
                    InputDataEntity.all().count()
                }
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "import"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Import Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        importPageContent(dataCount)
                    }
                }
                application.log.error("Import failed", e)
            }
        }

        post("/purge") {
            try {
                val purgeCount = purgeAllData()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "import"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} records from the database." }
                        }
                        importPageContent(0) // After purge, count is 0
                    }
                }
            } catch (e: Exception) {
                val dataCount = transaction {
                    InputDataEntity.all().count()
                }
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "import"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        importPageContent(dataCount)
                    }
                }
                application.log.error("Purge failed", e)
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

            // API endpoint to get imported data count
            get("/inputdata") {
                val count = transaction {
                    InputDataEntity.all().count()
                }
                call.respond(
                    ApiResponse(
                        status = "success",
                        message = "Input data retrieved",
                        data = mapOf("count" to count.toString())
                    )
                )
            }
        }
    }
}

data class ImportResult(val count: Int)

fun purgeAllData(): Long {
    var purgeCount: Long = 0
    transaction {
        purgeCount = InputDataEntity.all().count()
        InputDataEntity.all().forEach { it.delete() }
    }
    return purgeCount
}

fun importDataFromCsv(): ImportResult {
    val file = File("input/flat/id_geojson.csv")
    if (!file.exists()) {
        throw IllegalStateException("CSV file not found at ${file.absolutePath}")
    }

    var importCount = 0

    transaction {
        CSVParser.parse(file, StandardCharsets.UTF_8, CSVFormat.DEFAULT).use { csvParser ->
            for (record in csvParser) {
                if (record.size() >= 2) {
                    val hikeId = record.get(0)
                    val geoJson = record.get(1)

                    // Check if record already exists
                    val existingRecord = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()

                    if (existingRecord == null) {
                        // Create new record
                        InputDataEntity.new {
                            this.hikeId = hikeId
                            this.geoJson = geoJson
                        }
                        importCount++
                    }
                }
            }
        }
    }

    return ImportResult(importCount)
}

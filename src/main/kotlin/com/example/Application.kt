package com.example

import com.example.models.*
import com.example.services.ApiService
import com.example.services.ImportService
import com.example.services.POIService
import com.example.templates.IndexPage
import com.example.templates.importPageContent
import com.example.templates.poisPageContent
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
import kotlinx.serialization.json.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.net.URL
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
        SchemaUtils.create(InputDataTable, POITable)
    }

    log.info("Database initialized with $jdbcURL")
}

fun Application.configureRouting() {
    // Initialize services
    val importService = ImportService()
    val poiService = POIService()
    val apiService = ApiService()

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
            val dataCount = importService.getInputDataCount()
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "import"
                content {
                    importPageContent(dataCount)
                }
            }
        }

        post("/import") {
            try {
                val importResult = importService.importDataFromCsv()
                val dataCount = importService.getInputDataCount()
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
                val dataCount = importService.getInputDataCount()
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
                val purgeCount = importService.purgeAllData()
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
                val dataCount = importService.getInputDataCount()
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

        // POIs page routes
        get("/pois") {
            val poiCounts = poiService.getPOICountsByTrack()
            val totalPois = poiService.getTotalPOICount()
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "pois"
                content {
                    poisPageContent(poiCounts, totalPois)
                }
            }
        }

        post("/pois/search") {
            try {
                val searchResult = poiService.searchAndStorePOIs()
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Search Successful!" }
                            p { +"Found and stored ${searchResult.totalPois} POIs across ${searchResult.tracksProcessed} tracks." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Search Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
                application.log.error("POI search failed", e)
            }
        }

        post("/pois/purge") {
            try {
                val purgeCount = poiService.purgePOIData()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} POI records from the database." }
                        }
                        poisPageContent(emptyList(), 0) // After purge, count is 0
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
                application.log.error("POI purge failed", e)
            }
        }

        // Purge POIs for a specific track
        post("/pois/purge/{hikeId}") {
            try {
                val hikeId = call.parameters["hikeId"] ?: throw IllegalArgumentException("Missing hikeId parameter")
                val purgeCount = poiService.purgePOIDataForTrack(hikeId)
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} POI records for track ${hikeId}." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
                application.log.error("POI purge for track failed", e)
            }
        }

        // Search POIs for a specific track
        post("/pois/search/{hikeId}") {
            try {
                val hikeId = call.parameters["hikeId"] ?: throw IllegalArgumentException("Missing hikeId parameter")
                val searchResult = poiService.searchAndStorePOIsForTrack(hikeId)
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Search Successful!" }
                            p { +"Found and stored ${searchResult.totalPois} POIs for track ${hikeId}." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Search Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
                application.log.error("POI search for track failed", e)
            }
        }

        // Search POIs for all tracks missing them
        post("/pois/search-missing") {
            try {
                val searchResult = poiService.searchAndStorePOIsForMissingTracks()
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Search Successful!" }
                            p { +"Found and stored ${searchResult.totalPois} POIs across ${searchResult.tracksProcessed} tracks that were missing POIs." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Search Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois)
                    }
                }
                application.log.error("POI search for missing tracks failed", e)
            }
        }

        // API routes
        route("/api") {
            get("/health") {
                call.respond(apiService.getHealthStatus())
            }

            get("/version") {
                call.respond(apiService.getVersionInfo())
            }

            get("/info") {
                call.respond(apiService.getApplicationInfo())
            }

            // API endpoint to get imported data count
            get("/inputdata") {
                call.respond(apiService.getInputDataCount())
            }

            // API endpoint to get POI search progress
            get("/poi/progress") {
                call.respond(poiService.getSearchProgress())
            }
        }
    }
}

package com.example

import com.example.models.*
import com.example.models.FilteredPOITable
import com.example.models.RouteTable
import com.example.services.ApiService
import com.example.services.ImportService
import com.example.services.POIService
import com.example.services.FilterPOIService
import com.example.services.RouteService
import com.example.services.ExportService
import com.example.templates.IndexPage
import com.example.templates.importPageContent
import com.example.templates.poisPageContent
import com.example.templates.viewPoisPageContent
import com.example.templates.filterPoisPageContent
import com.example.templates.viewFilteredPoisPageContent
import com.example.templates.routePageContent
import com.example.templates.viewRoutePageContent
import com.example.templates.welcomeContent
import com.example.templates.exportPageContent
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
        SchemaUtils.create(InputDataTable, POITable, FilteredPOITable, RouteTable)
    }

    log.info("Database initialized with $jdbcURL")
}

fun Application.configureRouting() {
    // Initialize services
    val importService = ImportService()
    val poiService = POIService()
    val filterPOIService = FilterPOIService()
    val routeService = RouteService()
    val exportService = ExportService()
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
            val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "pois"
                content {
                    poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                }
            }
        }

        // View POIs page routes
        get("/view-pois") {
            val hikeIds = poiService.getAllHikeIds()
            val selectedHikeId = call.request.queryParameters["hikeId"]

            // If a hike is selected, get its POIs and track data
            val pois = if (selectedHikeId != null) poiService.getPOIsForTrack(selectedHikeId) else emptyList()
            val trackData = if (selectedHikeId != null) poiService.getTrackData(selectedHikeId) else null

            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "view-pois"
                content {
                    viewPoisPageContent(hikeIds, selectedHikeId, pois, trackData)
                }
            }
        }

        post("/pois/search") {
            try {
                // Extract selected map features and sublevels from form data
                val parameters = call.receiveParameters()
                val mapFeatures = parameters.getAll("mapFeatures") ?: listOf("natural", "geological", "historic", "tourism")
                val mapFeaturesSublevels = parameters.getAll("mapFeatures-sublevel") ?: emptyList()

                val searchResult = poiService.searchAndStorePOIs(mapFeatures, mapFeaturesSublevels)
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Search Successful!" }
                            p { +"Found and stored ${searchResult.totalPois} POIs across ${searchResult.tracksProcessed} tracks." }
                            p { +"Map features used: ${mapFeatures.joinToString(", ")}" }
                            if (mapFeaturesSublevels.isNotEmpty()) {
                                p { +"Map feature sublevels used: ${mapFeaturesSublevels.joinToString(", ")}" }
                            }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Search Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
                application.log.error("POI search failed", e)
            }
        }

        post("/pois/purge") {
            try {
                val purgeCount = poiService.purgePOIData()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} POI records from the database." }
                        }
                        poisPageContent(emptyList(), 0, hikesWithoutPOIs) // After purge, count is 0
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
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
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} POI records for track ${hikeId}." }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
                application.log.error("POI purge for track failed", e)
            }
        }

        // Search POIs for a specific track
        post("/pois/search/{hikeId}") {
            try {
                val hikeId = call.parameters["hikeId"] ?: throw IllegalArgumentException("Missing hikeId parameter")

                // Extract selected map features and sublevels from form data if available
                val parameters = call.receiveParameters()
                val mapFeatures = parameters.getAll("mapFeatures") ?: listOf("natural", "geological", "historic", "tourism")
                val mapFeaturesSublevels = parameters.getAll("mapFeatures-sublevel") ?: emptyList()

                val searchResult = poiService.searchAndStorePOIsForTrack(hikeId, mapFeatures, mapFeaturesSublevels)
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Search Successful!" }
                            p { +"Found and stored ${searchResult.totalPois} POIs for track ${hikeId}." }
                            p { +"Map features used: ${mapFeatures.joinToString(", ")}" }
                            if (mapFeaturesSublevels.isNotEmpty()) {
                                p { +"Map feature sublevels used: ${mapFeaturesSublevels.joinToString(", ")}" }
                            }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Search Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
                application.log.error("POI search for track failed", e)
            }
        }

        // Search POIs for all tracks missing them
        post("/pois/search-missing") {
            try {
                // Extract selected map features and sublevels from form data if available
                val parameters = call.receiveParameters()
                val mapFeatures = parameters.getAll("mapFeatures") ?: listOf("natural", "geological", "historic", "tourism")
                val mapFeaturesSublevels = parameters.getAll("mapFeatures-sublevel") ?: emptyList()

                val searchResult = poiService.searchAndStorePOIsForMissingTracks(mapFeatures, mapFeaturesSublevels)
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Search Successful!" }
                            p { +"Found and stored ${searchResult.totalPois} POIs across ${searchResult.tracksProcessed} tracks that were missing POIs." }
                            p { +"Map features used: ${mapFeatures.joinToString(", ")}" }
                            if (mapFeaturesSublevels.isNotEmpty()) {
                                p { +"Map feature sublevels used: ${mapFeaturesSublevels.joinToString(", ")}" }
                            }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
            } catch (e: Exception) {
                val poiCounts = poiService.getPOICountsByTrack()
                val totalPois = poiService.getTotalPOICount()
                val hikesWithoutPOIs = poiService.getHikesWithoutPOIsCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Search Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        poisPageContent(poiCounts, totalPois, hikesWithoutPOIs)
                    }
                }
                application.log.error("POI search for missing tracks failed", e)
            }
        }

        // Filter POIs page routes
        get("/filter-pois") {
            val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
            val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
            val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()
            val hikesWithoutPOIs = poiService.getTracksWithoutPOIs()

            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "filter-pois"
                content {
                    filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, hikesWithoutPOIs = hikesWithoutPOIs)
                }
            }
        }

        // Filter POIs for all tracks
        post("/filter-pois/filter") {
            try {
                val maxPOIs = call.receiveParameters()["maxPOIs"]?.toIntOrNull() ?: 10
                val filterResult = filterPOIService.filterPOIsForAllTracks(maxPOIs)
                val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
                val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
                val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Filtering Successful!" }
                            p { +"Filtered ${filterResult.filteredPois} POIs and created ${filterResult.artificialPois} artificial POIs across ${filterResult.tracksProcessed} tracks." }
                        }
                        filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, maxPOIs, poiService.getTracksWithoutPOIs())
                    }
                }
            } catch (e: Exception) {
                val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
                val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
                val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Filtering Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, hikesWithoutPOIs = poiService.getTracksWithoutPOIs())
                    }
                }
                application.log.error("POI filtering failed", e)
            }
        }

        // Filter POIs for a specific track
        post("/filter-pois/filter/{hikeId}") {
            try {
                val hikeId = call.parameters["hikeId"] ?: throw IllegalArgumentException("Missing hikeId parameter")
                val maxPOIs = call.receiveParameters()["maxPOIs"]?.toIntOrNull() ?: 10
                val filterResult = filterPOIService.filterPOIsForTrack(hikeId, maxPOIs)
                val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
                val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
                val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"POI Filtering Successful!" }
                            p { +"Filtered ${filterResult.filteredPois} POIs and created ${filterResult.artificialPois} artificial POIs for track ${hikeId}." }
                        }
                        filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, maxPOIs, poiService.getTracksWithoutPOIs())
                    }
                }
            } catch (e: Exception) {
                val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
                val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
                val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"POI Filtering Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, hikesWithoutPOIs = poiService.getTracksWithoutPOIs())
                    }
                }
                application.log.error("POI filtering for track failed", e)
            }
        }

        // Purge all filtered POI data
        get("/filter-pois/purge") {
            try {
                val purgeCount = filterPOIService.purgeFilteredPOIData()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Filtered POI Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} filtered POI records from the database." }
                        }
                        filterPoisPageContent(emptyList(), 0, 0, hikesWithoutPOIs = poiService.getTracksWithoutPOIs()) // After purge, counts are 0
                    }
                }
            } catch (e: Exception) {
                val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
                val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
                val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Filtered POI Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, hikesWithoutPOIs = poiService.getTracksWithoutPOIs())
                    }
                }
                application.log.error("Filtered POI purge failed", e)
            }
        }

        // Purge filtered POIs for a specific track
        post("/filter-pois/purge/{hikeId}") {
            try {
                val hikeId = call.parameters["hikeId"] ?: throw IllegalArgumentException("Missing hikeId parameter")
                val purgeCount = filterPOIService.purgeFilteredPOIDataForTrack(hikeId)
                val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
                val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
                val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Filtered POI Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} filtered POI records for track ${hikeId}." }
                        }
                        filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, hikesWithoutPOIs = poiService.getTracksWithoutPOIs())
                    }
                }
            } catch (e: Exception) {
                val filteredPoiCounts = filterPOIService.getFilteredPOICountsByTrack()
                val totalFilteredPois = filterPOIService.getTotalFilteredPOICount()
                val totalArtificialPois = filterPOIService.getTotalArtificialPOICount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "filter-pois"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Filtered POI Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        filterPoisPageContent(filteredPoiCounts, totalFilteredPois, totalArtificialPois, hikesWithoutPOIs = poiService.getTracksWithoutPOIs())
                    }
                }
                application.log.error("Filtered POI purge for track failed", e)
            }
        }

        // View filtered POIs page routes
        get("/filter-pois/view") {
            val hikeIds = poiService.getAllHikeIds()
            val selectedHikeId = call.request.queryParameters["hikeId"]

            // If a hike is selected, get its filtered POIs and track data
            val pois = if (selectedHikeId != null) filterPOIService.getFilteredPOIsForTrack(selectedHikeId) else emptyList()
            val trackData = if (selectedHikeId != null) poiService.getTrackData(selectedHikeId) else null

            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "filter-pois"
                content {
                    viewFilteredPoisPageContent(hikeIds, selectedHikeId, pois, trackData)
                }
            }
        }

        // Routes page routes
        get("/routes") {
            val routeCounts = routeService.getRouteCountsByTrack()
            val totalRoutes = routeService.getTotalRouteCount()
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "routes"
                content {
                    routePageContent(routeCounts, totalRoutes)
                }
            }
        }

        // Generate routes for all tracks
        post("/routes/generate") {
            try {
                val generateResult = routeService.generateRoutesForAllTracks()
                val routeCounts = routeService.getRouteCountsByTrack()
                val totalRoutes = routeService.getTotalRouteCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Route Generation Successful!" }
                            p { +"Generated ${generateResult.routesGenerated} routes across ${generateResult.tracksProcessed} tracks." }
                        }
                        routePageContent(routeCounts, totalRoutes)
                    }
                }
            } catch (e: Exception) {
                val routeCounts = routeService.getRouteCountsByTrack()
                val totalRoutes = routeService.getTotalRouteCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Route Generation Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        routePageContent(routeCounts, totalRoutes)
                    }
                }
                application.log.error("Route generation failed", e)
            }
        }

        // Generate route for a specific track
        post("/routes/generate/{hikeId}") {
            try {
                val hikeId = call.parameters["hikeId"] ?: throw IllegalArgumentException("Missing hikeId parameter")
                val generateResult = routeService.generateRouteForTrack(hikeId)
                val routeCounts = routeService.getRouteCountsByTrack()
                val totalRoutes = routeService.getTotalRouteCount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Route Generation Successful!" }
                            p { +"Generated route for track ${hikeId}." }
                        }
                        routePageContent(routeCounts, totalRoutes)
                    }
                }
            } catch (e: Exception) {
                val routeCounts = routeService.getRouteCountsByTrack()
                val totalRoutes = routeService.getTotalRouteCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Route Generation Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        routePageContent(routeCounts, totalRoutes)
                    }
                }
                application.log.error("Route generation for track failed", e)
            }
        }

        // Purge all route data
        post("/routes/purge") {
            try {
                val purgeCount = routeService.purgeRouteData()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Route Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} route records from the database." }
                        }
                        routePageContent(emptyList(), 0) // After purge, count is 0
                    }
                }
            } catch (e: Exception) {
                val routeCounts = routeService.getRouteCountsByTrack()
                val totalRoutes = routeService.getTotalRouteCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Route Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        routePageContent(routeCounts, totalRoutes)
                    }
                }
                application.log.error("Route purge failed", e)
            }
        }

        // Purge route data for a specific track
        post("/routes/purge/{hikeId}") {
            try {
                val hikeId = call.parameters["hikeId"] ?: throw IllegalArgumentException("Missing hikeId parameter")
                val purgeCount = routeService.purgeRouteDataForTrack(hikeId)
                val routeCounts = routeService.getRouteCountsByTrack()
                val totalRoutes = routeService.getTotalRouteCount()

                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Route Data Purged Successfully!" }
                            p { +"Successfully purged ${purgeCount} route records for track ${hikeId}." }
                        }
                        routePageContent(routeCounts, totalRoutes)
                    }
                }
            } catch (e: Exception) {
                val routeCounts = routeService.getRouteCountsByTrack()
                val totalRoutes = routeService.getTotalRouteCount()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "routes"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Route Purge Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        routePageContent(routeCounts, totalRoutes)
                    }
                }
                application.log.error("Route purge for track failed", e)
            }
        }

        // View route page routes
        get("/routes/view") {
            val hikeIds = poiService.getAllHikeIds()
            val selectedHikeId = call.request.queryParameters["hikeId"]

            // If a hike is selected, get its route and track data
            val route = if (selectedHikeId != null) routeService.getRouteForTrack(selectedHikeId) else null
            val trackData = if (selectedHikeId != null) poiService.getTrackData(selectedHikeId) else null

            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "routes"
                content {
                    viewRoutePageContent(hikeIds, selectedHikeId, route, trackData)
                }
            }
        }

        // Export page routes
        get("/export") {
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "export"
                content {
                    exportPageContent()
                }
            }
        }

        // Export to GeoJSON
        post("/export/geojson") {
            try {
                val exportCount = exportService.exportToGeoJSON()
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "export"
                    content {
                        div("alert alert-success") {
                            h4("alert-heading") { +"Export Successful!" }
                            p { +"Successfully exported ${exportCount} hikes to GeoJSON files in the output folder." }
                        }
                        exportPageContent()
                    }
                }
            } catch (e: Exception) {
                call.respondHtmlTemplate(IndexPage()) {
                    activeTab = "export"
                    content {
                        div("alert alert-danger") {
                            h4("alert-heading") { +"Export Failed!" }
                            p { +"Error: ${e.message}" }
                            hr {}
                            p("mb-0") { +"Please check the logs for more details." }
                        }
                        exportPageContent()
                    }
                }
                application.log.error("Export to GeoJSON failed", e)
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

            // API endpoint to get POI filter progress
            get("/filter/progress") {
                call.respond(filterPOIService.getFilterProgress())
            }

            // API endpoint to get route generation progress
            get("/route/progress") {
                call.respond(routeService.getRoutingProgress())
            }

            // API endpoint to get export progress
            get("/export/progress") {
                call.respond(exportService.getExportProgress())
            }
        }
    }
}

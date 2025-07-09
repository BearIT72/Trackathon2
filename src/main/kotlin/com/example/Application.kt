package com.example

import com.example.models.*
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

        // POIs page routes
        get("/pois") {
            val poiCounts = getPOICountsByTrack()
            val totalPois = transaction {
                POIEntity.all().count()
            }
            call.respondHtmlTemplate(IndexPage()) {
                activeTab = "pois"
                content {
                    poisPageContent(poiCounts, totalPois)
                }
            }
        }

        post("/pois/search") {
            try {
                val searchResult = searchAndStorePOIs()
                val poiCounts = getPOICountsByTrack()
                val totalPois = transaction {
                    POIEntity.all().count()
                }
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
                val poiCounts = getPOICountsByTrack()
                val totalPois = transaction {
                    POIEntity.all().count()
                }
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
                val purgeCount = purgePOIData()
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
                val poiCounts = getPOICountsByTrack()
                val totalPois = transaction {
                    POIEntity.all().count()
                }
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

data class POISearchResult(val totalPois: Int, val tracksProcessed: Int)

fun purgeAllData(): Long {
    var purgeCount: Long = 0
    transaction {
        purgeCount = InputDataEntity.all().count()
        InputDataEntity.all().forEach { it.delete() }
    }
    return purgeCount
}

fun purgePOIData(): Long {
    var purgeCount: Long = 0
    transaction {
        purgeCount = POIEntity.all().count()
        POIEntity.all().forEach { it.delete() }
    }
    return purgeCount
}

fun getPOICountsByTrack(): List<POICountDTO> {
    return transaction {
        (InputDataTable innerJoin POITable)
            .slice(InputDataTable.hikeId, POITable.id.count())
            .selectAll()
            .groupBy(InputDataTable.hikeId)
            .map { row ->
                POICountDTO(
                    hikeId = row[InputDataTable.hikeId],
                    count = row[POITable.id.count()]
                )
            }
    }
}

// Parse GeoJSON to extract bounding box
fun extractBoundingBox(geoJson: String): BoundingBox? {
    try {
        val jsonElement = Json.parseToJsonElement(geoJson)
        val coordinates = mutableListOf<Pair<Double, Double>>()

        // Extract all coordinates from the GeoJSON
        fun extractCoordinates(element: JsonElement) {
            when (element) {
                is JsonArray -> {
                    if (element.size == 2 && element[0] is JsonPrimitive && element[1] is JsonPrimitive) {
                        // This looks like a coordinate pair [lon, lat]
                        val lon = (element[0] as JsonPrimitive).double
                        val lat = (element[1] as JsonPrimitive).double
                        coordinates.add(Pair(lon, lat))
                    } else {
                        // Recursively process array elements
                        element.forEach { extractCoordinates(it) }
                    }
                }
                is JsonObject -> {
                    // Process object properties
                    element.forEach { (_, value) -> extractCoordinates(value) }
                }
                else -> {} // Ignore primitives
            }
        }

        extractCoordinates(jsonElement)

        if (coordinates.isEmpty()) {
            return null
        }

        // Calculate bounding box
        var minLon = Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE
        var maxLat = Double.MIN_VALUE

        coordinates.forEach { (lon, lat) ->
            minLon = minOf(minLon, lon)
            minLat = minOf(minLat, lat)
            maxLon = maxOf(maxLon, lon)
            maxLat = maxOf(maxLat, lat)
        }

        return BoundingBox(minLat, minLon, maxLat, maxLon)
    } catch (e: Exception) {
        return null
    }
}

data class BoundingBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)

fun searchAndStorePOIs(): POISearchResult {
    var totalPois = 0
    var tracksProcessed = 0

    transaction {
        // Get all tracks
        val tracks = InputDataEntity.all().toList()
        tracksProcessed = tracks.size

        tracks.forEach { track ->
            // Extract bounding box from GeoJSON
            val bbox = extractBoundingBox(track.geoJson)
            if (bbox != null) {
                // Build Overpass query
                val bboxString = """${bbox.minLat},${bbox.minLon},${bbox.maxLat},${bbox.maxLon}"""
                val query = """
                    [out:json];
                    (
                      node[natural][natural != tree][natural != shrub]($bboxString);
                      node[geological]($bboxString);
                      node[historic]($bboxString);
                      node[tourism = artwork]($bboxString);
                      node[tourism = viewpoint]($bboxString);
                    );
                    out body;
                """.trimIndent()

                // Call Overpass API
                val url = URL("https://overpass-api.de/api/interpreter")
                val connection = url.openConnection()
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                connection.outputStream.use { os ->
                    os.write("data=${query}".toByteArray())
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = Json.parseToJsonElement(response).jsonObject

                if (jsonResponse.containsKey("elements")) {
                    val elements = jsonResponse["elements"]?.jsonArray ?: return@forEach

                    elements.forEach { element ->
                        val obj = element.jsonObject
                        if (obj["type"]?.jsonPrimitive?.content == "node") {
                            val id = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                            val lat = obj["lat"]?.jsonPrimitive?.double ?: return@forEach
                            val lon = obj["lon"]?.jsonPrimitive?.double ?: return@forEach
                            val tags = obj["tags"]?.jsonObject

                            // Determine POI type and name
                            var poiType = "unknown"
                            var poiName: String? = null

                            tags?.let {
                                if (it.containsKey("amenity")) {
                                    poiType = "amenity:${it["amenity"]?.jsonPrimitive?.content}"
                                } else if (it.containsKey("tourism")) {
                                    poiType = "tourism:${it["tourism"]?.jsonPrimitive?.content}"
                                } else if (it.containsKey("shop")) {
                                    poiType = "shop:${it["shop"]?.jsonPrimitive?.content}"
                                } else if (it.containsKey("leisure")) {
                                    poiType = "leisure:${it["leisure"]?.jsonPrimitive?.content}"
                                } else if (it.containsKey("natural")) {
                                    poiType = "natural:${it["natural"]?.jsonPrimitive?.content}"
                                } else if (it.containsKey("historic")) {
                                    poiType = "historic:${it["historic"]?.jsonPrimitive?.content}"
                                }

                                poiName = it["name"]?.jsonPrimitive?.content
                            }

                            // Store POI in database
                            POIEntity.new {
                                this.inputData = track
                                this.type = poiType
                                this.name = poiName
                                this.latitude = lat
                                this.longitude = lon
                                this.osmId = id
                                this.properties = tags?.toString() ?: "{}"
                            }

                            totalPois++
                        }
                    }
                }
            }
        }
    }

    return POISearchResult(totalPois, tracksProcessed)
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

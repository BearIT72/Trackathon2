package com.example.services

import com.example.models.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service class for handling POI-related operations
 */
class POIService {
    // Progress tracking
    private val currentProgress = AtomicInteger(0)
    private val totalTracks = AtomicInteger(0)
    private var searchInProgress = false

    /**
     * Get the current progress of POI search
     * @return ProgressStatus object with current progress and total
     */
    fun getSearchProgress(): ProgressStatus {
        return ProgressStatus(
            current = currentProgress.get(),
            total = totalTracks.get(),
            inProgress = searchInProgress
        )
    }

    /**
     * Reset the progress tracking
     */
    private fun resetProgress() {
        currentProgress.set(0)
        totalTracks.set(0)
        searchInProgress = false
    }
    /**
     * Get POI counts by track
     * @return List of POICountDTO objects
     */
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

    /**
     * Search for POIs and store them in the database
     * @return POISearchResult with the count of POIs found and tracks processed
     */
    fun searchAndStorePOIs(): POISearchResult {
        var totalPois = 0
        var tracksProcessed = 0

        // Reset progress tracking
        resetProgress()
        searchInProgress = true

        transaction {
            // Get all tracks
            val tracks = InputDataEntity.all().toList()
            tracksProcessed = tracks.size

            // Set total tracks for progress tracking
            totalTracks.set(tracks.size)

            tracks.forEach { track ->
                // Update progress
                currentProgress.incrementAndGet()
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
                                    if (it.containsKey("natural")) {
                                        poiType = "natural:${it["natural"]?.jsonPrimitive?.content}"
                                    } else if (it.containsKey("tourism")) {
                                        poiType = "tourism:${it["tourism"]?.jsonPrimitive?.content}"
                                    } else if (it.containsKey("geological")) {
                                        poiType = "geological:${it["geological"]?.jsonPrimitive?.content}"
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

        // Mark search as completed
        searchInProgress = false
        return POISearchResult(totalPois, tracksProcessed)
    }

    /**
     * Purge all POI data from the database
     * @return The number of records purged
     */
    fun purgePOIData(): Long {
        var purgeCount: Long = 0
        transaction {
            purgeCount = POIEntity.all().count()
            POIEntity.all().forEach { it.delete() }
        }
        return purgeCount
    }

    /**
     * Purge POI data for a specific track
     * @param hikeId The ID of the hike/track
     * @return The number of records purged
     */
    fun purgePOIDataForTrack(hikeId: String): Long {
        var purgeCount: Long = 0
        transaction {
            // Find the input data entity for the given hikeId
            val inputData = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()

            if (inputData != null) {
                // Find and delete all POIs associated with this input data
                val pois = POIEntity.find { POITable.inputDataId eq inputData.id }
                purgeCount = pois.count()
                pois.forEach { it.delete() }
            }
        }
        return purgeCount
    }

    /**
     * Get the total count of POIs
     * @return The number of POIs in the database
     */
    fun getTotalPOICount(): Long {
        return transaction {
            POIEntity.all().count()
        }
    }

    /**
     * Search for POIs for a specific track and store them in the database
     * @param hikeId The ID of the hike/track
     * @return POISearchResult with the count of POIs found and tracks processed
     */
    fun searchAndStorePOIsForTrack(hikeId: String): POISearchResult {
        var totalPois = 0
        var tracksProcessed = 0

        // Reset progress tracking
        resetProgress()
        searchInProgress = true

        transaction {
            // Find the input data entity for the given hikeId
            val track = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()

            if (track != null) {
                tracksProcessed = 1
                totalTracks.set(1)
                currentProgress.incrementAndGet()

                // First, purge existing POIs for this track
                val pois = POIEntity.find { POITable.inputDataId eq track.id }
                pois.forEach { it.delete() }

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
                        val elements = jsonResponse["elements"]?.jsonArray ?: return@transaction

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
                                    if (it.containsKey("natural")) {
                                        poiType = "natural:${it["natural"]?.jsonPrimitive?.content}"
                                    } else if (it.containsKey("tourism")) {
                                        poiType = "tourism:${it["tourism"]?.jsonPrimitive?.content}"
                                    } else if (it.containsKey("geological")) {
                                        poiType = "geological:${it["geological"]?.jsonPrimitive?.content}"
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

        // Mark search as completed
        searchInProgress = false
        return POISearchResult(totalPois, tracksProcessed)
    }

    /**
     * Get a list of tracks that don't have any POIs
     * @return List of hikeIds for tracks without POIs
     */
    fun getTracksWithoutPOIs(): List<String> {
        return transaction {
            // Get all tracks
            val allTracks = InputDataEntity.all().map { it.hikeId }

            // Get tracks that have POIs
            val tracksWithPois = (InputDataTable innerJoin POITable)
                .slice(InputDataTable.hikeId)
                .selectAll()
                .groupBy(InputDataTable.hikeId)
                .map { it[InputDataTable.hikeId] }
                .toSet()

            // Return tracks that don't have POIs
            allTracks.filter { it !in tracksWithPois }
        }
    }

    /**
     * Search for POIs for all tracks that don't have any POIs
     * @return POISearchResult with the count of POIs found and tracks processed
     */
    fun searchAndStorePOIsForMissingTracks(): POISearchResult {
        var totalPois = 0
        var tracksProcessed = 0

        // Get tracks without POIs
        val tracksWithoutPOIs = getTracksWithoutPOIs()

        // If there are no tracks without POIs, return early
        if (tracksWithoutPOIs.isEmpty()) {
            return POISearchResult(0, 0)
        }

        // Reset progress tracking
        resetProgress()
        searchInProgress = true
        totalTracks.set(tracksWithoutPOIs.size)

        // Process each track
        tracksWithoutPOIs.forEach { hikeId ->
            val result = searchAndStorePOIsForTrack(hikeId)
            totalPois += result.totalPois
            tracksProcessed += result.tracksProcessed
        }

        // Mark search as completed
        searchInProgress = false
        return POISearchResult(totalPois, tracksProcessed)
    }

    /**
     * Extract bounding box from GeoJSON
     * @param geoJson The GeoJSON string
     * @return BoundingBox object or null if extraction fails
     */
    private fun extractBoundingBox(geoJson: String): BoundingBox? {
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

    /**
     * Get all POIs for a specific track
     * @param hikeId The ID of the hike/track
     * @return List of POIDTO objects
     */
    fun getPOIsForTrack(hikeId: String): List<POIDTO> {
        return transaction {
            // Find the input data entity for the given hikeId
            val inputData = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()
                ?: return@transaction emptyList()

            // Find all POIs associated with this input data
            POIEntity.find { POITable.inputDataId eq inputData.id }
                .map { it.toDTO() }
        }
    }

    /**
     * Get track data (GeoJSON) for a specific track
     * @param hikeId The ID of the hike/track
     * @return The GeoJSON string or null if not found
     */
    fun getTrackData(hikeId: String): String? {
        return transaction {
            InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()?.geoJson
        }
    }

    /**
     * Get all hike IDs
     * @return List of hike IDs
     */
    fun getAllHikeIds(): List<String> {
        return transaction {
            InputDataEntity.all().map { it.hikeId }
        }
    }
}

/**
 * Data class for POI search result
 */
data class POISearchResult(val totalPois: Int, val tracksProcessed: Int)

/**
 * Data class for bounding box
 */
data class BoundingBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)

/**
 * Data class for progress status
 */
@Serializable
data class ProgressStatus(val current: Int, val total: Int, val inProgress: Boolean)

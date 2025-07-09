package com.example.services

import com.example.models.*
import com.example.utils.PolylineUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service class for handling Route operations
 */
class RouteService {
    // Progress tracking
    private val currentProgress = AtomicInteger(0)
    private val totalTracks = AtomicInteger(0)
    private var routingInProgress = false

    /**
     * Get the current progress of route generation
     * @return ProgressStatus object with current progress and total
     */
    fun getRoutingProgress(): ProgressStatus {
        return ProgressStatus(
            current = currentProgress.get(),
            total = totalTracks.get(),
            inProgress = routingInProgress
        )
    }

    /**
     * Reset the progress tracking
     */
    private fun resetProgress() {
        currentProgress.set(0)
        totalTracks.set(0)
        routingInProgress = false
    }

    /**
     * Get route counts by track
     * @return List of RouteCountDTO objects
     */
    fun getRouteCountsByTrack(): List<RouteCountDTO> {
        return transaction {
            (InputDataTable innerJoin RouteTable)
                .slice(InputDataTable.hikeId, RouteTable.id.count())
                .selectAll()
                .groupBy(InputDataTable.hikeId)
                .map { row ->
                    RouteCountDTO(
                        hikeId = row[InputDataTable.hikeId],
                        count = row[RouteTable.id.count()]
                    )
                }
        }
    }

    /**
     * Get the total count of routes
     * @return The number of routes in the database
     */
    fun getTotalRouteCount(): Long {
        return transaction {
            RouteEntity.all().count()
        }
    }

    /**
     * Generate routes for all tracks with filtered POIs
     * @return RouteResult with the count of routes generated and tracks processed
     */
    fun generateRoutesForAllTracks(): RouteResult {
        var totalRoutes = 0
        var tracksProcessed = 0

        // Reset progress tracking
        resetProgress()
        routingInProgress = true

        transaction {
            // Get all tracks with filtered POIs
            val tracksWithFilteredPOIs = FilteredPOIEntity.all()
                .map { it.inputData.hikeId }
                .distinct()

            tracksProcessed = tracksWithFilteredPOIs.size
            totalTracks.set(tracksProcessed)

            tracksWithFilteredPOIs.forEach { hikeId ->
                // Update progress
                currentProgress.incrementAndGet()

                // Generate route for this track
                val result = generateRouteForTrack(hikeId)
                if (result.success) {
                    totalRoutes++
                }
            }
        }

        // Mark routing as completed
        routingInProgress = false
        return RouteResult(totalRoutes, tracksProcessed)
    }

    /**
     * Generate a route for a specific track
     * @param hikeId The ID of the hike/track
     * @return RouteResult with success status
     */
    fun generateRouteForTrack(hikeId: String): RouteResult {
        var success = false

        transaction {
            // Find the input data entity for the given hikeId
            val track = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()
                ?: return@transaction

            // First, purge existing routes for this track
            val existingRoutes = RouteEntity.find { RouteTable.inputDataId eq track.id }
            existingRoutes.forEach { it.delete() }

            // Get filtered POIs for this track, ordered by track position
            val filteredPOIs = FilteredPOIEntity.find { FilteredPOITable.inputDataId eq track.id }
                .orderBy(FilteredPOITable.trackPosition to SortOrder.ASC)
                .toList()

            if (filteredPOIs.isEmpty()) {
                return@transaction
            }

            // Extract track coordinates from GeoJSON
            val trackCoordinates = extractCoordinatesFromGeoJson(track.geoJson)
            if (trackCoordinates.isEmpty()) {
                return@transaction
            }

            // Get the first and last points of the track
            val firstPoint = trackCoordinates.first()
            val lastPoint = trackCoordinates.last()

            // Create a list of points for the route: first track point, filtered POIs, last track point
            val routePoints = mutableListOf<Point>()
            routePoints.add(firstPoint)

            // Add filtered POIs
            filteredPOIs.forEach { poi ->
                routePoints.add(Point(poi.latitude, poi.longitude))
            }

            routePoints.add(lastPoint)

            // Call the routing API
            val routeJson = callRoutingApi(routePoints)
            if (routeJson != null) {
                // Extract distance and duration from the route JSON
                val routeInfo = extractRouteInfo(routeJson)

                // Decode the polyline before storing
                val decodedRouteJson = decodePolyline(routeJson)

                // Store the route in the database
                RouteEntity.new {
                    this.inputData = track
                    this.routeJson = decodedRouteJson
                    this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    this.distance = routeInfo.first
                    this.duration = routeInfo.second
                }

                success = true
            }
        }

        return RouteResult(if (success) 1 else 0, 1)
    }

    /**
     * Call the routing API to generate a route
     * @param points List of points for the route
     * @return The route JSON or null if the API call fails
     */
    private fun callRoutingApi(points: List<Point>): String? {
        try {
            // Prepare the request body
            val pointsJson = points.joinToString(",") { 
                """[${it.lon},${it.lat}]""" 
            }

            val requestBody = """
                {
                    "profile": "hike",
                    "points": [$pointsJson],
                    "snap_preventions": [
                        "motorway",
                        "ferry",
                        "tunnel"
                    ],
                    "details": []
                }
            """.trimIndent()

            // Call the routing API
            val url = URL("https://gh.dev-in.fr/route?key=")
            val connection = url.openConnection()
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return response
        } catch (e: Exception) {
            println(e)
            return null
        }
    }

    /**
     * Extract route information (distance and duration) from the route JSON
     * @param routeJson The route JSON
     * @return Pair of distance (in meters) and duration (in seconds)
     */
    private fun extractRouteInfo(routeJson: String): Pair<Double, Double> {
        try {
            val jsonElement = Json.parseToJsonElement(routeJson)
            val paths = jsonElement.jsonObject["paths"]?.jsonArray

            if (paths != null && paths.isNotEmpty()) {
                val path = paths[0].jsonObject
                val distance = path["distance"]?.jsonPrimitive?.double ?: 0.0
                val time = path["time"]?.jsonPrimitive?.double ?: 0.0

                return Pair(distance, time / 1000.0) // Convert time from milliseconds to seconds
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }

        return Pair(0.0, 0.0)
    }

    /**
     * Decode the polyline in the route JSON and update it with the decoded coordinates
     * @param routeJson The route JSON with encoded polyline
     * @return Updated route JSON with decoded coordinates
     */
    private fun decodePolyline(routeJson: String): String {
        try {
            val jsonElement = Json.parseToJsonElement(routeJson)
            val jsonObject = jsonElement.jsonObject.toMutableMap()

            val paths = jsonObject["paths"]?.jsonArray
            if (paths != null && paths.isNotEmpty()) {
                val pathsArray = paths.toMutableList()
                val path = pathsArray[0].jsonObject.toMutableMap()

                // Check if points exists and is encoded
                val points = path["points"]?.jsonPrimitive?.content
                val pointsEncoded = path["points_encoded"]?.jsonPrimitive?.boolean ?: false

                if (points != null && pointsEncoded) {
                    // Decode the polyline
                    val decodedPoints = PolylineUtils.decode(points)

                    // Create a GeoJSON structure for the decoded points
                    val pointsObject = buildJsonObject {
                        put("type", "LineString")
                        putJsonArray("coordinates") {
                            decodedPoints.forEach { point ->
                                addJsonArray {
                                    add(point[0]) // longitude
                                    add(point[1]) // latitude
                                }
                            }
                        }
                    }

                    // Update the path with the decoded points
                    path["points"] = pointsObject
                    path["points_encoded"] = JsonPrimitive(false)

                    // Update the paths array
                    pathsArray[0] = JsonObject(path)
                    jsonObject["paths"] = JsonArray(pathsArray)

                    // Return the updated JSON
                    return Json.encodeToString(JsonObject.serializer(), JsonObject(jsonObject))
                }
            }

            // If no polyline to decode or decoding failed, return the original JSON
            return routeJson
        } catch (e: Exception) {
            println("Error decoding polyline: ${e.message}")
            // If there's an error, return the original JSON
            return routeJson
        }
    }

    /**
     * Extract coordinates from GeoJSON
     * @param geoJson The GeoJSON string
     * @return List of Point objects representing the track coordinates
     */
    private fun extractCoordinatesFromGeoJson(geoJson: String): List<Point> {
        try {
            val jsonElement = Json.parseToJsonElement(geoJson)
            val coordinates = mutableListOf<Point>()

            // Extract all coordinates from the GeoJSON
            fun extractCoordinates(element: JsonElement) {
                when (element) {
                    is JsonArray -> {
                        if (element.size == 2 && element[0] is JsonPrimitive && element[1] is JsonPrimitive) {
                            // This looks like a coordinate pair [lon, lat]
                            val lon = (element[0] as JsonPrimitive).double
                            val lat = (element[1] as JsonPrimitive).double
                            // For GeoJSON coordinates, we need to swap lat and lon for the Point constructor
                            coordinates.add(Point(lat, lon))
                        } else if (element.size == 3 && element[0] is JsonPrimitive && element[1] is JsonPrimitive) {
                            // This looks like a coordinate pair [lon, lat]
                            val lon = (element[0] as JsonPrimitive).double
                            val lat = (element[1] as JsonPrimitive).double
                            // For GeoJSON coordinates, we need to swap lat and lon for the Point constructor
                            coordinates.add(Point(lat, lon))
                        } else{
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
            return coordinates
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Purge all route data from the database
     * @return The number of records purged
     */
    fun purgeRouteData(): Long {
        var purgeCount: Long = 0
        transaction {
            purgeCount = RouteEntity.all().count()
            RouteEntity.all().forEach { it.delete() }
        }
        return purgeCount
    }

    /**
     * Purge route data for a specific track
     * @param hikeId The ID of the hike/track
     * @return The number of records purged
     */
    fun purgeRouteDataForTrack(hikeId: String): Long {
        var purgeCount: Long = 0
        transaction {
            // Find the input data entity for the given hikeId
            val inputData = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()

            if (inputData != null) {
                // Find and delete all routes associated with this input data
                val routes = RouteEntity.find { RouteTable.inputDataId eq inputData.id }
                purgeCount = routes.count()
                routes.forEach { it.delete() }
            }
        }
        return purgeCount
    }

    /**
     * Get route data for a specific track
     * @param hikeId The ID of the hike/track
     * @return RouteDTO or null if not found
     */
    fun getRouteForTrack(hikeId: String): RouteDTO? {
        return transaction {
            // Find the input data entity for the given hikeId
            val inputData = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()
                ?: return@transaction null

            // Find the most recent route for this track
            RouteEntity.find { RouteTable.inputDataId eq inputData.id }
                .orderBy(RouteTable.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.toDTO()
        }
    }

    /**
     * Get all routes
     * @return List of RouteDTO objects
     */
    fun getAllRoutes(): List<RouteDTO> {
        return transaction {
            RouteEntity.all()
                .orderBy(RouteTable.createdAt to SortOrder.DESC)
                .map { it.toDTO() }
        }
    }
}

/**
 * Data class for route result
 */
data class RouteResult(val routesGenerated: Int, val tracksProcessed: Int, val success: Boolean = true)

package com.example.services

import com.example.models.InputDataEntity
import com.example.models.InputDataTable
import com.example.models.RouteEntity
import com.example.models.RouteTable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for exporting data to various formats
 */
class ExportService {
    // Progress tracking
    private val currentProgress = AtomicInteger(0)
    private val totalTracks = AtomicInteger(0)
    private var inProgress = false

    /**
     * Export all hikes to GeoJSON files in the output folder
     * @return The number of files exported
     */
    fun exportToGeoJSON(): Int {
        // Reset progress
        currentProgress.set(0)
        inProgress = true

        try {
            // Create output directory if it doesn't exist
            val outputDir = File("output")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Get all routes from the database
            val exportCount = transaction {
                // Get the most recent route for each hike
                val routesByHike = mutableMapOf<String, RouteEntity>()

                // Get all routes ordered by creation date (newest first)
                val allRoutes = RouteEntity.all()
                    .orderBy(RouteTable.createdAt to SortOrder.DESC)
                    .toList()

                // Keep only the most recent route for each hike
                allRoutes.forEach { route ->
                    val hikeId = route.inputData.hikeId
                    if (!routesByHike.containsKey(hikeId)) {
                        routesByHike[hikeId] = route
                    }
                }

                totalTracks.set(routesByHike.size)

                // Export each route to a GeoJSON file
                var index = 0
                routesByHike.forEach { (hikeId, route) ->
                    val outputFile = File(outputDir, "$hikeId.json")
                    val geoJson = convertToGeoJSON(route.routeJson)
                    outputFile.writeText(geoJson)
                    currentProgress.set(++index)
                }

                routesByHike.size
            }

            return exportCount
        } finally {
            inProgress = false
        }
    }

    /**
     * Get the current export progress
     * @return A map with progress information
     */
    fun getExportProgress(): Map<String, Any> {
        return mapOf(
            "inProgress" to inProgress,
            "current" to currentProgress.get(),
            "total" to totalTracks.get()
        )
    }

    /**
     * Convert route JSON to standard GeoJSON format
     * @param routeJson The route JSON from the database
     * @return A standard GeoJSON string
     */
    private fun convertToGeoJSON(routeJson: String): String {
        try {
            val jsonElement = Json.parseToJsonElement(routeJson)
            val jsonObject = jsonElement.jsonObject

            // Extract the paths array
            val paths = jsonObject["paths"]?.jsonArray
            if (paths != null && paths.isNotEmpty()) {
                val path = paths[0].jsonObject

                // Extract the points object (which should be in GeoJSON format)
                val points = path["points"]?.jsonObject
                if (points != null && points["type"]?.jsonPrimitive?.content == "LineString") {
                    // Extract the coordinates array
                    val coordinates = points["coordinates"]?.jsonArray
                    if (coordinates != null) {
                        // Create a standard GeoJSON object
                        val geoJson = buildJsonObject {
                            put("type", "Feature")
                            putJsonObject("geometry") {
                                put("type", "LineString")
                                putJsonArray("coordinates") {
                                    coordinates.forEach { coord ->
                                        add(coord)
                                    }
                                }
                            }
                            putJsonObject("properties") {
                                // Add any properties from the original JSON
                                path["distance"]?.let { put("distance", it) }
                                path["time"]?.let { put("time", it) }
                                path["ascend"]?.let { put("ascend", it) }
                                path["descend"]?.let { put("descend", it) }
                            }
                        }

                        return Json.encodeToString(JsonObject.serializer(), geoJson)
                    }
                }
            }

            // If we couldn't extract the GeoJSON elements, return the original JSON
            return routeJson
        } catch (e: Exception) {
            println("Error converting to GeoJSON: ${e.message}")
            // If there's an error, return the original JSON
            return routeJson
        }
    }
}

package com.example.services

import com.example.models.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/**
 * Service class for handling Filtered POI operations
 */
class FilterPOIService {
    // Progress tracking
    private val currentProgress = AtomicInteger(0)
    private val totalTracks = AtomicInteger(0)
    private var filterInProgress = false

    /**
     * Get the current progress of POI filtering
     * @return ProgressStatus object with current progress and total
     */
    fun getFilterProgress(): ProgressStatus {
        return ProgressStatus(
            current = currentProgress.get(),
            total = totalTracks.get(),
            inProgress = filterInProgress
        )
    }

    /**
     * Reset the progress tracking
     */
    private fun resetProgress() {
        currentProgress.set(0)
        totalTracks.set(0)
        filterInProgress = false
    }

    /**
     * Get filtered POI counts by track
     * @return List of FilteredPOICountDTO objects, including hikes without filtered POIs
     */
    fun getFilteredPOICountsByTrack(): List<FilteredPOICountDTO> {
        return transaction {
            // Get all hikes
            val allHikes = InputDataEntity.all().map { it.hikeId }

            // Get filtered POI counts for hikes that have them
            val filteredPoiCounts = FilteredPOIEntity.all()
                .groupBy { it.inputData.hikeId }
                .map { (hikeId, pois) ->
                    hikeId to FilteredPOICountDTO(
                        hikeId = hikeId,
                        count = pois.size.toLong(),
                        artificialCount = pois.count { it.isArtificial }.toLong()
                    )
                }.toMap()

            // Create a list of FilteredPOICountDTO for all hikes, with zero counts for those without filtered POIs
            allHikes.map { hikeId ->
                filteredPoiCounts[hikeId] ?: FilteredPOICountDTO(
                    hikeId = hikeId,
                    count = 0,
                    artificialCount = 0
                )
            }
        }
    }

    /**
     * Get the total count of filtered POIs
     * @return The number of filtered POIs in the database
     */
    fun getTotalFilteredPOICount(): Long {
        return transaction {
            FilteredPOIEntity.all().count()
        }
    }

    /**
     * Get the total count of artificial POIs
     * @return The number of artificial POIs in the database
     */
    fun getTotalArtificialPOICount(): Long {
        return transaction {
            FilteredPOIEntity.find { FilteredPOITable.isArtificial eq true }.count()
        }
    }

    /**
     * Filter POIs for all tracks
     * @param maxPOIs Maximum number of POIs to keep per track
     * @return FilterResult with the count of filtered POIs and tracks processed
     */
    fun filterPOIsForAllTracks(maxPOIs: Int): FilterResult {
        var totalFilteredPois = 0
        var totalArtificialPois = 0
        var tracksProcessed = 0

        // Reset progress tracking
        resetProgress()
        filterInProgress = true

        transaction {
            // Get all tracks
            val tracks = InputDataEntity.all().toList()
            tracksProcessed = tracks.size

            // Set total tracks for progress tracking
            totalTracks.set(tracks.size)

            tracks.forEach { track ->
                // Update progress
                currentProgress.incrementAndGet()

                // Filter POIs for this track
                val result = filterPOIsForTrack(track.hikeId, maxPOIs)
                totalFilteredPois += result.filteredPois
                totalArtificialPois += result.artificialPois
            }
        }

        // Mark filtering as completed
        filterInProgress = false
        return FilterResult(totalFilteredPois, totalArtificialPois, tracksProcessed)
    }

    /**
     * Filter POIs for a specific track
     * @param hikeId The ID of the hike/track
     * @param maxPOIs Maximum number of POIs to keep
     * @return FilterResult with the count of filtered POIs and artificial POIs
     */
    fun filterPOIsForTrack(hikeId: String, maxPOIs: Int): FilterResult {
        var filteredPois = 0
        var artificialPoisCount = 0

        transaction {
            // Find the input data entity for the given hikeId
            val track = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()
                ?: return@transaction

            // First, purge existing filtered POIs for this track
            val existingFilteredPois = FilteredPOIEntity.find { FilteredPOITable.inputDataId eq track.id }
            existingFilteredPois.forEach { it.delete() }

            // Get all POIs for this track
            val pois = POIEntity.find { POITable.inputDataId eq track.id }.toList()

            // Parse track GeoJSON to get coordinates
            val trackCoordinates = extractCoordinatesFromGeoJson(track.geoJson)

            if (trackCoordinates.isEmpty()) {
                return@transaction
            }

            // Filter POIs that are within 500m of the track
            val poisWithinDistance = pois.filter { poi ->
                val poiPoint = Point(poi.latitude, poi.longitude)
                isPointNearTrack(poiPoint, trackCoordinates, 500.0)
            }

            // Calculate the closest point on the track for each POI
            val poisWithTrackPoints = poisWithinDistance.map { poi ->
                val poiPoint = Point(poi.latitude, poi.longitude)
                val closestPointInfo = findClosestPointOnTrack(poiPoint, trackCoordinates)
                POIWithTrackPoint(poi, closestPointInfo)
            }

            // Sort POIs by their position along the track
            val sortedPoisWithTrackPoints = poisWithTrackPoints.sortedBy { it.trackPointInfo.segmentDistance }

            // If we have more POIs than the maximum, select them evenly along the track
            val selectedPois = if (poisWithinDistance.size > maxPOIs) {
                // Select POIs evenly along the track
                selectPoisEvenly(sortedPoisWithTrackPoints, maxPOIs)
            } else {
                // Convert to list of POIEntity
                sortedPoisWithTrackPoints.map { it.poi }
            }

            // Create a map of POIs to their track positions
            val poiToPositionMap = poisWithTrackPoints.associate { it.poi to it.trackPointInfo.segmentDistance }

            // Store the selected POIs as filtered POIs
            selectedPois.forEach { poi ->
                // Get the track position for this POI
                val trackPosition = poiToPositionMap[poi] ?: 0.0

                FilteredPOIEntity.new {
                    this.inputData = track
                    this.type = poi.type
                    this.name = poi.name
                    this.latitude = poi.latitude
                    this.longitude = poi.longitude
                    this.osmId = poi.osmId
                    this.properties = poi.properties
                    this.isArtificial = false
                    this.trackPosition = trackPosition
                }
                filteredPois++
            }

            // If we have fewer POIs than the maximum, create artificial ones
            if (selectedPois.size < maxPOIs) {
                val numArtificialToCreate = maxPOIs - selectedPois.size
                val artificialPois = createArtificialPOIs(trackCoordinates, numArtificialToCreate, selectedPois)

                // Store the artificial POIs
                artificialPois.forEach { pointWithPosition ->
                    FilteredPOIEntity.new {
                        this.inputData = track
                        this.type = "artificial:poi"
                        this.name = "Artificial POI"
                        this.latitude = pointWithPosition.point.lat
                        this.longitude = pointWithPosition.point.lon
                        this.osmId = null
                        this.properties = "{}"
                        this.isArtificial = true
                        this.trackPosition = pointWithPosition.position
                    }
                    artificialPoisCount++
                }
            }
        }

        return FilterResult(filteredPois, artificialPoisCount, 1)
    }

    /**
     * Purge all filtered POI data from the database
     * @return The number of records purged
     */
    fun purgeFilteredPOIData(): Long {
        var purgeCount: Long = 0
        transaction {
            purgeCount = FilteredPOIEntity.all().count()
            FilteredPOIEntity.all().forEach { it.delete() }
        }
        return purgeCount
    }

    /**
     * Purge filtered POI data for a specific track
     * @param hikeId The ID of the hike/track
     * @return The number of records purged
     */
    fun purgeFilteredPOIDataForTrack(hikeId: String): Long {
        var purgeCount: Long = 0
        transaction {
            // Find the input data entity for the given hikeId
            val inputData = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()

            if (inputData != null) {
                // Find and delete all filtered POIs associated with this input data
                val pois = FilteredPOIEntity.find { FilteredPOITable.inputDataId eq inputData.id }
                purgeCount = pois.count()
                pois.forEach { it.delete() }
            }
        }
        return purgeCount
    }

    /**
     * Get all filtered POIs for a specific track
     * @param hikeId The ID of the hike/track
     * @return List of FilteredPOIDTO objects ordered by their position along the track
     */
    fun getFilteredPOIsForTrack(hikeId: String): List<FilteredPOIDTO> {
        return transaction {
            // Find the input data entity for the given hikeId
            val inputData = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()
                ?: return@transaction emptyList()

            // Find all filtered POIs associated with this input data, ordered by their position along the track
            FilteredPOIEntity.find { FilteredPOITable.inputDataId eq inputData.id }
                .orderBy(FilteredPOITable.trackPosition to SortOrder.ASC)
                .map { it.toDTO() }
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
                            coordinates.add(Point(lat, lon))
                        } else if (element.size == 3 && element[0] is JsonPrimitive && element[1] is JsonPrimitive) {
                            // This looks like a coordinate pair [lon, lat]
                            val lon = (element[0] as JsonPrimitive).double
                            val lat = (element[1] as JsonPrimitive).double
                            coordinates.add(Point(lat, lon))
                        }else {
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
     * Check if a point is within a certain distance of a track
     * @param point The point to check
     * @param trackPoints The track coordinates
     * @param maxDistance The maximum distance in meters
     * @return True if the point is within the specified distance of the track
     */
    private fun isPointNearTrack(point: Point, trackPoints: List<Point>, maxDistance: Double): Boolean {
        // Find the closest point on the track
        val closestPointInfo = findClosestPointOnTrack(point, trackPoints)
        return closestPointInfo.distance <= maxDistance
    }

    /**
     * Find the closest point on a track to a given point
     * @param point The point to find the closest point for
     * @param trackPoints The track coordinates
     * @return ClosestPointInfo with the closest point and its distance
     */
    private fun findClosestPointOnTrack(point: Point, trackPoints: List<Point>): ClosestPointInfo {
        var minDistance = Double.MAX_VALUE
        var closestPoint = trackPoints.first()
        var segmentDistance = 0.0
        var currentSegmentDistance = 0.0

        // Check each segment of the track
        for (i in 0 until trackPoints.size - 1) {
            val p1 = trackPoints[i]
            val p2 = trackPoints[i + 1]

            // Calculate the closest point on this segment
            val closestOnSegment = closestPointOnSegment(point, p1, p2)
            val distance = haversineDistance(point, closestOnSegment)

            // If this is the closest point so far, update the result
            if (distance < minDistance) {
                minDistance = distance
                closestPoint = closestOnSegment
                segmentDistance = currentSegmentDistance + haversineDistance(p1, closestOnSegment)
            }

            // Add the length of this segment to the current segment distance
            currentSegmentDistance += haversineDistance(p1, p2)
        }

        return ClosestPointInfo(closestPoint, minDistance, segmentDistance)
    }

    /**
     * Find the closest point on a line segment to a given point
     * @param p The point to find the closest point for
     * @param v The start of the line segment
     * @param w The end of the line segment
     * @return The closest point on the segment
     */
    private fun closestPointOnSegment(p: Point, v: Point, w: Point): Point {
        // Convert to Cartesian coordinates for simplicity
        // This is an approximation that works for short distances
        val vx = v.lon
        val vy = v.lat
        val wx = w.lon
        val wy = w.lat
        val px = p.lon
        val py = p.lat

        // Calculate the squared length of the segment
        val l2 = (wx - vx) * (wx - vx) + (wy - vy) * (wy - vy)
        if (l2 == 0.0) return v // v == w case

        // Calculate the projection of p onto the segment
        val t = ((px - vx) * (wx - vx) + (py - vy) * (wy - vy)) / l2

        // Clamp t to [0, 1] to ensure the point is on the segment
        val clampedT = t.coerceIn(0.0, 1.0)

        // Calculate the coordinates of the closest point
        val closestX = vx + clampedT * (wx - vx)
        val closestY = vy + clampedT * (wy - vy)

        return Point(closestY, closestX)
    }

    /**
     * Calculate the Haversine distance between two points
     * @param p1 The first point
     * @param p2 The second point
     * @return The distance in meters
     */
    private fun haversineDistance(p1: Point, p2: Point): Double {
        val R = 6371e3 // Earth radius in meters
        val phi1 = p1.lat * Math.PI / 180
        val phi2 = p2.lat * Math.PI / 180
        val deltaPhi = (p2.lat - p1.lat) * Math.PI / 180
        val deltaLambda = (p2.lon - p1.lon) * Math.PI / 180

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    /**
     * Select POIs evenly along the track
     * @param pois List of POIs with their track points
     * @param maxPOIs Maximum number of POIs to select
     * @return List of selected POIs
     */
    private fun selectPoisEvenly(pois: List<POIWithTrackPoint>, maxPOIs: Int): List<POIEntity> {
        if (pois.isEmpty() || maxPOIs <= 0) return emptyList()
        if (pois.size <= maxPOIs) return pois.map { it.poi }

        // Get the total track length
        val trackLength = pois.last().trackPointInfo.segmentDistance

        // Calculate the ideal spacing between POIs
        val idealSpacing = trackLength / maxPOIs

        val selected = mutableListOf<POIEntity>()
        var nextIdealPosition = 0.0

        // Always include the first POI
        selected.add(pois.first().poi)
        nextIdealPosition += idealSpacing

        // Select POIs that are closest to the ideal positions
        for (i in 1 until maxPOIs - 1) {
            // Find the POI closest to the next ideal position
            val closestPoi = pois.minByOrNull { 
                abs(it.trackPointInfo.segmentDistance - nextIdealPosition) 
            }

            closestPoi?.let { selected.add(it.poi) }
            nextIdealPosition += idealSpacing
        }

        // Always include the last POI
        selected.add(pois.last().poi)

        return selected
    }

    /**
     * Create artificial POIs evenly distributed along the track
     * @param trackPoints The track coordinates
     * @param numPOIs Number of artificial POIs to create
     * @param existingPOIs List of existing POIs to avoid placing artificial ones too close
     * @return List of artificial POI points with their positions along the track
     */
    private fun createArtificialPOIs(
        trackPoints: List<Point>, 
        numPOIs: Int,
        existingPOIs: List<POIEntity>
    ): List<PointWithPosition> {
        if (trackPoints.size < 2 || numPOIs <= 0) return emptyList()

        // Calculate the total track length
        var totalLength = 0.0
        for (i in 0 until trackPoints.size - 1) {
            totalLength += haversineDistance(trackPoints[i], trackPoints[i + 1])
        }

        // Calculate the ideal spacing between POIs
        val idealSpacing = totalLength / (numPOIs + 1)

        // Create artificial POIs at the ideal positions
        val artificialPOIs = mutableListOf<PointWithPosition>()
        var currentDistance = idealSpacing
        var accumulatedDistance = 0.0

        for (i in 0 until trackPoints.size - 1) {
            val p1 = trackPoints[i]
            val p2 = trackPoints[i + 1]
            val segmentLength = haversineDistance(p1, p2)

            // Check if we need to place a POI on this segment
            while (accumulatedDistance + segmentLength >= currentDistance && artificialPOIs.size < numPOIs) {
                // Calculate the position along this segment
                val ratio = (currentDistance - accumulatedDistance) / segmentLength

                // Interpolate between p1 and p2
                val lat = p1.lat + ratio * (p2.lat - p1.lat)
                val lon = p1.lon + ratio * (p2.lon - p1.lon)
                val artificialPoint = Point(lat, lon)

                // Check if this point is too close to an existing POI
                val tooClose = existingPOIs.any { poi ->
                    val poiPoint = Point(poi.latitude, poi.longitude)
                    haversineDistance(artificialPoint, poiPoint) < 100 // 100m minimum distance
                }

                if (!tooClose) {
                    artificialPOIs.add(PointWithPosition(artificialPoint, currentDistance))
                }

                currentDistance += idealSpacing
            }

            accumulatedDistance += segmentLength
        }

        return artificialPOIs
    }
}

/**
 * Data class for a point (latitude, longitude)
 */
data class Point(val lat: Double, val lon: Double)

/**
 * Data class for a point with its position along the track
 */
data class PointWithPosition(
    val point: Point,
    val position: Double // Position along the track
)

/**
 * Data class for information about the closest point on a track
 */
data class ClosestPointInfo(
    val point: Point,
    val distance: Double,
    val segmentDistance: Double // Distance along the track
)

/**
 * Data class for a POI with its closest point on the track
 */
data class POIWithTrackPoint(
    val poi: POIEntity,
    val trackPointInfo: ClosestPointInfo
)

/**
 * Data class for filter result
 */
data class FilterResult(
    val filteredPois: Int,
    val artificialPois: Int,
    val tracksProcessed: Int
)

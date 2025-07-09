package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import java.util.UUID

// Database table definition for Filtered POIs
object FilteredPOITable : UUIDTable("filtered_poi_data") {
    val inputDataId = reference("input_data_id", InputDataTable)
    val type = varchar("type", 50)
    val name = varchar("name", 255).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val osmId = varchar("osm_id", 50).nullable() // Can be null for artificial POIs
    val properties = text("properties")
    val isArtificial = bool("is_artificial").default(false) // Flag to indicate if this is an artificial POI
    val trackPosition = double("track_position").default(0.0) // Position along the track for ordering
}

// DAO Entity for Filtered POIs
class FilteredPOIEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FilteredPOIEntity>(FilteredPOITable)

    var inputData by InputDataEntity referencedOn FilteredPOITable.inputDataId
    var type by FilteredPOITable.type
    var name by FilteredPOITable.name
    var latitude by FilteredPOITable.latitude
    var longitude by FilteredPOITable.longitude
    var osmId by FilteredPOITable.osmId
    var properties by FilteredPOITable.properties
    var isArtificial by FilteredPOITable.isArtificial
    var trackPosition by FilteredPOITable.trackPosition
}

// Data Transfer Object for API responses
@Serializable
data class FilteredPOIDTO(
    val id: String,
    val inputDataId: String,
    val type: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val osmId: String?,
    val properties: String,
    val isArtificial: Boolean,
    val trackPosition: Double
)

// Conversion function
fun FilteredPOIEntity.toDTO(): FilteredPOIDTO = FilteredPOIDTO(
    id = id.value.toString(),
    inputDataId = inputData.id.value.toString(),
    type = type,
    name = name,
    latitude = latitude,
    longitude = longitude,
    osmId = osmId,
    properties = properties,
    isArtificial = isArtificial,
    trackPosition = trackPosition
)

// Data class for Filtered POI count per track
@Serializable
data class FilteredPOICountDTO(
    val hikeId: String,
    val count: Long,
    val artificialCount: Long
)

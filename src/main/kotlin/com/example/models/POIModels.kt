package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import java.util.UUID

// Database table definition for POIs
object POITable : UUIDTable("poi_data") {
    val inputDataId = reference("input_data_id", InputDataTable)
    val type = varchar("type", 50)
    val name = varchar("name", 255).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val osmId = varchar("osm_id", 50)
    val properties = text("properties")
}

// DAO Entity for POIs
class POIEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<POIEntity>(POITable)
    
    var inputData by InputDataEntity referencedOn POITable.inputDataId
    var type by POITable.type
    var name by POITable.name
    var latitude by POITable.latitude
    var longitude by POITable.longitude
    var osmId by POITable.osmId
    var properties by POITable.properties
}

// Data Transfer Object for API responses
@Serializable
data class POIDTO(
    val id: String,
    val inputDataId: String,
    val type: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val osmId: String,
    val properties: String
)

// Conversion function
fun POIEntity.toDTO(): POIDTO = POIDTO(
    id = id.value.toString(),
    inputDataId = inputData.id.value.toString(),
    type = type,
    name = name,
    latitude = latitude,
    longitude = longitude,
    osmId = osmId,
    properties = properties
)

// Data class for POI count per track
@Serializable
data class POICountDTO(
    val hikeId: String,
    val count: Long
)
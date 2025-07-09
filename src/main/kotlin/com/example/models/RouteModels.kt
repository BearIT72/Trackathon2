package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import java.util.UUID
// Database table definition for Routes
object RouteTable : UUIDTable("route_data") {
    val inputDataId = reference("input_data_id", InputDataTable)
    val routeJson = text("route_json")
    val createdAt = varchar("created_at", 50)
    val distance = double("distance").nullable()
    val duration = double("duration").nullable()
}

// DAO Entity for Routes
class RouteEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RouteEntity>(RouteTable)

    var inputData by InputDataEntity referencedOn RouteTable.inputDataId
    var routeJson by RouteTable.routeJson
    var createdAt by RouteTable.createdAt
    var distance by RouteTable.distance
    var duration by RouteTable.duration
}

// Data Transfer Object for API responses
@Serializable
data class RouteDTO(
    val id: String,
    val inputDataId: String,
    val hikeId: String,
    val routeJson: String,
    val createdAt: String,
    val distance: Double?,
    val duration: Double?
)

// Conversion function
fun RouteEntity.toDTO(): RouteDTO = RouteDTO(
    id = id.value.toString(),
    inputDataId = inputData.id.value.toString(),
    hikeId = inputData.hikeId,
    routeJson = routeJson,
    createdAt = createdAt.toString(),
    distance = distance,
    duration = duration
)

// Data class for Route count per track
@Serializable
data class RouteCountDTO(
    val hikeId: String,
    val count: Long
)

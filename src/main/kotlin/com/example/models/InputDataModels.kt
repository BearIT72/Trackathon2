package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import java.util.UUID

// Database table definition
object InputDataTable : UUIDTable("inputdata") {
    val hikeId = varchar("hike_id", 50).uniqueIndex()
    val geoJson = text("geo_json")
}

// DAO Entity
class InputDataEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InputDataEntity>(InputDataTable)
    
    var hikeId by InputDataTable.hikeId
    var geoJson by InputDataTable.geoJson
}

// Data Transfer Object for API responses
@Serializable
data class InputDataDTO(
    val id: String,
    val hikeId: String,
    val geoJson: String
)

// Conversion function
fun InputDataEntity.toDTO(): InputDataDTO = InputDataDTO(
    id = id.value.toString(),
    hikeId = hikeId,
    geoJson = geoJson
)
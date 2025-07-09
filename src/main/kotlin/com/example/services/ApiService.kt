package com.example.services

import com.example.models.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Service class for handling API-related operations
 */
class ApiService {
    /**
     * Get health status of the application
     * @return ApiResponse with health status
     */
    fun getHealthStatus(): ApiResponse {
        return ApiResponse(
            status = "success",
            message = "Service is healthy",
            data = mapOf("status" to "UP")
        )
    }

    /**
     * Get version information
     * @return VersionInfo object
     */
    fun getVersionInfo(): VersionInfo {
        return VersionInfo()
    }

    /**
     * Get application information
     * @return ApiResponse with application information
     */
    fun getApplicationInfo(): ApiResponse {
        return ApiResponse(
            status = "success",
            message = "Application information",
            data = mapOf(
                "name" to "Ktor Web App",
                "description" to "A simple Ktor web application with up-to-date dependencies"
            )
        )
    }

    /**
     * Get input data count
     * @return ApiResponse with input data count
     */
    fun getInputDataCount(): ApiResponse {
        val count = transaction {
            InputDataEntity.all().count()
        }
        return ApiResponse(
            status = "success",
            message = "Input data retrieved",
            data = mapOf("count" to count.toString())
        )
    }
}

/**
 * Data class for API response
 */
@Serializable
data class ApiResponse(
    val status: String,
    val message: String,
    val data: Map<String, String>
)

/**
 * Data class for version information
 */
@Serializable
data class VersionInfo(
    val version: String = "1.0.0",
    val buildDate: String = "2023-12-15",
    val environment: String = "development"
)
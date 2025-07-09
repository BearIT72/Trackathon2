package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val status: String,
    val message: String,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class VersionInfo(
    val kotlinVersion: String = "1.9.22",
    val ktorVersion: String = "2.3.7",
    val appVersion: String = "0.0.1"
)
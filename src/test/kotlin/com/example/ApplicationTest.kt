package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Welcome to Ktor Web App"))
    }
    
    @Test
    fun testHealthEndpoint() = testApplication {
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
        assertTrue(response.bodyAsText().contains("Service is healthy"))
    }
    
    @Test
    fun testVersionEndpoint() = testApplication {
        val response = client.get("/api/version")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("1.9.22"))
        assertTrue(response.bodyAsText().contains("2.3.7"))
    }
    
    @Test
    fun testInfoEndpoint() = testApplication {
        val response = client.get("/api/info")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
        assertTrue(response.bodyAsText().contains("Ktor Web App"))
    }
}
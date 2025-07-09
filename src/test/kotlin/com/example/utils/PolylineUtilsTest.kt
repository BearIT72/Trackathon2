package com.example.utils

import kotlin.test.*

class PolylineUtilsTest {
    @Test
    fun testPolylineDecode() {
        // Example encoded polyline from Google Maps API
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        
        // Expected decoded coordinates
        val expected = listOf(
            listOf(-120.2, 38.5),
            listOf(-120.95, 40.7),
            listOf(-126.453, 43.252)
        )
        
        // Decode the polyline
        val decoded = PolylineUtils.decode(encoded)
        
        // Check that we have the expected number of points
        assertEquals(expected.size, decoded.size)
        
        // Check each point with a small delta for floating point comparison
        for (i in expected.indices) {
            assertEquals(expected[i][0], decoded[i][0], 0.001)
            assertEquals(expected[i][1], decoded[i][1], 0.001)
        }
    }
}
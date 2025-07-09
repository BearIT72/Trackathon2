package com.example.utils

/**
 * Utility class for handling polyline encoding/decoding
 * Based on the Google polyline algorithm format
 */
object PolylineUtils {
    /**
     * Decodes an encoded polyline string into a list of coordinates
     * @param encoded The encoded polyline string
     * @return List of coordinate pairs [longitude, latitude]
     */
    fun decode(encoded: String): List<List<Double>> {
        val result = mutableListOf<List<Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result1 = 0
            do {
                b = encoded[index++].code - 63
                result1 = result1 or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result1 and 1 != 0) (result1 shr 1).inv() else result1 shr 1
            lat += dlat

            shift = 0
            result1 = 0
            do {
                b = encoded[index++].code - 63
                result1 = result1 or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result1 and 1 != 0) (result1 shr 1).inv() else result1 shr 1
            lng += dlng

            val latDouble = lat.toDouble() / 1e5
            val lngDouble = lng.toDouble() / 1e5
            result.add(listOf(lngDouble, latDouble)) // GeoJSON format is [longitude, latitude]
        }
        return result
    }
}
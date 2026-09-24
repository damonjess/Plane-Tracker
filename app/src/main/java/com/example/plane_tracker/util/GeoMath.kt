package com.example.plane_tracker.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Formats the remaining flight time in minutes as a real-time ETA clock (e.g. "ETA 21:24").
 */
fun calculateClockETA(minutesToGo: Int, now: LocalTime = LocalTime.now()): String {
    if (minutesToGo <= 0) return "Arriving now"

    // Adds the remaining minutes to the device's current local time
    val etaTime = now.plusMinutes(minutesToGo.toLong())
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    return "ETA ${etaTime.format(formatter)}"
}

/** Geographic math helpers: dead-reckoning projection and great-circle distance. */
object GeoMath {

    private const val METERS_PER_DEG_LAT = 111_320.0

    /**
     * Dead reckoning: projects a position forward along its heading using
     * speed x elapsed time. Returns (longitude, latitude).
     */
    fun deadReckon(
        longitude: Double,
        latitude: Double,
        headingDegrees: Double,
        velocityMps: Double,
        elapsedSeconds: Double
    ): Pair<Double, Double> {
        val distance = velocityMps * elapsedSeconds
        val headingRad = headingDegrees * (PI / 180.0)
        val latRad = latitude * (PI / 180.0)
        val metersPerLonDegree = METERS_PER_DEG_LAT * cos(latRad)
        val deltaLat = (distance * cos(headingRad)) / METERS_PER_DEG_LAT
        val deltaLon = if (abs(metersPerLonDegree) < 1e-6) 0.0
            else (distance * sin(headingRad)) / metersPerLonDegree
        return (longitude + deltaLon) to (latitude + deltaLat)
    }

    /** Great-circle distance between two points in meters. */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val aCoerced = a.coerceIn(0.0, 1.0)
        return 6_371_000.0 * 2 * atan2(sqrt(aCoerced), sqrt(1 - aCoerced))
    }

    /**
     * Approximates a great-circle path starting at (lon, lat) flying along
     * [headingDegrees] for [distanceKm], sampled every ~40 km.
     * Returns the list of points along the projected course.
     */
    fun greatCirclePath(
        longitude: Double,
        latitude: Double,
        headingDegrees: Double,
        distanceKm: Double
    ): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val steps = (distanceKm / 40.0).toInt().coerceIn(4, 12)
        val stepKm = distanceKm / steps
        var currentLat = latitude
        var currentLon = longitude
        points.add(currentLon to currentLat)
        repeat(steps) {
            val next = deadReckon(currentLon, currentLat, headingDegrees, stepKm * 1000.0, 1.0)
            currentLon = next.first
            currentLat = next.second
            points.add(currentLon to currentLat)
        }
        return points
    }

    /** Resolves country flag emoji and country name from MMSI Maritime Identification Digits (MID). */
    fun getFlagAndCountryFromMmsi(mmsi: String): Pair<String, String> {
        val cleanMmsi = mmsi.trim()
        if (cleanMmsi.length < 3) return "⚓" to "International"
        val mid = cleanMmsi.substring(0, 3).toIntOrNull() ?: return "⚓" to "International"
        return when (mid) {
            232, 233, 234, 235 -> "🇬🇧" to "United Kingdom"
            244, 245, 246 -> "🇳🇱" to "Netherlands"
            211, 218 -> "🇩🇪" to "Germany"
            226, 227, 228 -> "🇫🇷" to "France"
            265, 266 -> "🇸🇪" to "Sweden"
            257, 258, 259 -> "🇳🇴" to "Norway"
            219, 220 -> "🇩🇰" to "Denmark"
            230 -> "🇫🇮" to "Finland"
            205 -> "🇧🇪" to "Belgium"
            247 -> "🇮🇹" to "Italy"
            224, 225 -> "🇪🇸" to "Spain"
            263 -> "🇵🇹" to "Portugal"
            250 -> "🇮🇪" to "Ireland"
            338, 366, 367, 368, 369 -> "🇺🇸" to "United States"
            316 -> "🇨🇦" to "Canada"
            503 -> "🇦🇺" to "Australia"
            512 -> "🇳🇿" to "New Zealand"
            601 -> "🇿🇦" to "South Africa"
            273 -> "🇷🇺" to "Russia"
            else -> "⚓" to "Maritime ($mid)"
        }
    }
}

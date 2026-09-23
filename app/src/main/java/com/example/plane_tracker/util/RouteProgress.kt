package com.example.plane_tracker.util

import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.data.Airport

/** Live progress of an aircraft along its origin -> destination route. */
data class RouteProgress(
    val totalKm: Double,
    val flownKm: Double,
    val remainingKm: Double,
    /** 0..1 clamped fraction of the route completed. */
    val fraction: Float,
    /** Estimated minutes to arrival at current ground speed; null when not computable. */
    val etaMinutes: Int?
)

/**
 * Computes live route progress from the aircraft's real position versus the
 * origin and destination airports reported by adsbdb.
 *
 * Note: flown + remaining can slightly exceed total when the aircraft is
 * off the direct great-circle track; the progress bar uses flown/total.
 */
object RouteProgressCalculator {

    fun compute(aircraft: Aircraft, origin: Airport, destination: Airport): RouteProgress? {
        val oLat = origin.latitude ?: return null
        val oLon = origin.longitude ?: return null
        val dLat = destination.latitude ?: return null
        val dLon = destination.longitude ?: return null

        val totalM = GeoMath.distanceMeters(oLat, oLon, dLat, dLon)
        if (totalM < 1.0) return null

        val flownM = GeoMath.distanceMeters(oLat, oLon, aircraft.latitude, aircraft.longitude)
        val remainingM = GeoMath.distanceMeters(aircraft.latitude, aircraft.longitude, dLat, dLon)

        val fraction = (flownM / totalM).toFloat().coerceIn(0f, 1f)

        val etaMinutes = if (aircraft.onGround || aircraft.velocityMps < 20.0) {
            null // parked or too slow for a meaningful estimate
        } else {
            ((remainingM / aircraft.velocityMps) / 60.0).toInt()
        }

        return RouteProgress(
            totalKm = totalM / 1000.0,
            flownKm = flownM / 1000.0,
            remainingKm = remainingM / 1000.0,
            fraction = fraction,
            etaMinutes = etaMinutes
        )
    }
}

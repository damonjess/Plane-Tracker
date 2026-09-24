package com.example.plane_tracker.data

import com.example.plane_tracker.util.GeoMath

data class Vessel(
    val mmsi: String,
    @Volatile var name: String = "",
    @Volatile var latitude: Double = 0.0,
    @Volatile var longitude: Double = 0.0,
    @Volatile var speedKnots: Double = 0.0,
    @Volatile var heading: Double = 0.0,
    @Volatile var isLifeboat: Boolean = false,
    @Volatile var lastSeen: Long = System.currentTimeMillis(),
    @Volatile var callSign: String = "",
    @Volatile var shipType: Int = 0,
    @Volatile var navStatus: Int = 15,
    @Volatile var destination: String = "",
    @Volatile var draught: Double = 0.0,
    @Volatile var lengthMeters: Int = 0,
    @Volatile var widthMeters: Int = 0,
    @Volatile var imoNumber: Int = 0
) {
    /** Set when name/type changed and the cached lifeboat verdict needs recomputing. Managed by AisRepository. */
    @Volatile
    var lifeboatCheckDirty: Boolean = true
    /** Derives country flag emoji and country name from MMSI MID digits. */
    val countryFlagAndName: Pair<String, String>
        get() = GeoMath.getFlagAndCountryFromMmsi(mmsi)

    /** Friendly navigational status description according to AIS specification. */
    val navStatusText: String
        get() = when (navStatus) {
            0 -> "Under way using engine"
            1 -> "At anchor"
            2 -> "Not under command"
            3 -> "Restricted manoeuvrability"
            4 -> "Constrained by her draught"
            5 -> "Moored"
            6 -> "Aground"
            7 -> "Engaged in fishing"
            8 -> "Under way sailing"
            14 -> "AIS-SART active"
            else -> if (speedKnots < 0.5) "Moored" else "Under way"
        }

    /** Friendly ship type description. */
    val shipTypeText: String
        get() = when (shipType) {
            50 -> "Pilot Vessel"
            52 -> "Tug"
            53 -> "Port Tender"
            55 -> "Law Enforcement"
            // 51 (SAR) and unknown: AIS lifeboats rarely broadcast a type,
            // and every vessel here is rescue-fleet, so default to the SAR label.
            else -> "Search and Rescue vessel"
        }
}

/** One historical position sample for a vessel's course trail. */
data class VesselTrailPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long
)

/**
 * Records a bounded breadcrumb trail of each lifeboat's positions.
 *
 * Pure and injectable-timed so it's unit-testable: a sample lands only after
 * both the minimum interval AND a real movement, so a moored boat's GPS
 * wobble never grows the trail.
 */
class VesselTrailRecorder(
    private val minIntervalMs: Long = 15_000L,
    private val minDistanceMeters: Double = 50.0,
    private val maxPoints: Int = 120
) {
    private val trails = java.util.concurrent.ConcurrentHashMap<String, MutableList<VesselTrailPoint>>()

    fun record(mmsi: String, latitude: Double, longitude: Double, nowMs: Long) {
        if (latitude == 0.0 && longitude == 0.0) return
        val trail = trails.getOrPut(mmsi) { mutableListOf() }
        synchronized(trail) {
            val last = trail.lastOrNull()
            if (last != null) {
                if (nowMs - last.timestampMs < minIntervalMs) return
                val moved = GeoMath.distanceMeters(last.latitude, last.longitude, latitude, longitude)
                if (moved < minDistanceMeters) return
            }
            trail.add(VesselTrailPoint(latitude, longitude, nowMs))
            if (trail.size > maxPoints) trail.removeAt(0)
        }
    }

    fun trailFor(mmsi: String): List<VesselTrailPoint> =
        trails[mmsi]?.let { synchronized(it) { it.toList() } } ?: emptyList()

    /** Drops trails for vessels no longer in the live set. */
    fun retainAll(keepMmsis: Set<String>) {
        trails.keys.retainAll(keepMmsis)
    }
}

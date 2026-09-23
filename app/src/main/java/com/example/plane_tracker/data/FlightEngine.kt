package com.example.plane_tracker.data

import com.example.plane_tracker.util.GeoMath

/** One historical position sample for the selected aircraft's trail. */
data class TrailPoint(
    val longitude: Double,
    val latitude: Double,
    val altitudeMeters: Double,
    val timestampMs: Long
)

/** Immutable render frame handed to the map each tick. */
data class MapFrame(
    val planes: org.maplibre.geojson.FeatureCollection,
    val selected: Aircraft?,
    val trailCoordinates: List<org.maplibre.geojson.Point>,
    val followPos: org.maplibre.android.geometry.LatLng?
)

/**
 * Core tracking engine: merges API snapshots into live state, interpolates
 * positions between updates (dead reckoning), maintains history trails and
 * tracks the selected aircraft.
 */
class FlightEngine {

    private val fleet = LinkedHashMap<String, Aircraft>()
    private val trails = mutableMapOf<String, MutableList<TrailPoint>>()
    private val history = mutableMapOf<String, Aircraft>() // last raw report per hex

    /** Last fleet snapshot metadata. */
    var lastFleetState: FleetState? = null
        private set

    @Volatile
    var selectedHex: String? = null

    /** Trail sample interval in ms (record roughly every 10s). */
    private val trailIntervalMs = 10_000L
    /** How long after lastSeen a plane stops being dead-reckoned. */
    private val maxExtrapolationMs = 90_000L

    /** Merges a freshly fetched fleet snapshot into live state. */
    @Synchronized
    fun mergeFleet(state: FleetState) {
        lastFleetState = state
        val now = state.fetchedAt
        state.aircraft.forEach { ac ->
            fleet[ac.icao24] = ac
            history[ac.icao24] = ac
            recordTrailPoint(ac, now)
        }
        // Expire aircraft not seen for 5 minutes.
        fleet.entries.removeAll { now - it.value.lastSeen > 300_000 }
        history.entries.removeAll { now - it.value.lastSeen > 300_000 }
    }

    private fun recordTrailPoint(ac: Aircraft, now: Long) {
        val trail = trails.getOrPut(ac.icao24) { mutableListOf() }
        val last = trail.lastOrNull()
        if (last != null && last.longitude == ac.longitude && last.latitude == ac.latitude) return
        if (last == null || now - last.timestampMs >= trailIntervalMs) {
            trail.add(TrailPoint(ac.longitude, ac.latitude, ac.altitudeMeters, now))
            if (trail.size > 90) trail.removeAt(0)
        }
    }

    /** Effective interpolated state for an aircraft at [now]. */
    fun interpolate(ac: Aircraft, now: Long): Aircraft {
        val elapsedMs = now - ac.lastSeen
        if (ac.onGround || ac.velocityMps < 1.0 || elapsedMs <= 0 ||
            elapsedMs > maxExtrapolationMs
        ) return ac
        val (lon, lat) = GeoMath.deadReckon(
            ac.longitude, ac.latitude, ac.heading.toDouble(),
            ac.velocityMps, elapsedMs / 1000.0
        )
        return ac.copy(longitude = lon, latitude = lat)
    }

    @Synchronized
    fun aircraftByHex(hex: String): Aircraft? = fleet[hex]

    @Synchronized
    fun allAircraft(now: Long): List<Aircraft> = fleet.values.map { interpolate(it, now) }

    @Synchronized
    fun selectedAircraft(now: Long): Aircraft? =
        selectedHex?.let { fleet[it] }?.let { interpolate(it, now) }

    @Synchronized
    fun trailFor(hex: String): List<TrailPoint> = trails[hex]?.toList() ?: emptyList()

    /** Drops trail samples older than [cutoffMs] and empties stale trails. */
    @Synchronized
    fun trimTrails(cutoffMs: Long) {
        trails.entries.removeAll { (_, trail) ->
            trail.removeAll { it.timestampMs < cutoffMs }
            trail.isEmpty()
        }
    }

    /** Latest raw API report for the selected aircraft, if newer than interpolation. */
    @Synchronized
    fun rawSelected(): Aircraft? = selectedHex?.let { history[it] }
}

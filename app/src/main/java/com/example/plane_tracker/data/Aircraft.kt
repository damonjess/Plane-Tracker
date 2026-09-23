package com.example.plane_tracker.data

import org.json.JSONArray
import org.json.JSONObject

/** A single aircraft position report. */
data class Aircraft(
    val icao24: String,
    val callsign: String,
    val longitude: Double,
    val latitude: Double,
    val heading: Float,
    val altitudeMeters: Double,
    val velocityMps: Double,
    val verticalRateMps: Double,
    val onGround: Boolean,
    val squawk: String? = null,
    val registration: String? = null,
    val typeCode: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val altitudeFt: Int get() = (altitudeMeters * 3.28084).toInt()
    val speedKt: Int get() = (velocityMps * 1.94384).toInt()
    val speedKmh: Int get() = (velocityMps * 3.6).toInt()
    val climbFpm: Int get() = (verticalRateMps * 196.85).toInt()
}

/** Aggregated live state for the map layer. */
data class FleetState(
    val aircraft: List<Aircraft>,
    val source: String,
    val fetchedAt: Long = System.currentTimeMillis()
)

/** Detail record resolved from adsbdb. */
data class AircraftInfo(
    val hex: String,
    val registration: String?,
    val typeCode: String?,
    val typeDescription: String?,
    val registeredOwner: String?,
    val ownerCountry: String?,
    val manufacturer: String? = null,
    val photoUrl: String? = null
)

/** Origin/destination airports for a callsign. */
data class RouteInfo(
    val callsign: String,
    val airlineName: String?,
    val airlineIata: String?,
    val origin: Airport?,
    val destination: Airport?
) {
    val routeLabel: String?
        get() = if (origin?.iata != null && destination?.iata != null) {
            "${origin.iata} → ${destination.iata}"
        } else null
}

data class Airport(
    val name: String,
    val iata: String?,
    val icao: String?,
    val latitude: Double?,
    val longitude: Double?,
    val municipality: String? = null,
    val country: String? = null
)

/** Details for the selected-aircraft panel. */
data class SelectedFlight(
    val aircraft: Aircraft,
    val info: AircraftInfo? = null,
    val route: RouteInfo? = null,
    val photoUrl: String? = null
)

/** Parses an adsb.lol / tar1090-style aircraft object. */
fun parseAdsbAircraft(json: JSONObject): Aircraft? {
    val lat = json.optDouble("lat", Double.NaN)
    val lon = json.optDouble("lon", Double.NaN)
    if (lat.isNaN() || lon.isNaN()) return null
    val hex = json.optString("hex", "").trim()
    if (hex.isEmpty()) return null
    val headingVal = json.optDouble("track", json.optDouble("true_heading", 0.0))
    val altFtVal = json.optDouble("alt_baro", json.optDouble("alt_geom", 0.0))
    val gsVal = json.optDouble("gs", 0.0)
    val vRateVal = json.optDouble("baro_rate", json.optDouble("geom_rate", 0.0))
    return Aircraft(
        icao24 = hex,
        callsign = json.optString("flight", "").trim(),
        latitude = lat,
        longitude = lon,
        heading = if (headingVal.isNaN()) 0f else headingVal.toFloat(),
        altitudeMeters = if (altFtVal.isNaN() || altFtVal <= -1000.0) 0.0 else altFtVal * 0.3048, // ft -> m
        velocityMps = if (gsVal.isNaN()) 0.0 else gsVal * 0.514444, // knots -> m/s
        verticalRateMps = if (vRateVal.isNaN()) 0.0 else vRateVal * 0.00508,
        onGround = json.optString("alt_baro", "").equals("ground", ignoreCase = true),
        squawk = json.optStringOrNull("squawk"),
        registration = json.optStringOrNull("r"),
        typeCode = json.optStringOrNull("t")
    )
}

/** Safely extracts a Double from a JSONArray element, handling null/NaN properly. */
private fun JSONArray.optDoubleOrNull(index: Int): Double? {
    if (isNull(index)) return null
    val v = optDouble(index, Double.NaN)
    return if (v.isNaN()) null else v
}

/** Parses an OpenSky state vector array. */
fun parseOpenSkyState(state: JSONArray): Aircraft? {
    val lon = state.optDoubleOrNull(5)
    val lat = state.optDoubleOrNull(6)
    if (lon == null || lat == null) return null

    val headingVal = state.optDoubleOrNull(10) ?: 0.0
    val altMetersVal = state.optDoubleOrNull(13) ?: state.optDoubleOrNull(7) ?: 0.0
    val velocityVal = state.optDoubleOrNull(9) ?: 0.0
    val vRateVal = state.optDoubleOrNull(11) ?: 0.0
    val onGround = state.optBoolean(8, false)

    return Aircraft(
        icao24 = state.optString(0, "").trim(),
        callsign = state.optString(1, "").trim(),
        longitude = lon,
        latitude = lat,
        heading = headingVal.toFloat(),
        altitudeMeters = if (altMetersVal < 0) 0.0 else altMetersVal,
        velocityMps = velocityVal,
        verticalRateMps = vRateVal,
        onGround = onGround,
        squawk = state.optString(14, null)?.takeIf { it.isNotBlank() && it != "null" }
    )
}

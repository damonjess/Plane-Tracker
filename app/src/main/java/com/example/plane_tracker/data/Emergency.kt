package com.example.plane_tracker.data

/** A live emergency-squawk event detected in the fleet feed. */
data class EmergencyEvent(
    val hex: String,
    val callsign: String,
    val squawk: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val atMs: Long = System.currentTimeMillis(),
)

/** Detects the three ICAO emergency squawks in the live fleet. */
object EmergencyDetector {
    val SQUAWKS: Map<String, String> = mapOf(
        "7500" to "Hijack",
        "7600" to "Radio failure",
        "7700" to "General emergency",
    )

    fun isEmergency(squawk: String?): Boolean {
        val sq = squawk?.trim() ?: return false
        return SQUAWKS.containsKey(sq)
    }

    fun identify(aircraft: List<Aircraft>): List<EmergencyEvent> =
        aircraft.mapNotNull { ac ->
            if (ac.latitude.isNaN() || ac.longitude.isNaN()) return@mapNotNull null
            val sq = ac.squawk?.trim() ?: return@mapNotNull null
            val label = SQUAWKS[sq] ?: return@mapNotNull null
            EmergencyEvent(
                hex = ac.icao24,
                callsign = ac.callsign.trim().ifEmpty { ac.icao24.uppercase() },
                squawk = sq,
                label = label,
                latitude = ac.latitude,
                longitude = ac.longitude,
            )
        }
}


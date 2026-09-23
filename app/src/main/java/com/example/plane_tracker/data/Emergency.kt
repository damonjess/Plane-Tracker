package com.example.plane_tracker.data

/** A live emergency-squawk event detected in the fleet feed. */
data class EmergencyEvent(
    val hex: String,
    val callsign: String,
    val squawk: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val atMs: Long = System.currentTimeMillis()
)

/** Detects the three ICAO emergency squawks in the live fleet. */
object EmergencyDetector {
    val SQUAWKS: Map<String, String> = mapOf(
        "7500" to "Hijack",
        "7600" to "Radio failure",
        "7700" to "General emergency"
    )

    fun isEmergency(squawk: String?): Boolean = squawk != null && SQUAWKS.containsKey(squawk)

    fun identify(aircraft: List<Aircraft>): List<EmergencyEvent> =
        aircraft.filter { isEmergency(it.squawk) }.map { ac ->
            EmergencyEvent(
                hex = ac.icao24,
                callsign = ac.callsign.ifEmpty { ac.icao24.uppercase() },
                squawk = ac.squawk!!,
                label = SQUAWKS[ac.squawk!!] ?: "Emergency",
                latitude = ac.latitude,
                longitude = ac.longitude
            )
        }
}

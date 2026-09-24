package com.example.plane_tracker.data

import com.example.plane_tracker.util.GeoMath

data class Vessel(
    val mmsi: String,
    var name: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var speedKnots: Double = 0.0,
    var heading: Double = 0.0,
    var isLifeboat: Boolean = false,
    var lastSeen: Long = System.currentTimeMillis(),
    var callSign: String = "",
    var shipType: Int = 0,
    var navStatus: Int = 15,
    var destination: String = "",
    var draught: Double = 0.0,
    var lengthMeters: Int = 0,
    var widthMeters: Int = 0,
    var imoNumber: Int = 0
) {
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
            51 -> "Search and Rescue vessel"
            50 -> "Pilot Vessel"
            52 -> "Tug"
            53 -> "Port Tender"
            55 -> "Law Enforcement"
            else -> "Lifeboat"
        }
}

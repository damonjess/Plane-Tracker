package com.example.plane_tracker.data

/** Colors aircraft by altitude band, mirroring FR24's altitude color scale. */
object AltitudeColors {
    // Ascending bands: low -> high. Each entry is [maxAltitudeMeters, colorHex]
    val bands = listOf(
        0L to "#e04545",      // on ground / very low
        1500L to "#ff7b3d",   // departure climb
        3000L to "#ffb547",
        4500L to "#f9e14c",
        6000L to "#a3d949",
        7500L to "#38b24a",
        9000L to "#2fb98d",
        10500L to "#22a4c6",  // cruise band
        12500L to "#3b7bd6",
        Long.MAX_VALUE to "#7a5fd0" // very high
    )

    /** Returns the hex color for an altitude given in meters. */
    fun forAltitude(altitudeMeters: Double): String {
        val alt = if (altitudeMeters.isNaN() || altitudeMeters < 0) 0.0 else altitudeMeters
        return bands.firstOrNull { alt <= it.first }?.second ?: "#e04545"
    }
}

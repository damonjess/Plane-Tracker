package com.example.plane_tracker.data

import org.json.JSONObject

/** One past radar composite frame from RainViewer. */
data class RadarFrame(
    val timeMs: Long,
    val tileUrl: String,
)

/**
 * Parses RainViewer's public weather-maps.json into XYZ tile frame URLs.
 * Color scheme 2 (universal blue), smoothed with snow overlay.
 */
fun parseRadarMapsJson(body: String, size: Int = 256): List<RadarFrame> = try {
    val root = JSONObject(body)
    val host = (root.optStringOrNull("host") ?: "https://tilecache.rainviewer.com").trimEnd('/')
    val past = root.optJSONObject("radar")?.optJSONArray("past")
    if (past == null) emptyList() else {
        val frames = mutableListOf<RadarFrame>()
        for (i in 0 until past.length()) {
            val f = past.optJSONObject(i) ?: continue
            val rawPath = f.optString("path", "")
            val timeSec = f.optLong("time", 0L)
            if (rawPath.isEmpty() || (timeSec <= 0L)) continue
            val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            frames += RadarFrame(
                timeMs = timeSec * 1000L,
                tileUrl = "$host$path/$size/{z}/{x}/{y}/2/1_1.png",
            )
        }
        frames.sortBy { it.timeMs }
        frames
    }
} catch (_: Exception) {
    emptyList()
}


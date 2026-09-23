package com.example.plane_tracker.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/** Decoded METAR weather report for an airport. */
data class Metar(
    val icaoId: String,
    val rawOb: String,
    val reportTimeMs: Long?,
    val tempC: Double?,
    val dewpointC: Double?,
    /** Direction the wind blows FROM, degrees; null = variable/calm. */
    val windFromDeg: Int?,
    val windSpeedKt: Int?,
    val visibility: String?,
    val altimHpa: Int?,
) {
    /** Friendly one-word-ish condition derived from the raw report. */
    val condition: String get() = WeatherMapper.describe(rawOb)
}

/** Parses aviationweather.gov's JSON METAR array (takes the first report). */
fun parseMetarJson(body: String): Metar? = try {
    val arr = JSONArray(body)
    if (arr.length() == 0) null else {
        val o = arr.getJSONObject(0)
        Metar(
            icaoId = o.optString("icaoId", ""),
            rawOb = o.optString("rawOb", ""),
            reportTimeMs = o.optLong("obsTime", 0L).takeIf { it > 0L }?.times(1000L),
            tempC = o.optDoubleOrNull("temp"),
            dewpointC = o.optDoubleOrNull("dewp"),
            windFromDeg = o.optIntOrNull("wdir"),
            windSpeedKt = o.optIntOrNull("wspd"),
            visibility = o.optStringOrNull("visib"),
            altimHpa = o.optIntOrNull("altim")?.takeIf { it > 0 },
        )
    }
} catch (_: Exception) {
    null
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val v = optInt(key, Int.MIN_VALUE)
    return v.takeIf { it != Int.MIN_VALUE }
}

/** Maps raw METAR present-weather codes onto friendly descriptions. */
object WeatherMapper {
    private val codes = listOf(
        "+TS" to "Thunderstorm", "TSRA" to "Thunderstorm", "VCTS" to "Nearby storms", "TS" to "Thunderstorm",
        "+RA" to "Heavy rain", "RA" to "Rain", "-RA" to "Light rain", "SHRA" to "Rain showers",
        "SHSN" to "Snow showers", "+SN" to "Heavy snow", "SN" to "Snow", "-SN" to "Light snow",
        "DZ" to "Drizzle", "FZRA" to "Freezing rain", "GR" to "Hail",
        "BR" to "Mist", "FG" to "Fog", "HZ" to "Haze", "FU" to "Smoke", "SA" to "Sand",
        "DS" to "Duststorm", "SS" to "Sandstorm",
    )

    private val cloudPrefixes = listOf("FEW", "SCT", "BKN", "OVC", "VV")

    fun describe(rawOb: String): String {
        if (rawOb.isBlank()) return "Unknown"
        val rawTokens = rawOb.trim().split(Regex("\\s+"))

        // Stop processing at remarks (RMK)
        val rmkIdx = rawTokens.indexOfFirst { it == "RMK" }
        val tokens = if (rmkIdx != -1) rawTokens.subList(0, rmkIdx) else rawTokens

        // Filter out header, station ID, timestamps, wind, visibility, temp/dew, pressure, etc.
        val body = tokens.filterIndexed { index, token ->
            val clean = token.trim()
            val isHeader = clean in setOf("METAR", "SPECI", "AUTO", "COR", "NIL")
            val isTimestamp = clean.matches(Regex("\\d{6}Z"))
            val isWind = clean.matches(Regex("\\d{5}(G\\d+)?(KT|MPS|KMH)")) || clean.matches(Regex("\\d{3}V\\d{3}"))
            val isStationId = (index <= 1) && clean.matches(Regex("[A-Za-z0-9]{4}")) && !isHeader
            val isVis = clean.matches(Regex("(\\d{4}|\\d+SM|\\d+/\\d+SM|P?\\d+SM)"))
            val isTempDew = clean.matches(Regex("M?\\d+/M?\\d+"))
            val isPressure = clean.matches(Regex("[QA]\\d{4}"))

            !isHeader && !isTimestamp && !isWind && !isStationId && !isVis && !isTempDew && !isPressure
        }

        // 1) Strong present-weather codes (never match inside cloud groups).
        for (t in body) {
            if (cloudPrefixes.any { t.startsWith(it) }) continue
            codes.firstOrNull { (code, _) -> t.contains(code) }?.let { return it.second }
        }
        // 2) Explicit clear-sky groups.
        if (body.any { (it == "CAVOK") || (it == "SKC") || (it == "NCD") || (it == "NSC") || (it == "CLR") }) return "Clear"
        // 3) Otherwise decide by cloud cover.
        when {
            body.any { t -> listOf("BKN", "OVC", "VV").any { t.startsWith(it) } } -> return "Cloudy"
            body.any { t -> listOf("FEW", "SCT").any { t.startsWith(it) } } -> return "Partly cloudy"
        }
        // 4) Weak qualifier codes, exact-token only (VC/SH appear inside other strings).
        body.firstOrNull { it.trimStart('+', '-') in setOf("VC", "SH", "BL") }?.let {
            return when (it.trimStart('+', '-')) {
                "VC" -> "Nearby"
                "SH" -> "Showers"
                else -> "Blowing"
            }
        }
        return "Fair"
    }

    /** Cheap day/night hint: LocalTime-based, good enough for the moon/sun glyph. */
    fun isNight(): Boolean {
        val hour = LocalTime.now().hour
        return hour !in 6..19
    }

    /** Condition emoji: sun/moon plus weather glyph. */
    fun emoji(condition: String): String = when {
        condition == "Clear" -> if (isNight()) "🌙" else "☀️"
        condition.startsWith("Partly") -> if (isNight()) "☁️" else "⛅"
        condition == "Cloudy" -> "☁️"
        condition.contains("Thunder") -> "⛈️"
        condition.contains("Snow") -> "🌨️"
        condition.contains("rain", ignoreCase = true) || condition.contains("Drizzle") -> "🌧️"
        condition.contains("Fog") || condition.contains("Mist") -> "🌫️"
        condition.contains("Hail") -> "🌨️"
        else -> "🌤️"
    }
}

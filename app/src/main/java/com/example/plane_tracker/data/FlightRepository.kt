package com.example.plane_tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

suspend fun fetchGeoJsonFlights(): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    
    // 1. Try primary ADSB endpoint (adsb.lol community API with 250 NM radius around Scunthorpe/Humber)
    val adsbUrl = "https://api.adsb.lol/v2/point/53.58/-0.65/250"
    val request = Request.Builder()
        .url(adsbUrl)
        .header("User-Agent", "PlaneTrackerApp/1.0 (Android)")
        .build()

    try {
        val response = client.newCall(request).execute()
        val jsonStr = response.body?.string() ?: ""
        if (jsonStr.isNotEmpty()) {
            val json = JSONObject(jsonStr)
            val aircraftList = json.optJSONArray("ac")
            if (aircraftList != null && aircraftList.length() > 0) {
                val features = mutableListOf<String>()
                for (i in 0 until aircraftList.length()) {
                    val plane = aircraftList.getJSONObject(i)
                    if (plane.has("lat") && plane.has("lon") && !plane.isNull("lat") && !plane.isNull("lon")) {
                        var callsign = plane.optString("flight", "Unknown").trim()
                        if (callsign.isEmpty()) callsign = "Unknown"
                        val lat = plane.getDouble("lat")
                        val lon = plane.getDouble("lon")
                        val heading = plane.optDouble("track", 0.0)

                        features.add("""{"type":"Feature","properties":{"callsign":"$callsign","heading":$heading},"geometry":{"type":"Point","coordinates":[$lon,$lat]}}""")
                    }
                }
                if (features.isNotEmpty()) {
                    return@withContext """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Fallback to OpenSky Network if primary endpoint is unavailable
    val openSkyUrl = "https://opensky-network.org/api/states/all?lamin=45.0&lomin=-5.0&lamax=60.0&lomax=5.0"
    val openSkyRequest = Request.Builder()
        .url(openSkyUrl)
        .header("User-Agent", "PlaneTrackerApp/1.0 (Android)")
        .build()

    try {
        val response = client.newCall(openSkyRequest).execute()
        val jsonStr = response.body?.string() ?: return@withContext ""
        val states = JSONObject(jsonStr).optJSONArray("states") ?: return@withContext ""

        val features = mutableListOf<String>()
        for (i in 0 until states.length()) {
            val plane = states.getJSONArray(i)
            if (!plane.isNull(5) && !plane.isNull(6)) {
                var callsign = plane.optString(1, "Unknown").trim()
                if (callsign.isEmpty()) callsign = "Unknown"
                val lon = plane.getDouble(5)
                val lat = plane.getDouble(6)
                val heading = plane.optDouble(10, 0.0)

                features.add("""{"type":"Feature","properties":{"callsign":"$callsign","heading":$heading},"geometry":{"type":"Point","coordinates":[$lon,$lat]}}""")
            }
        }
        return@withContext """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return@withContext ""
}

class FlightRepository {
    suspend fun fetchGeoJsonFlights(): String = com.example.plane_tracker.data.fetchGeoJsonFlights()
}

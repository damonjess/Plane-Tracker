package com.example.plane_tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Network layer.
 *
 * Primary feed: adsb.lol v2 API (tar1090-style, rich data, no key).
 * Fallback feed: OpenSky Network state vectors.
 * Aircraft + route details: adsbdb.com (keyless public API).
 * Photos: planespotters.net pub API, adsbdb thumbnail as fallback.
 */
class FlightRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getBody(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "PlaneTrackerApp/2.0 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }

    /** Fetches the live fleet, trying adsb.lol first then OpenSky. */
    suspend fun fetchFleet(): FleetState = withContext(Dispatchers.IO) {
        fetchAdsbLol() ?: fetchOpenSky() ?: FleetState(emptyList(), "offline")
    }

    private fun fetchAdsbLol(): FleetState? {
        val body = getBody("https://api.adsb.lol/v2/point/53.5/-0.5/250") ?: return null
        val aircraftList = JSONObject(body).optJSONArray("ac") ?: return null
        val list = mutableListOf<Aircraft>()
        for (i in 0 until aircraftList.length()) {
            parseAdsbAircraft(aircraftList.getJSONObject(i))?.let { list.add(it) }
        }
        return if (list.isEmpty()) null else FleetState(list, "adsb.lol")
    }

    private fun fetchOpenSky(): FleetState? {
        val body = getBody(
            "https://opensky-network.org/api/states/all?lamin=45.0&lomin=-5.0&lamax=60.0&lomax=5.0"
        ) ?: return null
        val states = JSONObject(body).optJSONArray("states") ?: return null
        val list = mutableListOf<Aircraft>()
        for (i in 0 until states.length()) {
            parseOpenSkyState(states.getJSONArray(i))?.let { list.add(it) }
        }
        return if (list.isEmpty()) null else FleetState(list, "OpenSky")
    }

    /** Resolves registration, type and owner via adsbdb. */
    suspend fun fetchAircraftInfo(hex: String): AircraftInfo? = withContext(Dispatchers.IO) {
        if (hex.isBlank()) return@withContext null
        val body = getBody("https://api.adsbdb.com/v0/aircraft/$hex") ?: return@withContext null
        val response = JSONObject(body).optJSONObject("response") ?: return@withContext null
        val ac = response.optJSONObject("aircraft") ?: return@withContext null
        AircraftInfo(
            hex = hex,
            registration = ac.optStringOrNull("registration"),
            typeCode = ac.optStringOrNull("icao_type"),
            typeDescription = ac.optStringOrNull("type"),
            registeredOwner = ac.optStringOrNull("registered_owner"),
            ownerCountry = ac.optStringOrNull("registered_owner_country_name"),
            manufacturer = ac.optStringOrNull("manufacturer"),
            photoUrl = ac.optJSONObject("url_photo_thumbnail")?.optStringOrNull("src")
                ?: ac.optStringOrNull("url_photo_thumbnail")
        )
    }

    /** Resolves airline and origin/destination airports via adsbdb. */
    suspend fun fetchRoute(callsign: String): RouteInfo? = withContext(Dispatchers.IO) {
        if (callsign.isBlank()) return@withContext null
        val body = getBody("https://api.adsbdb.com/v0/callsign/$callsign")
            ?: return@withContext null
        val response = JSONObject(body).optJSONObject("response") ?: return@withContext null
        val routeJson = response.optJSONObject("flightroute") ?: return@withContext null

        fun airport(key: String): Airport? {
            val obj = routeJson.optJSONObject(key) ?: return null
            val iata = obj.optStringOrNull("iata_code")
            val icao = obj.optStringOrNull("icao_code")
            val fallbackEntry = Airports.byIata(iata) ?: Airports.byIcao(icao)
            val name = obj.optStringOrNull("name")
                ?: fallbackEntry?.name
                ?: iata
                ?: icao
                ?: return null
            return Airport(
                name = name,
                iata = iata ?: fallbackEntry?.iata,
                icao = icao ?: fallbackEntry?.icao,
                latitude = obj.optDoubleOrNull("latitude") ?: fallbackEntry?.lat,
                longitude = obj.optDoubleOrNull("longitude") ?: fallbackEntry?.lon,
                municipality = obj.optStringOrNull("municipality"),
                country = obj.optStringOrNull("country_name")
            )
        }

        RouteInfo(
            callsign = callsign,
            airlineName = routeJson.optJSONObject("airline")?.optStringOrNull("name"),
            airlineIata = routeJson.optJSONObject("airline")?.optStringOrNull("iata"),
            origin = airport("origin"),
            destination = airport("destination")
        )
    }

    /** Resolves an aircraft photo from planespotters.net. */
    suspend fun fetchPhotoUrl(hex: String): String? = withContext(Dispatchers.IO) {
        if (hex.isBlank()) return@withContext null
        val body = getBody("https://api.planespotters.net/pub/photos/hex/$hex")
            ?: return@withContext null
        val photos = JSONObject(body).optJSONArray("photos") ?: return@withContext null
        if (photos.length() == 0) return@withContext null
        val first = photos.getJSONObject(0)
        first.optJSONObject("thumbnail_large")?.optStringOrNull("src")
            ?: first.optJSONObject("thumbnail")?.optStringOrNull("src")
    }

    /** Fetches info, route and photo in parallel for the selected aircraft. */
    suspend fun fetchFlightDetails(aircraft: Aircraft): SelectedFlight =
        coroutineScope {
            val info = async { fetchAircraftInfo(aircraft.icao24) }
            val route = async { fetchRoute(aircraft.callsign) }
            val photo = async { fetchPhotoUrl(aircraft.icao24) }
            val infoResult = info.await()
            SelectedFlight(
                aircraft = aircraft,
                info = infoResult,
                route = route.await(),
                // Prefer the live planespotters photo; fall back to adsbdb's.
                photoUrl = photo.await() ?: infoResult?.photoUrl
            )
        }
}

// --- JSONObject helpers that treat missing/blank/"null" uniformly ---

fun JSONObject.optStringOrNull(key: String): String? {
    val value = optString(key, "").trim()
    return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return value.takeIf { !it.isNaN() }
}

package com.example.plane_tracker.data

import com.example.plane_tracker.util.GeoMath
import kotlin.math.roundToInt

enum class BoardKind { DEPARTURE, ARRIVAL }

/** One row on an airport arrivals/departures board. */
data class BoardEntry(
    val aircraft: Aircraft,
    val kind: BoardKind,
    val distanceKm: Int,
    val etaMinutes: Int?,
    val airlineName: String?,
    val routeLabel: String?,
)

/** Computed arrivals/departures boards for one airport. */
data class AirportBoard(
    val iata: String,
    val departures: List<BoardEntry>,
    val arrivals: List<BoardEntry>,
) {
    val total: Int get() = departures.size + arrivals.size
}

/**
 * Builds FR24-style airport boards from the live fleet + cached routes.
 * A plane counts as an arrival when its route destination is the airport,
 * and a departure when its route origin is.
 */
object AirportBoardBuilder {

    /** Only aircraft within this range of the airport are considered. */
    const val MAX_RADIUS_KM = 400.0

    /** Nearest airborne aircraft worth spending a route lookup on. */
    fun candidateCallsigns(
        airportLat: Double,
        airportLon: Double,
        aircraft: List<Aircraft>,
        max: Int = 30,
    ): List<String> = aircraft.asSequence()
        .filter { !it.onGround && it.callsign.isNotBlank() && !it.latitude.isNaN() && !it.longitude.isNaN() }
        .map { it to (GeoMath.distanceMeters(airportLat, airportLon, it.latitude, it.longitude) / 1000.0) }
        .filter { it.second <= MAX_RADIUS_KM }
        .sortedBy { it.second }
        .map { it.first.callsign.trim().uppercase() }
        .distinct()
        .take(max)
        .toList()

    fun build(
        airportIata: String,
        airportLat: Double,
        airportLon: Double,
        aircraft: List<Aircraft>,
        routesByCallsign: Map<String, RouteInfo?>,
    ): AirportBoard {
        val targetIata = airportIata.trim().uppercase()
        if (targetIata.isEmpty()) {
            return AirportBoard(airportIata, emptyList(), emptyList())
        }
        val entries = mutableListOf<BoardEntry>()
        for (ac in aircraft) {
            if (ac.onGround || ac.latitude.isNaN() || ac.longitude.isNaN()) continue
            val cs = ac.callsign.trim().uppercase()
            val route = routesByCallsign[cs] ?: continue
            val distKm = GeoMath.distanceMeters(airportLat, airportLon, ac.latitude, ac.longitude) / 1000.0
            if (distKm > MAX_RADIUS_KM) continue
            val destIata = route.destination?.iata?.trim()?.uppercase()
            val origIata = route.origin?.iata?.trim()?.uppercase()
            val kind = when {
                destIata == targetIata -> BoardKind.ARRIVAL
                origIata == targetIata -> BoardKind.DEPARTURE
                else -> continue
            }
            val eta = if ((kind == BoardKind.ARRIVAL) && (ac.velocityMps > 5.0) && !ac.velocityMps.isNaN()) {
                ((distKm / (ac.velocityMps * 3.6)) * 60.0).roundToInt()
            } else null
            entries += BoardEntry(
                aircraft = ac,
                kind = kind,
                distanceKm = distKm.roundToInt(),
                etaMinutes = eta,
                airlineName = route.airlineName,
                routeLabel = route.routeLabel,
            )
        }
        val sorted = entries.sortedWith(
            compareBy({ it.etaMinutes ?: Int.MAX_VALUE }, { it.distanceKm }),
        )
        return AirportBoard(
            airportIata,
            sorted.filter { it.kind == BoardKind.DEPARTURE },
            sorted.filter { it.kind == BoardKind.ARRIVAL },
        )
    }
}


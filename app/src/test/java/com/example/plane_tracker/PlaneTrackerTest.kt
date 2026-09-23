package com.example.plane_tracker

import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.data.Airport
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.data.AltitudeColors
import com.example.plane_tracker.data.AirportBoardBuilder
import com.example.plane_tracker.data.BoardKind
import com.example.plane_tracker.data.EmergencyDetector
import com.example.plane_tracker.data.RouteInfo
import com.example.plane_tracker.data.parseOpenSkyState
import com.example.plane_tracker.data.parseRadarMapsJson
import com.example.plane_tracker.util.GeoMath
import com.example.plane_tracker.util.RouteProgressCalculator
import com.example.plane_tracker.util.calculateClockETA
import java.time.LocalTime
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun `dead reckon north moves latitude only`() {
        val (lon, lat) = GeoMath.deadReckon(-0.5, 53.5, 0.0, 250.0, 60.0)
        assertEquals(-0.5, lon, 1e-9)
        assertEquals(53.5 + 250.0 * 60.0 / 111_320.0, lat, 1e-6)
    }

    @Test
    fun `dead reckon east moves longitude only`() {
        val (lon, lat) = GeoMath.deadReckon(-0.5, 53.5, 90.0, 250.0, 60.0)
        assertEquals(53.5, lat, 1e-9)
        assertTrue(lon > -0.5)
    }

    @Test
    fun `zero elapsed returns same position`() {
        val (lon, lat) = GeoMath.deadReckon(-0.5, 53.5, 45.0, 250.0, 0.0)
        assertEquals(-0.5, lon, 1e-12)
        assertEquals(53.5, lat, 1e-12)
    }

    @Test
    fun `haversine london to paris is about 344 km`() {
        val d = GeoMath.distanceMeters(51.5074, -0.1278, 48.8566, 2.3522)
        assertEquals(344_000.0, d, 5_000.0)
    }

    @Test
    fun `great circle path starts at origin and has correct length`() {
        val path = GeoMath.greatCirclePath(-0.5, 53.5, 45.0, 200.0)
        assertEquals(-0.5 to 53.5, path.first())
        assertEquals(6, path.size) // ~5 steps of 40km
    }

    @Test
    fun `calculateClockETA formats clock time and arriving now correctly`() {
        val now = LocalTime.of(21, 9)
        assertEquals("ETA 21:24", calculateClockETA(15, now))
        assertEquals("Arriving now", calculateClockETA(0, now))
        assertEquals("Arriving now", calculateClockETA(-5, now))
        assertEquals("ETA 22:09", calculateClockETA(60, now))
    }
}

class AltitudeColorsTest {

    @Test
    fun `ground color is included in allColors`() {
        assertEquals("#b0bec5", AltitudeColors.GROUND_COLOR)
        assertTrue(AltitudeColors.allColors.contains("#b0bec5"))
    }

    @Test
    fun `ground is red and low climb is orange`() {
        assertEquals("#e04545", AltitudeColors.forAltitude(0.0))
        assertEquals("#ff7b3d", AltitudeColors.forAltitude(1200.0))
    }

    @Test
    fun `cruise altitude is blue`() {
        assertEquals("#3b7bd6", AltitudeColors.forAltitude(11_000.0))
    }

    @Test
    fun `very high altitude is purple`() {
        assertEquals("#7a5fd0", AltitudeColors.forAltitude(13_500.0))
    }
    @Test
    fun `nan altitude returns default color without crashing`() {
        assertEquals("#e04545", AltitudeColors.forAltitude(Double.NaN))
    }
}

class AirportsTest {

    @Test
    fun `iata lookup works`() {
        assertEquals("London Heathrow", Airports.byIata("LHR")?.name)
    }

    @Test
    fun `search matches iata icao and name`() {
        assertTrue(Airports.search("heathrow").any { it.iata == "LHR" })
        assertTrue(Airports.search("MAN").any { it.iata == "MAN" })
        assertTrue(Airports.search("EGLL").any { it.iata == "LHR" })
    }
}

class ParsingTest {

    @Test
    fun `opensky state parses`() {
        val state = JSONArray(
            "[\"abc123\",\"TEST1 \",\"UK\",\"UK\",1612450000,-0.5,53.5,9000.0,false,0.0,270.0,0.0,null,9500.0,null,null]"
        )
        val ac = parseOpenSkyState(state)!!
        assertEquals("abc123", ac.icao24)
        assertEquals("TEST1", ac.callsign)
        assertEquals(-0.5, ac.longitude, 1e-9)
        assertEquals(53.5, ac.latitude, 1e-9)
        assertEquals(270f, ac.heading)
        assertEquals(9500.0, ac.altitudeMeters, 0.1)
    }

    @Test
    fun `opensky state with null altitudes parses safely without NaN`() {
        val state = JSONArray(
            "[\"abc123\",\"TEST1 \",\"UK\",\"UK\",1612450000,-0.5,53.5,null,false,0.0,270.0,0.0,null,null,null,null]"
        )
        val ac = parseOpenSkyState(state)!!
        assertEquals("abc123", ac.icao24)
        assertEquals(0.0, ac.altitudeMeters, 1e-9)
        assertEquals(0, ac.altitudeFt)
    }
}

class RouteProgressTest {

    private val origin = Airport("London Heathrow", "LHR", "EGLL", 51.4700, -0.4543, "London", "United Kingdom")
    private val destination = Airport("Dublin", "DUB", "EIDW", 53.4213, -6.2701, "Dublin", "Ireland")

    private fun aircraftAt(lat: Double, lon: Double, speedMps: Double = 220.0) = Aircraft(
        icao24 = "test", callsign = "TEST1", longitude = lon, latitude = lat,
        heading = 0f, altitudeMeters = 9000.0, velocityMps = speedMps,
        verticalRateMps = 0.0, onGround = false
    )

    @Test
    fun `midpoint aircraft is roughly half way`() {
        val midLat = (51.4700 + 53.4213) / 2
        val midLon = (-0.4543 + -6.2701) / 2
        val p = RouteProgressCalculator.compute(aircraftAt(midLat, midLon), origin, destination)!!
        assertEquals(0.5f, p.fraction, 0.02f)
        assertEquals(p.totalKm, p.flownKm + p.remainingKm, 20.0)
    }

    @Test
    fun `eta derived from ground speed and remaining distance`() {
        val p = RouteProgressCalculator.compute(
            aircraftAt(51.4700, -0.4543, speedMps = 200.0), origin, destination
        )!!
        val expectedMinutes = p.remainingKm * 1000.0 / 200.0 / 60.0
        assertEquals(expectedMinutes, p.etaMinutes!!.toDouble(), 1.0)
    }

    @Test
    fun `grounded or slow or NaN speed aircraft has no eta`() {
        val grounded = aircraftAt(51.4700, -0.4543).copy(onGround = true)
        assertNull(RouteProgressCalculator.compute(grounded, origin, destination)?.etaMinutes)
        val slow = aircraftAt(51.4700, -0.4543, speedMps = 5.0)
        assertNull(RouteProgressCalculator.compute(slow, origin, destination)?.etaMinutes)
        val nanSpeed = aircraftAt(51.4700, -0.4543, speedMps = Double.NaN)
        assertNull(RouteProgressCalculator.compute(nanSpeed, origin, destination)?.etaMinutes)
    }

    @Test
    fun `fraction clamps to 1 beyond destination`() {
        val beyond = RouteProgressCalculator.compute(aircraftAt(54.5, -7.0), origin, destination)!!
        assertEquals(1f, beyond.fraction)
    }

    @Test
    fun `missing airport coordinates yield null`() {
        val noCoords = Airport("Nowhere", "XXX", null, null, null)
        assertNull(RouteProgressCalculator.compute(aircraftAt(51.0, 0.0), noCoords, destination))
    }
}

class AircraftClimbRateTest {

    @Test
    fun `descending plane has climbFpm under minus 300`() {
        // -5 m/s vertical rate is approximately -984 ft/min
        val descending = Aircraft(
            icao24 = "abc123", callsign = "DESC1", longitude = -0.5, latitude = 53.5,
            heading = 180f, altitudeMeters = 3000.0, velocityMps = 150.0,
            verticalRateMps = -5.0, onGround = false
        )
        assertTrue(descending.climbFpm < -300)
    }

    @Test
    fun `ADS-B 64 fpm quantization wobble stays above minus 300 threshold`() {
        // ADS-B vertical rate is quantized in 64 fpm steps (~0.325 m/s)
        val wobble = Aircraft(
            icao24 = "abc124", callsign = "CRUISE1", longitude = -0.5, latitude = 53.5,
            heading = 180f, altitudeMeters = 10000.0, velocityMps = 240.0,
            verticalRateMps = -0.325, onGround = false // -64 fpm
        )
        assertTrue(wobble.climbFpm >= -300)

        val level = wobble.copy(verticalRateMps = 0.0)
        assertTrue(level.climbFpm >= -300)

        val climbing = wobble.copy(verticalRateMps = 8.0)
        assertTrue(climbing.climbFpm >= -300)
    }
}

class EmergencyDetectorTest {

    private fun ac(squawk: String?, hex: String = "hex1") = Aircraft(
        icao24 = hex, callsign = "TEST", longitude = -0.5, latitude = 53.5,
        heading = 0f, altitudeMeters = 9000.0, velocityMps = 200.0,
        verticalRateMps = 0.0, onGround = false, squawk = squawk
    )

    @Test
    fun `identifies all three emergency squawks`() {
        val events = EmergencyDetector.identify(
            listOf(ac("7700"), ac("7600", "hex2"), ac("7500", "hex3"))
        )
        assertEquals(3, events.size)
        assertEquals("General emergency", events[0].label)
        assertEquals("Radio failure", events[1].label)
        assertEquals("Hijack", events[2].label)
    }

    @Test
    fun `normal squawks are ignored`() {
        val events = EmergencyDetector.identify(
            listOf(ac("7700"), ac("1000", "h2"), ac("2000", "h3"), ac("7705", "h4"), ac(null, "h5")))
        assertEquals(1, events.size)
    }

    @Test
    fun `callsign falls back to hex when blank`() {
        val blank = ac("7700").copy(callsign = "")
        val events = EmergencyDetector.identify(listOf(blank))
        assertEquals("HEX1", events[0].callsign)
    }
}

class AirportBoardBuilderTest {

    private val ams = 52.3105 to 4.7683 // Amsterdam Schiphol

    private fun plane(lat: Double, lon: Double, callsign: String, speedMps: Double = 200.0) = Aircraft(
        icao24 = callsign.lowercase(), callsign = callsign, longitude = lon, latitude = lat,
        heading = 90f, altitudeMeters = 9000.0, velocityMps = speedMps,
        verticalRateMps = 0.0, onGround = false
    )

    private fun route(originIata: String?, destIata: String?) = RouteInfo(
        callsign = "TST1",
        airlineName = "Test Air",
        airlineIata = "TE",
        origin = originIata?.let { Airport("Origin", it, null, 51.0, 0.0) },
        destination = destIata?.let { Airport("Dest", it, null, 48.0, 2.0) }
    )

    @Test
    fun `classifies arrivals and departures by route endpoints`() {
        val routes = mapOf(
            "ARR1" to route("LHR", "AMS"),
            "DEP1" to route("AMS", "DXB"),
            "OTH1" to route("LHR", "DXB")
        )
        val board = AirportBoardBuilder.build(
            "AMS", ams.first, ams.second,
            listOf(plane(52.0, 4.5, "ARR1"), plane(52.5, 5.0, "DEP1"), plane(52.2, 4.2, "OTH1")),
            routes
        )
        assertEquals(1, board.arrivals.size)
        assertEquals(1, board.departures.size)
        assertEquals("ARR1", board.arrivals[0].aircraft.callsign)
        assertEquals("DEP1", board.departures[0].aircraft.callsign)
    }

    @Test
    fun `candidates are capped sorted by distance and airborne only`() {
        val grounded = plane(52.31, 4.77, "GND1").copy(onGround = true)
        val far = plane(49.5, 0.5, "FAR1") // ~433 km, beyond the 400 km radius
        val near = plane(52.2, 4.7, "NEAR1")
        val candidates = AirportBoardBuilder.candidateCallsigns(ams.first, ams.second, listOf(grounded, far, near))
        assertEquals(listOf("NEAR1"), candidates)
    }

    @Test
    fun `eta is derived from ground speed for arrivals and null for departures`() {
        val routes = mapOf(
            "ETA1" to route("LHR", "AMS"),
            "DEP1" to route("AMS", "DXB")
        )
        // ~98 km away at 200 m/s (720 km/h) -> ~8 minutes
        val board = AirportBoardBuilder.build(
            "ams", ams.first, ams.second,
            listOf(plane(51.5, 4.2, "ETA1  "), plane(52.2, 4.7, "DEP1")),
            routes
        )
        val arrival = board.arrivals[0]
        assertEquals(8, arrival.etaMinutes)
        val departure = board.departures[0]
        assertNull(departure.etaMinutes)
    }
}

class RadarParserTest {

    @Test
    fun `parses past radar frames into tile urls`() {
        val json = """{"host":"https://tilecache.rainviewer.com","radar":{"past":[
            {"time":1790191800,"path":"/v2/radar/aaa"},
            {"time":1790192400,"path":"/v2/radar/bbb"}
        ]}}"""
        val frames = parseRadarMapsJson(json)
        assertEquals(2, frames.size)
        assertEquals(1790191800000L, frames[0].timeMs)
        assertEquals(
            "https://tilecache.rainviewer.com/v2/radar/aaa/256/{z}/{x}/{y}/2/1_1.png",
            frames[0].tileUrl
        )
    }

    @Test
    fun `malformed json yields empty list`() {
        assertTrue(parseRadarMapsJson("not json").isEmpty())
    }
}

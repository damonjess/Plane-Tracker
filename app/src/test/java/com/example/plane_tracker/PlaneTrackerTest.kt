package com.example.plane_tracker

import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.data.Airport
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.data.AltitudeColors
import com.example.plane_tracker.data.parseOpenSkyState
import com.example.plane_tracker.util.GeoMath
import com.example.plane_tracker.util.RouteProgressCalculator
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
    fun `grounded or slow aircraft has no eta`() {
        val grounded = aircraftAt(51.4700, -0.4543).copy(onGround = true)
        assertNull(RouteProgressCalculator.compute(grounded, origin, destination)?.etaMinutes)
        val slow = aircraftAt(51.4700, -0.4543, speedMps = 5.0)
        assertNull(RouteProgressCalculator.compute(slow, origin, destination)?.etaMinutes)
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

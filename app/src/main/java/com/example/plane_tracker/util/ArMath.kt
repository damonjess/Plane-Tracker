package com.example.plane_tracker.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Projection math for the AR sky view: converts an aircraft's real-world
 * bearing/elevation relative to the observer into screen coordinates, using
 * the device's compass azimuth and camera pitch.
 *
 * All angles in degrees unless suffixed Rad. Pure functions, unit-testable.
 */
object ArMath {

    /**
     * Compass bearing from observer to target (degrees 0..360, 0 = North).
     */
    fun bearingDeg(
        myLat: Double, myLon: Double,
        targetLat: Double, targetLon: Double
    ): Double {
        val dLon = Math.toRadians(targetLon - myLon)
        val lat1 = Math.toRadians(myLat)
        val lat2 = Math.toRadians(targetLat)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    /**
     * Elevation angle of the target above the observer's horizon (degrees,
     * can be negative when the target is below eye level).
     */
    fun elevationDeg(horizontalDistanceMeters: Double, altitudeDeltaMeters: Double): Double {
        if (horizontalDistanceMeters <= 0.0) return if (altitudeDeltaMeters >= 0) 90.0 else -90.0
        return Math.toDegrees(
            atan2(altitudeDeltaMeters, horizontalDistanceMeters)
        )
    }

    data class ScreenPos(val x: Float, val y: Float, val onScreen: Boolean)

    /**
     * Projects a target at [bearingDeg]/[elevationDeg] onto screen space.
     *
     * @param deviceAzimuthDeg compass heading of the camera's view direction (0 = North).
     * @param devicePitchDeg camera pitch above horizon (0 = level, 90 = straight up).
     * @param hFovDeg camera horizontal field of view.
     * @param vFovDeg camera vertical field of view.
     */
    fun project(
        bearingDeg: Double,
        elevationDeg: Double,
        deviceAzimuthDeg: Double,
        devicePitchDeg: Double,
        hFovDeg: Double = 50.0,
        vFovDeg: Double = 65.0,
        screenW: Float = 1f,
        screenH: Float = 1f
    ): ScreenPos {
        // Angular offsets from the view centre, wrapped to [-180, 180].
        var dAz = bearingDeg - deviceAzimuthDeg
        while (dAz > 180.0) dAz -= 360.0
        while (dAz < -180.0) dAz += 360.0
        val dEl = elevationDeg - devicePitchDeg

        val halfH = hFovDeg / 2.0
        val halfV = vFovDeg / 2.0
        val onScreen = dAz in -halfH..halfH && dEl in -halfV..halfV

        // Normalised position within the view frustum (linear approximation —
        // stable for FOVs <= ~70 degrees and keeps labels from compressing near edges).
        val nx = (dAz / halfH + 1.0) / 2.0
        val ny = (dEl / halfV + 1.0) / 2.0
        return ScreenPos(
            x = (nx * screenW).toFloat(),
            y = ((1.0 - ny) * screenH).toFloat(),
            onScreen = onScreen
        )
    }

    /**
     * True when the target is close enough to the view edge to be worth
     * clamping with an edge arrow rather than hidden.
     */
    fun nearEdge(pos: ScreenPos, screenW: Float, screenH: Float, margin: Float = 0.06f): Boolean =
        pos.x < screenW * margin || pos.x > screenW * (1 - margin) ||
            pos.y < screenH * margin || pos.y > screenH * (1 - margin)
}

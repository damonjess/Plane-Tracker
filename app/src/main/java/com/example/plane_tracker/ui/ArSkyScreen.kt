package com.example.plane_tracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.util.ArMath
import com.example.plane_tracker.util.GeoMath
import kotlin.math.abs
import kotlin.math.roundToInt

private val ArPanelBg = Color(0xE6101014)
private val ArAccent = Color(0xFFF5B942)
private val ArText = Color(0xFFE8EEF2)
private val ArTextDim = Color(0xFF9AA7B4)

data class ArObserver(val lat: Double, val lon: Double)

private data class ArTarget(
    val ac: Aircraft,
    val x: Float,
    val y: Float,
    val distKm: Double
)

@Composable
fun ArSkyScreen(
    aircraft: List<Aircraft>,
    opsByHex: Map<String, String>,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkHasCamera(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun checkHasLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var hasCameraPermission by remember { mutableStateOf(checkHasCamera()) }
    var hasLocationPermission by remember { mutableStateOf(checkHasLocation()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        hasCameraPermission = resultMap[Manifest.permission.CAMERA] ?: checkHasCamera()
        hasLocationPermission = (resultMap[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (resultMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true) || checkHasLocation()
    }

    var observer by remember { mutableStateOf<ArObserver?>(null) }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }
    var selectedHex by remember { mutableStateOf<String?>(null) }

    // ---- Location Provider (GPS + Network fallback) ----
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Best effort last known location
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            try {
                lm.getLastKnownLocation(p)?.let {
                    if (observer == null) {
                        observer = ArObserver(it.latitude, it.longitude)
                    }
                }
            } catch (_: SecurityException) {
            }
        }

        val listener = LocationListener { loc ->
            observer = ArObserver(loc.latitude, loc.longitude)
        }

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, context.mainLooper)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener, context.mainLooper)
            }
        } catch (_: SecurityException) {
        }

        onDispose {
            try {
                lm.removeUpdates(listener)
            } catch (_: SecurityException) {
            }
        }
    }

    // ---- Device orientation (Rotation Vector or Accel + Mag with Portrait Remapping) ----
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotation = FloatArray(9)
        val remappedRotation = FloatArray(9)
        val inclination = FloatArray(9)
        val orientation = FloatArray(3)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)

        var lastAzimuth = 0f
        var lastPitch = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                var updateOrientation = false
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    updateOrientation = true
                } else {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, gravity, 0, 3)
                        Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomag, 0, 3)
                    }
                    if (SensorManager.getRotationMatrix(rotation, inclination, gravity, geomag)) {
                        updateOrientation = true
                    }
                }

                if (updateOrientation) {
                    // Remap axes for portrait camera view (X remains X, Y maps to Z)
                    SensorManager.remapCoordinateSystem(
                        rotation,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedRotation
                    )
                    SensorManager.getOrientation(remappedRotation, orientation)

                    val newAzimuth = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                    val newPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()

                    // Simple low-pass filter to prevent high-frequency jitter
                    val smoothedAzimuth = if (abs(newAzimuth - lastAzimuth) > 180f) newAzimuth else lastAzimuth + 0.2f * (newAzimuth - lastAzimuth)
                    val smoothedPitch = lastPitch + 0.2f * (newPitch - lastPitch)

                    lastAzimuth = (smoothedAzimuth + 360f) % 360f
                    lastPitch = smoothedPitch

                    // Throttle Compose state updates to changes >= 0.2 degrees
                    if (abs(lastAzimuth - azimuth) >= 0.2f || abs(lastPitch - pitch) >= 0.2f) {
                        azimuth = lastAzimuth
                        pitch = lastPitch
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotSensor != null) {
            sm.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            magSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }

        onDispose { sm.unregisterListener(listener) }
    }

    // ---- Projection: recompute targets on orientation/location/data change ----
    val obs = observer
    val density = LocalDensity.current
    val targets: List<ArTarget> = if (hasCameraPermission && obs != null && canvasSize.width > 0 && canvasSize.height > 0) {
        remember(aircraft, obs.lat, obs.lon, azimuth, pitch, canvasSize) {
            val hFov = 50.0
            val vFov = hFov * (canvasSize.height.toDouble() / canvasSize.width.toDouble()).coerceIn(1.0, 2.5)
            aircraft.mapNotNull { ac ->
                val horiz = GeoMath.distanceMeters(obs.lat, obs.lon, ac.latitude, ac.longitude)
                if (horiz > 120_000.0) return@mapNotNull null // beyond ~120 km, skip clutter
                val brg = ArMath.bearingDeg(obs.lat, obs.lon, ac.latitude, ac.longitude)
                val el = ArMath.elevationDeg(horiz, ac.altitudeMeters)
                val pos = ArMath.project(
                    brg, el, azimuth.toDouble(), pitch.toDouble(),
                    hFovDeg = hFov, vFovDeg = vFov,
                    screenW = canvasSize.width.toFloat(), screenH = canvasSize.height.toFloat()
                )
                if (pos.onScreen) ArTarget(ac, pos.x, pos.y, horiz / 1000.0) else null
            }
        }
    } else emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    canvasSize = size
                }
            }
    ) {
        // ---- Stream 1: Camera Feed ----
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✈", color = ArAccent, fontSize = 40.sp)
                Spacer(Modifier.size(12.dp))
                Text(
                    "Point your phone at the sky.\nCamera and location access overlay live aircraft\non the real view.",
                    color = ArTextDim,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(ArPanelBg, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )
                Spacer(Modifier.size(16.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) {
                    Text("Grant camera & location access")
                }
            }
        }

        // ---- Overlay: crosshair, target dots, plane labels ----
        if (hasCameraPermission && obs != null && targets.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    Color.White.copy(alpha = 0.22f),
                    radius = 26f,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 2f)
                )
                targets.forEach { t ->
                    drawCircle(ArAccent.copy(alpha = 0.9f), radius = 6f, center = Offset(t.x, t.y))
                }
            }

            // Farthest first so nearby planes' labels draw on top
            targets.sortedByDescending { it.distKm }.forEach { t ->
                val isSelected = selectedHex == t.ac.icao24
                val xDp = with(density) { t.x.toDp() }
                val yDp = with(density) { t.y.toDp() }

                Column(
                    modifier = Modifier
                        .offset(x = xDp - 70.dp, y = yDp - 20.dp)
                        .width(140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                if (isSelected) ArAccent else ArPanelBg,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedHex = if (isSelected) null else t.ac.icao24
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "✈",
                            color = if (isSelected) Color(0xFF101014) else ArAccent,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Text(
                                t.ac.callsign.ifEmpty { t.ac.icao24.uppercase() },
                                color = if (isSelected) Color(0xFF101014) else ArText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${t.ac.altitudeFt} ft · ${t.distKm.toInt()} km" +
                                    (opsByHex[t.ac.icao24]?.let { " · $it" } ?: ""),
                                color = if (isSelected) Color(0xFF3a2f10) else ArTextDim,
                                fontSize = 10.sp
                            )
                        }
                    }
                    if (isSelected) {
                        Text(
                            "tap again to open details",
                            color = Color(0xFF101014),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .background(ArAccent, RoundedCornerShape(8.dp))
                                .clickable { onSelect(t.ac.icao24) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // ---- Top readout: heading, pitch, GPS status + close button ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ArPanelBg)
            ) {
                Text(
                    text = "🧭 ${azimuth.roundToInt()}° · ⤴ ${pitch.roundToInt()}°" +
                        if (obs == null) " · waiting for location…" else "",
                    color = ArTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ArPanelBg),
                modifier = Modifier.clickable(onClick = onClose)
            ) {
                Text(
                    "✕ Close",
                    color = ArText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

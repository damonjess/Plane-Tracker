package com.example.plane_tracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 1. Data Model & Thread-Safe Memory
data class Flight(
    val icao24: String,
    val callsign: String,
    val longitude: Double,
    val latitude: Double,
    val heading: Float,
    val altitude: Double,
    val velocity: Double,
    var lastSeen: Long
)

val activeFlights = ConcurrentHashMap<String, Flight>()

data class AircraftDetails(
    val hex: String,
    val callsign: String,
    val altitude: String,
    val speed: String,
    val type: String = "Aircraft",
    val photoUrl: String? = null
)

// 2. Dead Reckoning Interpolation Math
fun calculateDeadReckoning(flight: Flight, currentTimeMs: Long): Pair<Double, Double> {
    // Determine exactly how many seconds have passed since the last API update
    val elapsedSeconds = (currentTimeMs - flight.lastSeen) / 1000.0
    
    // Stop animating if we lost signal for a minute, or if the plane is parked
    if (elapsedSeconds > 60.0 || flight.velocity == 0.0) {
        return Pair(flight.longitude, flight.latitude)
    }

    // Distance = Speed × Time
    val distanceMeters = flight.velocity * elapsedSeconds
    
    // Convert degrees to Radians for trigonometry
    val headingRad = flight.heading * (PI / 180.0)
    val latRad = flight.latitude * (PI / 180.0)
    
    // Calculate geographic scaling (Longitude shrinks as you move north/south)
    val metersPerLatDegree = 111320.0
    val metersPerLonDegree = 111320.0 * cos(latRad)

    // Calculate the micro-change in coordinates
    val deltaLat = (distanceMeters * cos(headingRad)) / metersPerLatDegree
    val deltaLon = (distanceMeters * sin(headingRad)) / metersPerLonDegree

    return Pair(flight.longitude + deltaLon, flight.latitude + deltaLat)
}

// 3. Network Fetcher (Updates memory silently, does not redraw UI)
suspend fun fetchFlightsFromApi() = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val url = "https://opensky-network.org/api/states/all?lamin=45.0&lomin=-5.0&lamax=60.0&lomax=5.0"
    val request = Request.Builder().url(url).build()

    try {
        val response = client.newCall(request).execute()
        val jsonStr = response.body?.string() ?: return@withContext
        val states = JSONObject(jsonStr).optJSONArray("states") ?: return@withContext

        val currentTime = System.currentTimeMillis()

        for (i in 0 until states.length()) {
            val plane = states.getJSONArray(i)
            if (!plane.isNull(5) && !plane.isNull(6)) {
                val icao24 = plane.optString(0)
                val callsign = plane.optString(1, "Unknown").trim()
                val lon = plane.getDouble(5)
                val lat = plane.getDouble(6)
                val heading = plane.optDouble(10, 0.0).toFloat()
                val altitude = plane.optDouble(7, 0.0)
                val velocity = plane.optDouble(9, 0.0)

                activeFlights[icao24] = Flight(icao24, callsign, lon, lat, heading, altitude, velocity, currentTime)
            }
        }
        
        // Remove stale planes to prevent memory leaks
        activeFlights.entries.removeIf { currentTime - it.value.lastSeen > 60000 }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 4. Fetch aircraft photo from Planespotters.net
suspend fun fetchPlanePhotoUrl(hex: String): String? = withContext(Dispatchers.IO) {
    if (hex.isEmpty()) return@withContext null
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://api.planespotters.net/pub/photos/hex/$hex")
        .header("User-Agent", "DamonPlaneTracker/1.0 (+https://github.com/example/planetracker)")
        .build()
    try {
        val response = client.newCall(request).execute()
        val jsonStr = response.body?.string() ?: return@withContext null
        val photos = JSONObject(jsonStr).optJSONArray("photos")
        if (photos != null && photos.length() > 0) {
            return@withContext photos.getJSONObject(0).getJSONObject("thumbnail_large").getString("src")
        }
    } catch (e: Exception) { e.printStackTrace() }
    return@withContext null
}

// 5. Draw Airplane Icon (Yellow to match standard trackers)
fun createPlaneBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = AndroidColor.parseColor("#FFD54F") 
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val path = Path().apply {
        moveTo(24f, 4f)
        lineTo(28f, 20f)
        lineTo(44f, 26f)
        lineTo(28f, 30f)
        lineTo(26f, 38f)
        lineTo(32f, 42f)
        lineTo(24f, 40f)
        lineTo(16f, 42f)
        lineTo(22f, 38f)
        lineTo(20f, 30f)
        lineTo(4f, 26f)
        lineTo(20f, 20f)
        close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}

// 6. Activity Setup
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent { PlaneTrackerScreen() }
    }
}

// 7. UI
@Composable
fun PlaneTrackerScreen() {
    var selectedAircraft by remember { mutableStateOf<AircraftDetails?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    // LOOP 1: Network Engine (Updates memory every 10 seconds)
    LaunchedEffect(Unit) {
        while (true) {
            fetchFlightsFromApi()
            delay(10000)
        }
    }

    // LOOP 2: Animation Engine (Calculates new frames 10 times a second)
    LaunchedEffect(Unit) {
        while (true) {
            val currentTime = System.currentTimeMillis()
            
            val features = activeFlights.values.map { flight ->
                val (interpLon, interpLat) = calculateDeadReckoning(flight, currentTime)
                """{"type":"Feature","properties":{"hex":"${flight.icao24}", "callsign":"${flight.callsign}", "heading":${flight.heading}, "altitude":${flight.altitude.toInt()}, "velocity":${flight.velocity.toInt()}},"geometry":{"type":"Point","coordinates":[$interpLon,$interpLat]}}"""
            }
            val liveGeoJson = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
            
            // Push directly to MapLibre GPU without causing Jetpack Compose to redraw
            mapView.getMapAsync { map ->
                map.style?.getSourceAs<GeoJsonSource>("planes-source")?.setGeoJson(liveGeoJson)
            }
            
            delay(100) // 100ms = 10 Frames Per Second
        }
    }

    // Fetch photo when a plane is tapped
    LaunchedEffect(selectedAircraft?.hex) {
        val hexCode = selectedAircraft?.hex
        if (hexCode.isNullOrEmpty()) return@LaunchedEffect
        
        val photoUrl = fetchPlanePhotoUrl(hexCode)
        if (photoUrl != null) {
            selectedAircraft = selectedAircraft?.copy(photoUrl = photoUrl)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { 
                mapView.apply {
                    getMapAsync { map ->
                        map.setStyle("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json") { style ->
                            style.addImage("plane-icon", createPlaneBitmap())
                            style.addSource(GeoJsonSource("planes-source", """{"type":"FeatureCollection","features":[]}"""))
                            
                            style.addLayer(
                                SymbolLayer("planes-layer", "planes-source")
                                    .withProperties(
                                        iconImage("plane-icon"),
                                        iconRotate(get("heading")),
                                        iconAllowOverlap(true),
                                        iconIgnorePlacement(true)
                                    )
                            )
                            
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(53.5, -0.5))
                                .zoom(8.0)
                                .build()
                        }

                        map.addOnMapClickListener { point ->
                            val screenLocation = map.projection.toScreenLocation(point)
                            val rect = RectF(
                                screenLocation.x - 30f, screenLocation.y - 30f,
                                screenLocation.x + 30f, screenLocation.y + 30f
                            )
                            val features = map.queryRenderedFeatures(rect, "planes-layer")
                            if (features.isNotEmpty()) {
                                val f = features[0]
                                val callsign = f.getStringProperty("callsign") ?: "Unknown"
                                val hex = f.getStringProperty("hex") ?: ""
                                val alt = f.getStringProperty("altitude") ?: "0"
                                val speed = f.getStringProperty("velocity") ?: "0"

                                selectedAircraft = AircraftDetails(
                                    hex = hex,
                                    callsign = callsign,
                                    altitude = "$alt m",
                                    speed = "$speed m/s",
                                    type = "Aircraft"
                                )
                                Toast.makeText(context, "Flight: $callsign", Toast.LENGTH_SHORT).show()
                                return@addOnMapClickListener true
                            } else {
                                selectedAircraft = null
                            }
                            false
                        }
                    }
                }
            }
        )

        // Overlay card showing aircraft details and photo
        selectedAircraft?.let { aircraft ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (aircraft.photoUrl != null) {
                        AsyncImage(
                            model = aircraft.photoUrl,
                            contentDescription = "Aircraft Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading photo...", color = Color.DarkGray)
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = aircraft.callsign.ifEmpty { "N/A" }, 
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = aircraft.type, 
                                style = MaterialTheme.typography.titleMedium, 
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Altitude: ${aircraft.altitude}")
                            Text(text = "Speed: ${aircraft.speed}")
                        }
                    }
                }
            }
        }
    }
}

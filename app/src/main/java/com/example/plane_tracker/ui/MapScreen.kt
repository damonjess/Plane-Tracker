package com.example.plane_tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.plane_tracker.data.fetchGeoJsonFlights
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

fun createPlaneBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(56, 56, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fillPaint = Paint().apply { color = AndroidColor.parseColor("#FFCC00"); style = Paint.Style.FILL; isAntiAlias = true }
    val strokePaint = Paint().apply { color = AndroidColor.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    val path = Path().apply {
        moveTo(28f, 2f); lineTo(32f, 16f); lineTo(54f, 26f); lineTo(32f, 32f); lineTo(30f, 44f); lineTo(40f, 50f)
        lineTo(28f, 48f); lineTo(16f, 50f); lineTo(26f, 44f); lineTo(24f, 32f); lineTo(2f, 26f); lineTo(24f, 16f); close()
    }
    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, strokePaint)
    return bitmap
}

@Composable
fun PlaneTrackerScreen() {
    var geoJsonData by remember { mutableStateOf("""{"type":"FeatureCollection","features":[]}""") }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    LaunchedEffect(Unit) {
        while (true) {
            val newData = fetchGeoJsonFlights()
            if (newData.isNotEmpty()) geoJsonData = newData
            delay(10000)
        }
    }

    LaunchedEffect(geoJsonData) {
        mapView.getMapAsync { map ->
            map.getStyle { style ->
                style.getSourceAs<GeoJsonSource>("planes-source")?.setGeoJson(geoJsonData)
            }
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

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { 
            mapView.apply {
                getMapAsync { map ->
                    // Using CARTO's Dark Matter theme to match your screenshot
                    map.setStyle("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json") { style ->
                        style.addImage("plane-icon", createPlaneBitmap())
                        val geoJsonSource = GeoJsonSource("planes-source", geoJsonData)
                        style.addSource(geoJsonSource)
                        geoJsonSource.setGeoJson(geoJsonData)
                        
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
                            .target(LatLng(53.5, -0.5)) // Centered near the Humber
                            .zoom(8.0)
                            .build()
                    }

                    // Add a click listener to show the callsign ONLY when the plane is tapped
                    map.addOnMapClickListener { point ->
                        val screenLocation = map.projection.toScreenLocation(point)
                        // Query the map specifically for planes at the tapped location
                        val features = map.queryRenderedFeatures(screenLocation, "planes-layer")
                        
                        if (features.isNotEmpty()) {
                            val callsign = features[0].getStringProperty("callsign")
                            Toast.makeText(context, "Flight: $callsign", Toast.LENGTH_SHORT).show()
                            return@addOnMapClickListener true
                        }
                        false
                    }
                }
            }
        }
    )
}

@Composable
fun MapScreen() {
    PlaneTrackerScreen()
}

package com.example.plane_tracker.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import androidx.core.graphics.ColorUtils
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.data.AltitudeColors
import com.example.plane_tracker.data.EmergencyEvent
import com.example.plane_tracker.data.MapFrame
import com.example.plane_tracker.data.RadarFrame
import com.example.plane_tracker.util.GeoMath
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/**
 * Owns the MapLibre map and all flight-related layers.
 * All public methods must be called from the main thread.
 */
class MapManager(context: Context) {

    companion object {
        const val DEFAULT_LAT = 53.5
        const val DEFAULT_LON = -0.5
        const val DEFAULT_ZOOM = 7.5
        private const val COURSE_LINE_KM = 220.0
        private val EMPTY_COLLECTION = FeatureCollection.fromFeatures(emptyList())
    }

    init {
        MapLibre.getInstance(context)
    }

    val mapView: MapView = MapView(context)

    private var map: MapLibreMap? = null
    private var styleReady = false
    private val iconIds = mutableSetOf<String>()
    /** Which ops badge images have been added to the style. */
    private val badgeIds = mutableSetOf<String>()

    private lateinit var planesSource: GeoJsonSource
    private lateinit var selectedSource: GeoJsonSource
    private lateinit var trailSource: GeoJsonSource
    private lateinit var courseSource: GeoJsonSource
    private lateinit var emergencySource: GeoJsonSource
    private var radarUrl: String? = null
    /** Ping-pong slot for smooth radar crossfades: 0 = layer-a, 1 = layer-b. */
    private var radarSlot = 1
    private var radarCleanup: Runnable? = null

    private var planeLayerIds: List<String> = emptyList()

    /** Called when a plane symbol is tapped (receives icao24 hex). */
    var onPlaneTapped: ((String) -> Unit)? = null
    /** Called when an airport dot is tapped (receives IATA code). */
    var onAirportTapped: ((String) -> Unit)? = null
    /** Called for taps on empty map area. */
    var onMapTapped: (() -> Unit)? = null
    /** Called when camera pitch/tilt changes (true = 3D tilted). */
    var onCameraTiltChanged: ((Boolean) -> Unit)? = null

    private val mainHandler = android.os.Handler(Looper.getMainLooper())
    private fun requireMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun setup() {
        requireMain {
            mapView.getMapAsync { map ->
                this.map = map
                map.uiSettings.isCompassEnabled = false
                map.setStyle("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json") { style ->
                    installLayers(style)
                    map.addOnMapClickListener { point ->
                        handleTap(point)
                        true
                    }
                    map.addOnCameraIdleListener {
                        val isTilted = (map.cameraPosition.tilt ?: 0.0) > 10.0
                        onCameraTiltChanged?.invoke(isTilted)
                    }
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(DEFAULT_LAT, DEFAULT_LON))
                        .zoom(DEFAULT_ZOOM)
                        .build()
                    styleReady = true
                }
            }
        }
    }

    private fun installLayers(style: Style) {
        // --- Sources ---
        planesSource = GeoJsonSource("planes-source")
        selectedSource = GeoJsonSource("selected-source")
        trailSource = GeoJsonSource("trail-source")
        courseSource = GeoJsonSource("course-source")
        val airportsSource = GeoJsonSource(
            "airports-source",
            FeatureCollection.fromFeatures(
                Airports.byCode.values.map { entry ->
                    val feature = Feature.fromGeometry(Point.fromLngLat(entry.lon, entry.lat))
                    feature.addStringProperty("iata", entry.iata)
                    feature
                }
            )
        )
        style.addSource(planesSource)
        style.addSource(courseSource)
        style.addSource(trailSource)
        style.addSource(selectedSource)
        style.addSource(airportsSource)
        emergencySource = GeoJsonSource("emergency-source")
        style.addSource(emergencySource)

        // --- Airport layers (bottom of stack, hidden by default) ---
        style.addLayer(
            CircleLayer("airports-circle", "airports-source")
                .withProperties(
                    PropertyFactory.circleColor("#ff8f3d"),
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.linear(), Expression.zoom(),
                            Expression.stop(4.0, 2.0f), Expression.stop(9.0, 4.0f)
                        )
                    ),
                    PropertyFactory.circleStrokeColor("#0b0f14"),
                    PropertyFactory.circleStrokeWidth(1.0f),
                    PropertyFactory.visibility(Property.NONE)
                )
        )
        style.addLayer(
            SymbolLayer("airport-labels", "airports-source")
                .withProperties(
                    PropertyFactory.textField(Expression.get("iata")),
                    PropertyFactory.textSize(10f),
                    PropertyFactory.textColor("#ffb98a"),
                    PropertyFactory.textHaloColor("#0b0f14"),
                    PropertyFactory.textHaloWidth(1.2f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.visibility(Property.NONE)
                )
        )

        // --- Course line (projected great-circle path ahead) ---
        style.addLayer(
            LineLayer("course-line", "course-source")
                .withProperties(
                    PropertyFactory.lineColor("#9fb3c8"),
                    PropertyFactory.lineWidth(1.5f),
                    PropertyFactory.lineDasharray(arrayOf(3f, 3f)),
                    PropertyFactory.lineOpacity(0.9f)
                )
        )

        // --- Flown trail ---
        style.addLayer(
            LineLayer("trail-line", "trail-source")
                .withProperties(
                    PropertyFactory.lineColor("#ffd54f"),
                    PropertyFactory.lineWidth(2.2f),
                    PropertyFactory.lineOpacity(0.9f)
                )
        )

        // --- Selected aircraft ring (under plane icon) ---
        style.addLayer(
            CircleLayer("selected-ring", "selected-source")
                .withProperties(
                    PropertyFactory.circleColor("rgba(255, 255, 255, 0.15)"),
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.linear(), Expression.zoom(),
                            Expression.stop(5.0, 10.0f), Expression.stop(10.0, 20.0f)
                        )
                    ),
                    PropertyFactory.circleStrokeColor("#ffffff"),
                    PropertyFactory.circleStrokeWidth(2.0f)
                )
        )

        // --- Emergency squawk pulse ring (below plane icons) ---
        style.addLayer(
            CircleLayer("emergency-pulse", "emergency-source")
                .withProperties(
                    PropertyFactory.circleColor("#ff2e2e"),
                    PropertyFactory.circleOpacity(0.30f),
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.linear(), Expression.zoom(),
                            Expression.stop(4.0, 9.0f), Expression.stop(10.0, 22.0f)
                        )
                    ),
                    PropertyFactory.circleStrokeColor("#ff5252"),
                    PropertyFactory.circleStrokeWidth(2.0f),
                    PropertyFactory.circleStrokeOpacity(0.9f)
                )
        )

        // --- Special-ops rings (coastguard / police / air ambulance / military) ---
        style.addLayer(
            CircleLayer("ops-ring", "planes-source")
                .withProperties(
                    PropertyFactory.circleColor(Expression.get("opsRing")),
                    PropertyFactory.circleOpacity(0.35f),
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.linear(), Expression.zoom(),
                            Expression.stop(4.0, 8.0f), Expression.stop(10.0, 18.0f)
                        )
                    ),
                    PropertyFactory.circleStrokeColor(Expression.get("opsRing")),
                    PropertyFactory.circleStrokeWidth(1.5f),
                    PropertyFactory.circleStrokeOpacity(0.85f)
                )
                .withFilter(Expression.has("opsRing"))
        )

        // --- Plane layers: one per altitude color band + ground state ---
        val ids = mutableListOf<String>()
        AltitudeColors.allColors.forEach { color ->
            ensureIcon(style, color)
            val id = "planes-$color"
            style.addLayer(
                SymbolLayer(id, "planes-source")
                    .withProperties(
                        PropertyFactory.iconImage("plane-$color"),
                        PropertyFactory.iconRotate(Expression.get("heading")),
                        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconSize(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(4.0, 0.5f),
                                Expression.stop(8.0, 0.7f),
                                Expression.stop(12.0, 0.95f)
                            )
                        )
                    )
                    .withFilter(
                        Expression.eq(Expression.get("color"), Expression.literal(color))
                    )
            )
            ids.add(id)
        }
        planeLayerIds = ids + "plane-labels"

        // --- Callsign labels (topmost) ---
        style.addLayer(
            SymbolLayer("plane-labels", "planes-source")
                .withProperties(
                    PropertyFactory.textField(Expression.get("callsign")),
                    PropertyFactory.textSize(10f),
                    PropertyFactory.textColor("#e8eef2"),
                    PropertyFactory.textHaloColor("#0b0f14"),
                    PropertyFactory.textHaloWidth(1.4f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textIgnorePlacement(true),
                    PropertyFactory.textOptional(true)
                )
                .withFilter(
                    Expression.neq(Expression.get("callsign"), Expression.literal(""))
                )
        )

        // --- Ops badges: category emoji next to classified aircraft ---
        com.example.plane_tracker.data.OpsCategory.entries.forEach { cat ->
            val badgeId = "badge-${cat.name}"
            style.addImage(badgeId, createBadgeBitmap(cat.emoji, cat.ringColor))
            badgeIds.add(badgeId)
        }
        style.addLayer(
            SymbolLayer("ops-badges", "planes-source")
                .withProperties(
                    PropertyFactory.iconImage(
                        Expression.concat(
                            Expression.literal("badge-"),
                            Expression.get("ops")
                        )
                    ),
                    PropertyFactory.iconSize(0.9f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconOffset(arrayOf(-1.4f, -1.4f)),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT)
                )
                .withFilter(Expression.has("ops"))
        )

        // --- Airport weather chips (IATA + condition, toggleable) ---
        style.addLayer(
            SymbolLayer("airport-wx", "airports-source")
                .withProperties(
                    PropertyFactory.textField(
                        Expression.concat(
                            Expression.get("iata"),
                            Expression.get("wxLabel")
                        )
                    ),
                    PropertyFactory.textSize(9f),
                    PropertyFactory.textColor("#a5d8ff"),
                    PropertyFactory.textHaloColor("#0b0f14"),
                    PropertyFactory.textHaloWidth(1.2f),
                    PropertyFactory.textOffset(arrayOf(0f, 2.2f)),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.visibility(Property.NONE)
                )
        )
    }

    private fun handleTap(point: LatLng) {
        val m = map ?: return
        val screen = m.projection.toScreenLocation(point)
        val rect = android.graphics.RectF(
            screen.x - 34f, screen.y - 34f, screen.x + 34f, screen.y + 34f
        )
        val features = m.queryRenderedFeatures(rect, *planeLayerIds.toTypedArray())
        if (features.isNotEmpty()) {
            val hex = features[0].getStringProperty("hex")
            if (!hex.isNullOrEmpty()) {
                onPlaneTapped?.invoke(hex)
                return
            }
        }
        // Airport dots (only tappable when the layer is visible).
        val airportsVisible = _airportsVisible
        if (airportsVisible) {
            val airportFeatures = m.queryRenderedFeatures(
                android.graphics.RectF(screen.x - 20f, screen.y - 20f, screen.x + 20f, screen.y + 20f),
                "airports-circle"
            )
            val iata = airportFeatures.firstOrNull()?.getStringProperty("iata")
            if (!iata.isNullOrEmpty()) {
                onAirportTapped?.invoke(iata)
                return
            }
        }
        onMapTapped?.invoke()
    }

    private fun ensureIcon(style: Style, color: String) {
        if (iconIds.contains(color)) return
        style.addImage("plane-$color", createPlaneBitmap(color))
        iconIds.add(color)
    }

    /** Applies a rendered map frame produced by the ViewModel. */
    fun applyFrame(frame: MapFrame) {
        requireMain {
            if (!styleReady) return@requireMain
            planesSource.setGeoJson(frame.planes)

            val sel = frame.selected
            if (sel == null) {
                selectedSource.setGeoJson(EMPTY_COLLECTION)
                trailSource.setGeoJson(EMPTY_COLLECTION)
                courseSource.setGeoJson(EMPTY_COLLECTION)
            } else {
                selectedSource.setGeoJson(
                    Feature.fromGeometry(Point.fromLngLat(sel.longitude, sel.latitude))
                )
                if (frame.trailCoordinates.size >= 2) {
                    trailSource.setGeoJson(LineString.fromLngLats(frame.trailCoordinates))
                } else {
                    trailSource.setGeoJson(EMPTY_COLLECTION)
                }
                if (sel.velocityMps > 1.0 && !sel.onGround) {
                    courseSource.setGeoJson(
                        LineString.fromLngLats(
                            GeoMath.greatCirclePath(
                                sel.longitude, sel.latitude,
                                sel.heading.toDouble(), COURSE_LINE_KM
                            ).map { Point.fromLngLat(it.first, it.second) }
                        )
                    )
                } else {
                    courseSource.setGeoJson(EMPTY_COLLECTION)
                }
            }

            frame.followPos?.let { follow ->
                val m = map ?: return@let
                val currentTilt = m.cameraPosition.tilt ?: 0.0
                if (currentTilt > 10.0 && frame.followHeading != null) {
                    val currentZoom = (m.cameraPosition.zoom ?: 13.0).coerceAtLeast(11.0)
                    val cameraPosition = CameraPosition.Builder()
                        .target(follow)
                        .zoom(currentZoom)
                        .tilt(currentTilt)
                        .bearing(frame.followHeading.toDouble())
                        .build()
                    m.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 200, false)
                } else {
                    m.easeCamera(CameraUpdateFactory.newLatLng(follow), 200, false)
                }
            }
        }
    }

    private var _airportsVisible = false

    fun setAirportsVisible(visible: Boolean) {
        _airportsVisible = visible
        setLayerVisible("airports-circle", visible)
        setLayerVisible("airport-labels", visible)
    }

    @Suppress("DEPRECATION")
    fun setPadding(leftPx: Int = 0, topPx: Int = 0, rightPx: Int = 0, bottomPx: Int = 0) {
        requireMain {
            map?.setPadding(leftPx, topPx, rightPx, bottomPx)
        }
    }

    /** Pushes emergency-squawk aircraft onto the map as pulsing red rings. */
    fun updateEmergencies(events: List<EmergencyEvent>) {
        requireMain {
            if (!styleReady) return@requireMain
            emergencySource.setGeoJson(
                FeatureCollection.fromFeatures(
                    events.map { e ->
                        val f = Feature.fromGeometry(Point.fromLngLat(e.longitude, e.latitude))
                        f.addStringProperty("hex", e.hex)
                        f
                    }
                )
            )
        }
    }

    /**
     * Swaps the radar overlay to a RainViewer tile URL using a two-slot
     * crossfade: the new frame is added in the other slot on top with a
     * raster fade-in while the old layer stays visible underneath, then the
     * old layer is removed once the fade completes. No blank flash between
     * frames.
     */
    fun updateRadarFrame(frame: RadarFrame?, visible: Boolean) {
        requireMain {
            val style = map?.style ?: return@requireMain
            if (frame == null) return@requireMain
            val visibility = if (visible) Property.VISIBLE else Property.NONE

            val slot = if (radarSlot == 0) 1 else 0
            val newSuffix = if (slot == 0) "a" else "b"
            val oldSuffix = if (slot == 0) "b" else "a"
            val srcId = "radar-source-$newSuffix"
            val layerId = "radar-layer-$newSuffix"

            // Same frame already shown and its layer still exists: just sync visibility.
            if (frame.tileUrl == radarUrl && style.getLayer(layerId) != null) {
                setRadarVisible(visible)
                return@requireMain
            }
            radarUrl = frame.tileUrl
            radarSlot = slot

            // Cancel any pending removal so it can't hit the recycled slot.
            radarCleanup?.let { mainHandler.removeCallbacks(it) }

            // (Re)create this slot's source with the new tiles.
            style.getLayer(layerId)?.let { style.removeLayer(it) }
            style.getSource(srcId)?.let { style.removeSource(it) }
            style.addSource(RasterSource(srcId, TileSet("2.1.0", frame.tileUrl), 256))

            val layer = RasterLayer(layerId, srcId).withProperties(
                PropertyFactory.rasterOpacity(0.55f),
                // Crossfade the new tiles in over the old layer as they load.
                PropertyFactory.rasterFadeDuration(600f),
                PropertyFactory.visibility(visibility)
            )
            // Inserting below airports-circle also stacks it above the old radar
            // layer (added earlier), so the fade reads as a true crossfade.
            if (style.getLayer("airports-circle") != null) {
                style.addLayerBelow(layer, "airports-circle")
            } else {
                style.addLayer(layer)
            }

            // Retire the previous slot after the fade has finished.
            radarCleanup = Runnable {
                style.getLayer("radar-layer-$oldSuffix")?.let { style.removeLayer(it) }
                style.getSource("radar-source-$oldSuffix")?.let { style.removeSource(it) }
            }.also { mainHandler.postDelayed(it, 900) }
        }
    }

    fun setRadarVisible(visible: Boolean) {
        requireMain {
            val visibility = if (visible) Property.VISIBLE else Property.NONE
            map?.style?.getLayer("radar-layer-a")?.setProperties(
                PropertyFactory.visibility(visibility)
            )
            map?.style?.getLayer("radar-layer-b")?.setProperties(
                PropertyFactory.visibility(visibility)
            )
        }
    }

    fun setLabelsVisible(visible: Boolean) = setLayerVisible("plane-labels", visible)

    /** Pushes airport weather labels ("MAN 9°C") onto the airport layer. */
    fun updateAirportWx(wx: Map<String, com.example.plane_tracker.data.Metar>) {
        requireMain {
            if (!styleReady) return@requireMain
            val features = Airports.byCode.values.map { entry ->
                val f = Feature.fromGeometry(Point.fromLngLat(entry.lon, entry.lat))
                f.addStringProperty("iata", entry.iata)
                val label = wx[entry.icao.uppercase()]?.tempC?.let { c -> " ${c.roundToInt()}°" } ?: ""
                f.addStringProperty("wxLabel", label)
                f
            }
            (map?.style?.getSource("airports-source") as? GeoJsonSource)?.setGeoJson(
                FeatureCollection.fromFeatures(features)
            )
        }
    }

    fun setAirportWxVisible(visible: Boolean) {
        setLayerVisible("airport-wx", visible)
    }

    /** Draws the replay path + moving plane position. */
    fun updateReplay(path: LineString?, position: Point?) {
        requireMain {
            if (!styleReady) return@requireMain
            val style = map?.style ?: return@requireMain
            if (style.getSource("replay-source") == null) {
                style.addSource(GeoJsonSource("replay-source"))
                style.addLayerBelow(
                    LineLayer("replay-line", "replay-source")
                        .withProperties(
                            PropertyFactory.lineColor("#4dd0e1"),
                            PropertyFactory.lineWidth(3f),
                            PropertyFactory.lineOpacity(0.9f)
                        ),
                    "selected-ring"
                )
                style.addLayer(
                    CircleLayer("replay-dot", "replay-source")
                        .withProperties(
                            PropertyFactory.circleColor("#4dd0e1"),
                            PropertyFactory.circleRadius(6f),
                            PropertyFactory.circleStrokeColor("#ffffff"),
                            PropertyFactory.circleStrokeWidth(2f)
                        )
                        .withFilter(Expression.eq(
                            Expression.geometryType(), Expression.literal("Point")
                        ))
                )
            }
            val src = style.getSource("replay-source") as? GeoJsonSource ?: return@requireMain
            val features = mutableListOf<Feature>()
            if (path != null) features.add(Feature.fromGeometry(path))
            if (position != null) features.add(Feature.fromGeometry(position))
            src.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    fun setTrailVisible(visible: Boolean) {
        setLayerVisible("trail-line", visible)
        setLayerVisible("course-line", visible)
    }

    private fun setLayerVisible(layerId: String, visible: Boolean) {
        map?.style?.getLayer(layerId)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
        )
    }

    fun centerOnDefault() {
        requireMain {
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(DEFAULT_LAT, DEFAULT_LON))
                .zoom(DEFAULT_ZOOM)
                .tilt(0.0)
                .bearing(0.0)
                .build()
            map?.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                600, null
            )
            onCameraTiltChanged?.invoke(false)
        }
    }

    fun flyTo(latitude: Double, longitude: Double, zoom: Double = 10.5) {
        requireMain {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom), 900, null
            )
        }
    }

    fun flyTo3D(latitude: Double, longitude: Double, heading: Float, zoom: Double = 13.0) {
        requireMain {
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(latitude, longitude))
                .zoom(zoom)
                .tilt(60.0)      // Tilts the camera into a 3D perspective
                .bearing(heading.toDouble()) // Rotates the map to match the plane's heading
                .build()

            map?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1200, null)
            onCameraTiltChanged?.invoke(true)
        }
    }

    fun set2D() {
        requireMain {
            val m = map ?: return@requireMain
            val target = m.cameraPosition.target ?: LatLng(DEFAULT_LAT, DEFAULT_LON)
            val zoom = m.cameraPosition.zoom ?: DEFAULT_ZOOM
            val cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .tilt(0.0)
                .bearing(0.0)
                .build()
            m.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 600, null)
            onCameraTiltChanged?.invoke(false)
        }
    }

    fun set3D(heading: Double = map?.cameraPosition?.bearing ?: 0.0) {
        requireMain {
            val m = map ?: return@requireMain
            val target = m.cameraPosition.target ?: LatLng(DEFAULT_LAT, DEFAULT_LON)
            val zoom = m.cameraPosition.zoom.coerceAtLeast(11.0)
            val cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .tilt(60.0)
                .bearing(heading)
                .build()
            m.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 800, null)
            onCameraTiltChanged?.invoke(true)
        }
    }

    fun toggle2D3D(heading: Float? = null) {
        val currentTilt = map?.cameraPosition?.tilt ?: 0.0
        if (currentTilt > 10.0) {
            set2D()
        } else {
            set3D(heading?.toDouble() ?: map?.cameraPosition?.bearing ?: 0.0)
        }
    }

    fun zoomBy(delta: Double) {
        val m = map ?: return
        val target = (m.cameraPosition.zoom ?: DEFAULT_ZOOM) + delta
        map?.animateCamera(CameraUpdateFactory.zoomTo(target.coerceIn(2.0, 16.0)), 300, null)
    }

    /** Forwards Android lifecycle events to the MapView. */
    fun forwardLifecycle(event: LifecycleEvent) {
        when (event) {
            LifecycleEvent.START -> mapView.onStart()
            LifecycleEvent.RESUME -> mapView.onResume()
            LifecycleEvent.PAUSE -> mapView.onPause()
            LifecycleEvent.STOP -> mapView.onStop()
            LifecycleEvent.DESTROY -> mapView.onDestroy()
        }
    }

    enum class LifecycleEvent { START, RESUME, PAUSE, STOP, DESTROY }
}

/** Renders a small rounded badge with an emoji + colored ring. */
fun createBadgeBitmap(emoji: String, ringColor: String): Bitmap {
    val size = 44
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val ring = Paint().apply {
        color = AndroidColor.parseColor(ringColor)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    val bg = Paint().apply {
        color = AndroidColor.parseColor("#EE10141A")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, bg)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, ring)
    val text = Paint().apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
        isAntiAlias = true
    }
    val y = size / 2f - (text.descent() + text.ascent()) / 2f
    canvas.drawText(emoji, size / 2f, y, text)
    return bitmap
}

/** Renders the FR24-style plane silhouette in the given color. */
fun createPlaneBitmap(colorHex: String): Bitmap {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint().apply {
        color = AndroidColor.parseColor(colorHex)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val stroke = Paint().apply {
        color = ColorUtils.blendARGB(AndroidColor.parseColor(colorHex), AndroidColor.BLACK, 0.55f)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path().apply {
        moveTo(28f, 2f); lineTo(32f, 16f); lineTo(54f, 26f); lineTo(32f, 32f)
        lineTo(30f, 44f); lineTo(40f, 50f); lineTo(28f, 48f); lineTo(16f, 50f)
        lineTo(26f, 44f); lineTo(24f, 32f); lineTo(2f, 26f); lineTo(24f, 16f)
        close()
    }
    canvas.drawPath(path, fill)
    canvas.drawPath(path, stroke)
    return bitmap
}

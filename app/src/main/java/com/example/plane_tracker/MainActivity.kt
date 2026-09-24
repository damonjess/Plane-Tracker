package com.example.plane_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.map.MapManager
import com.example.plane_tracker.ui.AirportSheet
import com.example.plane_tracker.ui.AlertHistorySheet
import com.example.plane_tracker.ui.EmergencyBanner
import com.example.plane_tracker.ui.OpsSheet
import com.example.plane_tracker.ui.PlaybackSheet
import com.example.plane_tracker.ui.FlightDetailsPanel
import com.example.plane_tracker.ui.LifeboatDetailsPanel
import com.example.plane_tracker.ui.FilterSheet
import com.example.plane_tracker.ui.FollowingChip
import com.example.plane_tracker.ui.MapControls
import com.example.plane_tracker.ui.RadarOverlay
import com.example.plane_tracker.ui.SearchOverlay
import com.example.plane_tracker.ui.StatusChip
import com.example.plane_tracker.ui.theme.PlaneTrackerTheme
import com.example.plane_tracker.viewmodel.FlightViewModel
import org.maplibre.android.MapLibre
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        setContent {
            PlaneTrackerTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackerScreen()
                }
            }
        }
    }
}

@Composable
fun TrackerScreen(viewModel: FlightViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapManager = remember { MapManager(context) }
    var filtersOpen by remember { mutableStateOf(false) }
    var is3D by remember { mutableStateOf(false) }
    var radarIndex by remember { mutableIntStateOf(0) }
    var opsOpen by remember { mutableStateOf(false) }
    var alertHistoryOpen by remember { mutableStateOf(false) }
    var arOpen by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    // Map setup once
    LaunchedEffect(mapManager) { mapManager.setup() }

    // Map callbacks
    LaunchedEffect(mapManager) {
        viewModel.onAutoTiltTo3D = { lat, lon, heading ->
            mapManager.flyTo3D(lat, lon, heading)
        }
        mapManager.onCameraTiltChanged = { tilted -> is3D = tilted }
        mapManager.onPlaneTapped = { hex ->
            viewModel.selectAircraft(hex) { lat, lon, heading ->
                mapManager.flyTo3D(lat, lon, heading)
            }
        }
        mapManager.onMapTapped = { viewModel.clearSelection() }
        mapManager.onAirportTapped = { iata ->
            Airports.byIata(iata)?.let { entry ->
                viewModel.openAirportPage(entry)
            }
        }
        mapManager.onVesselTapped = { mmsi ->
            viewModel.selectLifeboat(mmsi)
            viewModel.uiState.value.selectedLifeboat?.let { lb ->
                mapManager.flyTo(lb.latitude, lb.longitude, 11.0)
            }
        }
    }

    // Push render frames to the map
    LaunchedEffect(mapManager) {
        viewModel.mapFrame.collect { frame ->
            mapManager.applyFrame(frame)
        }
    }

    // Apply layer visibility when filter flags change
    LaunchedEffect(uiState.filters.showAirports, uiState.filters.showLabels) {
        mapManager.setAirportsVisible(uiState.filters.showAirports)
        mapManager.setLabelsVisible(uiState.filters.showLabels)
    }

    // Airport weather badges: push data + visibility
    LaunchedEffect(uiState.airportWx) {
        mapManager.updateAirportWx(uiState.airportWx)
    }
    LaunchedEffect(uiState.showAirportWx) {
        mapManager.setAirportWxVisible(uiState.showAirportWx)
    }

    // Apply camera padding so selected aircraft center in open map space above details card
    val density = LocalDensity.current
    LaunchedEffect(uiState.selected != null, uiState.selectedLifeboat != null, uiState.isFollowing) {
        if ((uiState.selected != null || uiState.selectedLifeboat != null) && !uiState.isFollowing) {
            val topPx = with(density) { 90.dp.roundToPx() }
            val bottomPx = with(density) { 360.dp.roundToPx() }
            mapManager.setPadding(0, topPx, 0, bottomPx)
        } else {
            mapManager.setPadding(0, 0, 0, 0)
        }
    }

    // Lifecycle forwarding
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapManager.forwardLifecycle(MapManager.LifecycleEvent.START)
                Lifecycle.Event.ON_RESUME -> mapManager.forwardLifecycle(MapManager.LifecycleEvent.RESUME)
                Lifecycle.Event.ON_PAUSE -> mapManager.forwardLifecycle(MapManager.LifecycleEvent.PAUSE)
                Lifecycle.Event.ON_STOP -> mapManager.forwardLifecycle(MapManager.LifecycleEvent.STOP)
                Lifecycle.Event.ON_DESTROY -> mapManager.forwardLifecycle(MapManager.LifecycleEvent.DESTROY)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The map
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapManager.mapView }
        )

        // Top: search + status bar safe area
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Emergency squawk alerts (7700/7600/7500)
            EmergencyBanner(
                emergencies = uiState.emergencies,
                onSelect = { e ->
                    mapManager.flyTo(e.latitude, e.longitude, 9.5)
                    viewModel.selectAircraft(e.hex)
                },
                onDismiss = viewModel::dismissEmergency,
                modifier = Modifier.padding(top = 6.dp)
            )

            SearchOverlay(
                query = uiState.searchQuery,
                aircraftResults = uiState.searchResults,
                airportResults = remember(uiState.searchQuery) {
                    Airports.search(uiState.searchQuery)
                },
                lifeboatResults = uiState.lifeboatSearchResults,
                onQueryChange = viewModel::updateSearch,
                onAircraftClick = { ac ->
                    viewModel.focusSearchResult(
                        ac,
                        onFocused = { lat, lon -> mapManager.flyTo(lat, lon) },
                        onFlyTo3D = { lat, lon, heading -> mapManager.flyTo3D(lat, lon, heading) }
                    )
                },
                onAirportClick = { ap ->
                    mapManager.flyTo(ap.lat, ap.lon, 11.0)
                    viewModel.openAirportPage(ap)
                    viewModel.updateSearch("")
                },
                onLifeboatClick = { lb ->
                    viewModel.focusLifeboatResult(
                        lb,
                        onFocused = { lat, lon -> mapManager.flyTo(lat, lon, 11.0) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    count = uiState.aircraftCount,
                    source = uiState.source,
                    lastUpdateMs = uiState.lastUpdateMs,
                    lifeboatCount = uiState.lifeboats.size
                )

                if (uiState.isFollowing) {
                    FollowingChip(
                        onCancel = viewModel::toggleFollow
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            RadarOverlay(
                radarOn = uiState.radarOn,
                radarPlaying = uiState.radarPlaying,
                frames = uiState.radarFrames,
                currentIndex = radarIndex,
                onTogglePlay = viewModel::toggleRadarPlaying
            )
        }

        // Rain radar: keep the map's raster frame in sync with the UI timeline
        LaunchedEffect(uiState.radarFrames, radarIndex, uiState.radarOn) {
            (uiState.radarFrames.getOrNull(radarIndex) ?: uiState.radarFrames.lastOrNull())?.let { frame ->
                mapManager.updateRadarFrame(frame, visible = uiState.radarOn)
            }
        }
        LaunchedEffect(uiState.radarOn, uiState.radarFrames) {
            mapManager.setRadarVisible(uiState.radarOn)
            if (uiState.radarOn && uiState.radarFrames.isNotEmpty()) {
                radarIndex = uiState.radarFrames.lastIndex
            }
        }
        // Animate the radar timeline when playing
        LaunchedEffect(uiState.radarOn, uiState.radarPlaying, uiState.radarFrames.size) {
            if (!uiState.radarOn || !uiState.radarPlaying || uiState.radarFrames.isEmpty()) {
                return@LaunchedEffect
            }
            while (true) {
                radarIndex = (radarIndex + 1) % uiState.radarFrames.size
                delay(1200)
            }
        }

        // Push emergency positions to their map pulse layer
        LaunchedEffect(uiState.emergencies) {
            mapManager.updateEmergencies(uiState.emergencies)
        }

        // Push AIS vessels to map
        LaunchedEffect(uiState.lifeboats) {
            mapManager.updateVessels(uiState.lifeboats)
        }

        // Sync flight replay track and active position to the map
        LaunchedEffect(uiState.replayFlight, uiState.replayPoints, uiState.replayIndex) {
            val flight = uiState.replayFlight
            val points = uiState.replayPoints
            val index = uiState.replayIndex
            if (flight != null && points.isNotEmpty() && index in points.indices) {
                val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
                val p = points[index]
                val pt = Point.fromLngLat(p.longitude, p.latitude)
                mapManager.updateReplay(line, pt)
            } else {
                mapManager.updateReplay(null, null)
            }
        }

        // Right-side controls
        MapControls(
            airportsOn = uiState.filters.showAirports,
            labelsOn = uiState.filters.showLabels,
            is3D = is3D,
            radarOn = uiState.radarOn,
            onZoomIn = { mapManager.zoomBy(+1.5) },
            onZoomOut = { mapManager.zoomBy(-1.5) },
            onCenter = mapManager::centerOnDefault,
            onToggle3D = { mapManager.toggle2D3D(uiState.selected?.aircraft?.heading) },
            onToggleAirports = viewModel::toggleAirports,
            onToggleLabels = viewModel::toggleLabels,
            onToggleRadar = viewModel::toggleRadar,
            onOpenOps = { opsOpen = true },
            onOpenAlerts = { alertHistoryOpen = true },
            onOpenAr = { arOpen = true },
            onOpenFilters = { filtersOpen = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )

        // Bottom: flight details (hidden when follow mode is active)
        if (!uiState.isFollowing) {
            uiState.selected?.let { selected ->
                FlightDetailsPanel(
                    selected = selected,
                    routeProgress = uiState.routeProgress,
                    isLoading = uiState.isLoadingDetails,
                    isFollowing = uiState.isFollowing,
                    onClose = viewModel::clearSelection,
                    onToggleFollow = viewModel::toggleFollow,
                    onReplay = {
                        uiState.selected?.aircraft?.let { ac ->
                            viewModel.openReplay(ac.icao24)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            uiState.selectedLifeboat?.let { selectedLifeboat ->
                val metar = remember(selectedLifeboat.mmsi, selectedLifeboat.latitude, selectedLifeboat.longitude, uiState.airportWx) {
                    Airports.findNearestMetar(selectedLifeboat.latitude, selectedLifeboat.longitude, uiState.airportWx)
                }
                LifeboatDetailsPanel(
                    vessel = selectedLifeboat,
                    metar = metar,
                    onClose = viewModel::clearSelection,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // AR sky view (full-screen camera overlay)
        if (arOpen) {
            com.example.plane_tracker.ui.ArSkyScreen(
                aircraft = remember(uiState.aircraftCount, uiState.lastUpdateMs) {
                    viewModel.aircraftForAr()
                },
                opsByHex = remember(uiState.opsAircraft) { viewModel.opsLabelsForAr() },
                onSelect = { hex ->
                    arOpen = false
                    viewModel.selectAircraft(hex)
                },
                onClose = { arOpen = false }
            )
        }

        // Filter bottom sheet
        FilterSheet(
            visible = filtersOpen,
            filters = uiState.filters,
            showAirportWx = uiState.showAirportWx,
            onToggleAirportWx = viewModel::toggleAirportWx,
            onChange = viewModel::setFilters,
            onDismiss = { filtersOpen = false }
        )

        // Emergency services & military list
        if (opsOpen) {
            OpsSheet(
                ops = uiState.opsAircraft,
                lifeboats = uiState.lifeboats,
                ready = uiState.opsReady,
                onSelect = { ac ->
                    opsOpen = false
                    viewModel.focusSearchResult(
                        ac,
                        onFocused = { lat, lon -> mapManager.flyTo(lat, lon, 11.0) }
                    )
                },
                onSelectLifeboat = { lb ->
                    opsOpen = false
                    viewModel.focusLifeboatResult(
                        lb,
                        onFocused = { lat, lon -> mapManager.flyTo(lat, lon, 11.0) }
                    )
                },
                onClose = { opsOpen = false }
            )
        }

        // Squawk history (reviewable session log)
        if (alertHistoryOpen) {
            AlertHistorySheet(
                entries = uiState.alertHistory,
                onSelect = { e ->
                    alertHistoryOpen = false
                    mapManager.flyTo(e.latitude, e.longitude, 10.0)
                    viewModel.selectAircraft(e.hex)
                },
                onClose = { alertHistoryOpen = false }
            )
        }

        // Flight history playback
        PlaybackSheet(
            flight = uiState.replayFlight,
            points = uiState.replayPoints,
            index = uiState.replayIndex,
            playing = uiState.replayPlaying,
            onSeek = viewModel::seekReplay,
            onPlay = viewModel::playReplay,
            onPause = viewModel::pauseReplay,
            onClose = viewModel::closeReplay
        )

        // FR24-style airport page (weather + boards + ground traffic)
        AirportSheet(
            airport = uiState.airportPage,
            metar = uiState.airportMetar,
            board = uiState.airportBoard,
            onGround = uiState.airportOnGround,
            loading = uiState.airportLoading,
            onSelectAircraft = { ac ->
                viewModel.closeAirportPage()
                viewModel.focusSearchResult(
                    ac,
                    onFocused = { lat, lon -> mapManager.flyTo(lat, lon, 11.0) }
                )
            },
            onClose = viewModel::closeAirportPage
        )
    }
}

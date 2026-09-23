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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.map.MapManager
import com.example.plane_tracker.ui.FlightDetailsPanel
import com.example.plane_tracker.ui.FilterSheet
import com.example.plane_tracker.ui.FollowingChip
import com.example.plane_tracker.ui.MapControls
import com.example.plane_tracker.ui.SearchOverlay
import com.example.plane_tracker.ui.StatusChip
import com.example.plane_tracker.ui.theme.PlaneTrackerTheme
import com.example.plane_tracker.viewmodel.FlightViewModel
import org.maplibre.android.MapLibre

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

    val uiState by viewModel.uiState.collectAsState()

    // Map setup once
    LaunchedEffect(mapManager) { mapManager.setup() }

    // Map callbacks
    LaunchedEffect(mapManager) {
        mapManager.onPlaneTapped = { hex ->
            viewModel.selectAircraft(hex) { lat, lon, heading ->
                mapManager.flyTo3D(lat, lon, heading)
            }
        }
        mapManager.onMapTapped = { viewModel.clearSelection() }
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
            SearchOverlay(
                query = uiState.searchQuery,
                aircraftResults = uiState.searchResults,
                airportResults = remember(uiState.searchQuery) {
                    Airports.search(uiState.searchQuery)
                },
                onQueryChange = viewModel::updateSearch,
                onAircraftClick = { ac ->
                    viewModel.focusSearchResult(
                        ac,
                        onFocused = { lat, lon -> mapManager.flyTo(lat, lon) },
                        onFlyTo3D = { lat, lon, heading -> mapManager.flyTo3D(lat, lon, heading) }
                    )
                },
                onAirportClick = { ap ->
                    mapManager.flyTo(ap.lat, ap.lon, 9.0)
                    viewModel.updateSearch("")
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
                    lastUpdateMs = uiState.lastUpdateMs
                )

                if (uiState.isFollowing) {
                    FollowingChip(
                        onCancel = viewModel::toggleFollow
                    )
                }
            }
        }

        // Right-side controls
        MapControls(
            airportsOn = uiState.filters.showAirports,
            labelsOn = uiState.filters.showLabels,
            onZoomIn = { mapManager.zoomBy(+1.5) },
            onZoomOut = { mapManager.zoomBy(-1.5) },
            onCenter = mapManager::centerOnDefault,
            onToggleAirports = viewModel::toggleAirports,
            onToggleLabels = viewModel::toggleLabels,
            onOpenFilters = { filtersOpen = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )

        // Bottom: flight details
        uiState.selected?.let { selected ->
            FlightDetailsPanel(
                selected = selected,
                routeProgress = uiState.routeProgress,
                isLoading = uiState.isLoadingDetails,
                isFollowing = uiState.isFollowing,
                onClose = viewModel::clearSelection,
                onToggleFollow = viewModel::toggleFollow,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Filter bottom sheet
        FilterSheet(
            visible = filtersOpen,
            filters = uiState.filters,
            onChange = viewModel::setFilters,
            onDismiss = { filtersOpen = false }
        )
    }
}

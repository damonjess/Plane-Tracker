package com.example.plane_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.data.FlightEngine
import com.example.plane_tracker.data.FlightRepository
import com.example.plane_tracker.data.SelectedFlight
import com.example.plane_tracker.data.MapFrame
import com.example.plane_tracker.util.RouteProgress
import com.example.plane_tracker.util.RouteProgressCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** UI filter state for which planes to show. */
data class FilterState(
    val minAltitudeFt: Int = 0,
    val maxAltitudeFt: Int = 50_000,
    val showAirports: Boolean = true,
    val showLabels: Boolean = true,
    val showTrail: Boolean = true
) {
    val isDefault: Boolean
        get() = minAltitudeFt == 0 && maxAltitudeFt >= 50_000
}

/** Top-level UI state exposed to Compose. */
data class TrackerUiState(
    val selected: SelectedFlight? = null,
    val routeProgress: RouteProgress? = null,
    val aircraftCount: Int = 0,
    val source: String = "…",
    val lastUpdateMs: Long = 0L,
    val filters: FilterState = FilterState(),
    val isFollowing: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Aircraft> = emptyList()
)

class FlightViewModel : ViewModel() {

    companion object {
        private const val FLEET_POLL_MS = 10_000L
        private const val FRAME_TICK_MS = 200L
        private const val HISTORY_KEEP_MS = 15 * 60_000L
    }

    private val repository = FlightRepository()
    private val engine = FlightEngine()

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private val _mapFrame = MutableStateFlow(MapFrame(emptyCollection(), null, emptyList(), null))
    val mapFrame: StateFlow<MapFrame> = _mapFrame.asStateFlow()

    private var followingHex: String? = null

    init {
        startPolling()
        startFrameTicker()
    }

    private fun emptyCollection() = FeatureCollection.fromFeatures(emptyList<Feature>())

    /** Network loop: refreshes the fleet every FLEET_POLL_MS. */
    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    engine.mergeFleet(repository.fetchFleet())
                    val state = engine.lastFleetState
                    _uiState.value = _uiState.value.copy(
                        aircraftCount = state?.aircraft?.size ?: 0,
                        source = state?.source ?: "offline",
                        lastUpdateMs = state?.fetchedAt ?: 0L
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(source = "offline")
                }
                delay(FLEET_POLL_MS)
            }
        }
    }

    /** Animation loop: recomputes interpolated positions and pushes to the map. */
    private fun startFrameTicker() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                pushFrame(now)
                delay(FRAME_TICK_MS)
            }
        }
    }

    private fun pushFrame(now: Long) {
        val filters = _uiState.value.filters
        val selected = engine.selectedAircraft(now)

        // Prune old trail samples periodically.
        trails(now)

        // Live route progress for the selected flight (position vs both airports).
        val progress = selected?.let { sel ->
            _uiState.value.selected?.route?.let { route ->
                route.origin?.takeIf { it.latitude != null && it.longitude != null }
                    ?.let { o -> route.destination?.let { d -> RouteProgressCalculator.compute(sel, o, d) } }
            }
        }

        // Keep live flight details panel updated with current position / speed / altitude
        val currentSelected = _uiState.value.selected
        val updatedSelected = if (currentSelected != null && selected != null && currentSelected.aircraft != selected) {
            currentSelected.copy(aircraft = selected)
        } else currentSelected

        if (progress != _uiState.value.routeProgress || updatedSelected != currentSelected) {
            _uiState.value = _uiState.value.copy(
                routeProgress = progress,
                selected = updatedSelected
            )
        }

        val features = engine.allAircraft(now)
            .filter { ac ->
                val altFt = if (ac.onGround) 0 else ac.altitudeFt
                altFt in filters.minAltitudeFt..filters.maxAltitudeFt
            }
            .map { ac -> buildFeature(ac) }

        val followPos = if (followingHex != null) {
            selected?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) }
        } else null

        val trailPoints = if (selected != null && filters.showTrail) {
            engine.trailFor(selected.icao24)
                .map { Point.fromLngLat(it.longitude, it.latitude) }
        } else emptyList()

        _mapFrame.value = MapFrame(
            planes = FeatureCollection.fromFeatures(features),
            selected = selected,
            trailCoordinates = trailPoints,
            followPos = followPos
        )
    }

    private fun trails(now: Long) {
        // Keep engine trail memory bounded (older than 15 min).
        engine.trimTrails(now - HISTORY_KEEP_MS)
    }

    private fun buildFeature(ac: Aircraft): Feature {
        val feature = Feature.fromGeometry(Point.fromLngLat(ac.longitude, ac.latitude))
        feature.addStringProperty("hex", ac.icao24)
        feature.addStringProperty("callsign", ac.callsign)
        feature.addNumberProperty("heading", ac.heading)
        feature.addNumberProperty("altitude", ac.altitudeFt)
        feature.addNumberProperty("velocity", ac.speedKt)
        feature.addStringProperty(
            "color",
            if (ac.onGround) "#b0bec5" else com.example.plane_tracker.data.AltitudeColors.forAltitude(ac.altitudeMeters)
        )
        return feature
    }

    /** User tapped a plane on the map. */
    fun selectAircraft(hex: String) {
        engine.selectedHex = hex
        _uiState.value = _uiState.value.copy(
            selected = engine.aircraftByHex(hex)?.let { SelectedFlight(it) },
            routeProgress = null,
            isLoadingDetails = true
        )
        viewModelScope.launch {
            val ac = engine.aircraftByHex(hex)
            if (ac == null) {
                _uiState.value = _uiState.value.copy(isLoadingDetails = false)
                return@launch
            }
            val details = try {
                repository.fetchFlightDetails(ac)
            } catch (e: Exception) {
                SelectedFlight(ac)
            }
            // Only apply if still selected.
            if (engine.selectedHex == hex) {
                _uiState.value = _uiState.value.copy(
                    selected = details, isLoadingDetails = false
                )
            }
        }
    }

    /** User tapped empty map: clear selection. */
    fun clearSelection() {
        engine.selectedHex = null
        followingHex = null
        _uiState.value = _uiState.value.copy(
            selected = null, routeProgress = null, isFollowing = false, isLoadingDetails = false
        )
    }

    fun toggleFollow() {
        val hex = engine.selectedHex ?: return
        followingHex = if (followingHex == hex) null else hex
        _uiState.value = _uiState.value.copy(isFollowing = followingHex != null)
    }

    fun updateFilters(transform: (FilterState) -> FilterState) {
        _uiState.value = _uiState.value.copy(filters = transform(_uiState.value.filters))
    }

    fun setFilters(filters: FilterState) {
        _uiState.value = _uiState.value.copy(filters = filters)
    }

    fun toggleAirports() = updateFilters { it.copy(showAirports = !it.showAirports) }

    fun toggleLabels() = updateFilters { it.copy(showLabels = !it.showLabels) }

    fun applyFiltersToMap(map: com.example.plane_tracker.map.MapManager) {
        val f = _uiState.value.filters
        map.setAirportsVisible(f.showAirports)
        map.setLabelsVisible(f.showLabels)
    }

    fun updateSearch(query: String) {
        val results = if (query.isBlank()) emptyList() else {
            val q = query.trim().uppercase()
            engine.allAircraft(System.currentTimeMillis())
                .filter { ac ->
                    ac.callsign.uppercase().contains(q) || ac.icao24.uppercase().contains(q)
                }
                .take(8)
        }
        _uiState.value = _uiState.value.copy(searchQuery = query, searchResults = results)
    }

    /** Focus the map on a search result. */
    fun focusSearchResult(ac: Aircraft, onFocused: (Double, Double) -> Unit) {
        onFocused(ac.latitude, ac.longitude)
        selectAircraft(ac.icao24)
        updateSearch("")
    }

    /** Flies to an airport's location (search fallback for airports). */
    fun flyToAirport(iata: String, onFocused: (Double, Double) -> Unit): Boolean {
        val entry = Airports.byIata(iata) ?: return false
        onFocused(entry.lat, entry.lon)
        return true
    }
}

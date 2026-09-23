package com.example.plane_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.data.AirportBoard
import com.example.plane_tracker.data.AirportBoardBuilder
import com.example.plane_tracker.data.EmergencyDetector
import com.example.plane_tracker.data.EmergencyEvent
import com.example.plane_tracker.data.OpsCategory
import com.example.plane_tracker.data.OpsClassifier
import com.example.plane_tracker.data.FlightEngine
import com.example.plane_tracker.data.FlightRepository
import com.example.plane_tracker.data.Metar
import com.example.plane_tracker.data.SelectedFlight
import com.example.plane_tracker.data.MapFrame
import com.example.plane_tracker.data.RadarFrame
import com.example.plane_tracker.util.GeoMath
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
    val showTrail: Boolean = true,
    /** Show only blue-light aviation (coastguard, police, air ambulance, military). */
    val showOnlyOps: Boolean = false
) {
    val isDefault: Boolean
        get() = minAltitudeFt == 0 && maxAltitudeFt >= 50_000 && !showOnlyOps
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
    val searchResults: List<Aircraft> = emptyList(),
    /** Live emergency-squawk events (7700/7600/7500), minus dismissed ones. */
    val emergencies: List<EmergencyEvent> = emptyList(),
    /** Rain radar overlay state. */
    val radarOn: Boolean = false,
    val radarPlaying: Boolean = true,
    val radarFrames: List<RadarFrame> = emptyList(),
    /** Full airport page (FR24-style). */
    val airportPage: Airports.Entry? = null,
    val airportMetar: Metar? = null,
    val airportBoard: AirportBoard? = null,
    val airportOnGround: List<Aircraft> = emptyList(),
    val airportLoading: Boolean = false
)

class FlightViewModel : ViewModel() {

    companion object {
        private const val FLEET_POLL_MS = 10_000L
        private const val FRAME_TICK_MS = 200L
        private const val HISTORY_KEEP_MS = 15 * 60_000L
        private const val RADAR_POLL_MS = 10 * 60_000L
    }

    private val repository = FlightRepository()
    private val engine = FlightEngine()

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private val _mapFrame = MutableStateFlow(MapFrame(emptyCollection(), null, emptyList(), null))
    val mapFrame: StateFlow<MapFrame> = _mapFrame.asStateFlow()

    private var followingHex: String? = null

    /** Triggered when a selected/followed aircraft transitions into descent during flight. */
    var onAutoTiltTo3D: ((Double, Double, Float) -> Unit)? = null
    private var lastSelectedWasDescending = false

    private val dismissedEmergencyHexes = mutableSetOf<String>()
    private var openAirportPageEntry: Airports.Entry? = null

    /** Cached special-ops classification per hex (coastguard, police, military...). */
    private val opsCategories = java.util.concurrent.ConcurrentHashMap<String, OpsCategory>()
    /** Owner names fetched from adsbdb, cached per hex. */
    private val ownerByHex = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Hexes already attempted (including misses) to avoid repeat lookups. */
    private val opsLookupAttempted = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    init {
        startPolling()
        startFrameTicker()
        startRadarPolling()
        startOpsLookupLoop()
    }

    private fun emptyCollection() = FeatureCollection.fromFeatures(emptyList<Feature>())

    /** Network loop: refreshes the fleet every FLEET_POLL_MS. */
    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    engine.mergeFleet(repository.fetchFleet())
                    val state = engine.lastFleetState
                    // Recompute emergency squawks from the fresh snapshot.
                    val emergencies = EmergencyDetector.identify(state?.aircraft ?: emptyList())
                    // Forgive dismissals once the event is gone so a new squawk alerts again.
                    dismissedEmergencyHexes.removeAll { hex -> emergencies.none { it.hex == hex } }
                    _uiState.value = _uiState.value.copy(
                        aircraftCount = state?.aircraft?.size ?: 0,
                        source = state?.source ?: "offline",
                        lastUpdateMs = state?.fetchedAt ?: 0L,
                        emergencies = emergencies.filter { it.hex !in dismissedEmergencyHexes }
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(source = "offline")
                }
                delay(FLEET_POLL_MS)
            }
        }
    }

    /**
     * Background loop: resolves adsbdb owner names for fleet aircraft we haven't
     * classified yet, then caches their special-ops category. Rate-limited.
     */
    private fun startOpsLookupLoop() {
        viewModelScope.launch {
            while (true) {
                val pending = engine.allAircraft(System.currentTimeMillis())
                    .map { it.icao24 }
                    .filter { !opsLookupAttempted.contains(it) }
                    .take(20)
                if (pending.isEmpty()) {
                    delay(5_000)
                } else {
                    for (hex in pending) {
                        opsLookupAttempted.add(hex)
                        try {
                            val info = repository.fetchAircraftInfo(hex)
                            val owner = info?.registeredOwner
                            if (owner != null) {
                                ownerByHex[hex] = owner
                            }
                            val fleetAc = engine.aircraftByHex(hex)
                            if (fleetAc != null) {
                                OpsClassifier.classify(fleetAc, owner)?.let { cat ->
                                    opsCategories[hex] = cat
                                    // Refresh the badge if this plane is currently selected.
                                    if (_uiState.value.selected?.aircraft?.icao24 == hex) {
                                        _uiState.value = _uiState.value.copy(
                                            selected = _uiState.value.selected?.copy(opsCategory = cat)
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Classification is best-effort.
                        }
                        delay(120) // gentle on adsbdb
                    }
                }
            }
        }
    }

    /** Background loop: refreshes RainViewer radar frames every 10 minutes. */
    private fun startRadarPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val frames = repository.fetchRadarFrames()
                    if (frames.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(radarFrames = frames)
                    }
                } catch (_: Exception) {
                    // Radar is best-effort; keep the old frames.
                }
                delay(RADAR_POLL_MS)
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
            .filter { ac ->
                !filters.showOnlyOps || opsCategoryFor(ac) != null
            }
            .map { ac -> buildFeature(ac) }

        // Trigger auto-tilt on transition to descent during live flight
        val sel = selected
        val isDescendingNow = sel != null && sel.climbFpm < -300
        if (isDescendingNow && !lastSelectedWasDescending) {
            onAutoTiltTo3D?.invoke(sel.latitude, sel.longitude, sel.heading)
        }
        lastSelectedWasDescending = isDescendingNow

        val followHeading = if (followingHex != null) {
            selected?.heading
        } else null

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
            followPos = followPos,
            followHeading = followHeading
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
        opsCategoryFor(ac)?.let {
            feature.addStringProperty("ops", it.name)
            feature.addStringProperty("opsRing", it.ringColor)
        }
        return feature
    }

    /** User tapped a plane on the map. */
    fun selectAircraft(hex: String, onFlyTo3D: ((Double, Double, Float) -> Unit)? = null) {
        engine.selectedHex = hex
        val ac = engine.aircraftByHex(hex)
        lastSelectedWasDescending = ac?.let { it.climbFpm < -300 } ?: false

        if (ac != null) {
            _uiState.value = _uiState.value.copy(
                selected = SelectedFlight(ac, opsCategory = opsCategoryFor(ac)),
                routeProgress = null,
                isLoadingDetails = true
            )

            // If the plane is actively descending, trigger 3D view.
            // Threshold -300 fpm: ADS-B vertical rate is quantized in 64 fpm steps,
            // so level flight wobbles between 0 and ±64 — -50 would false-positive.
            if (ac.climbFpm < -300) {
                onFlyTo3D?.invoke(ac.latitude, ac.longitude, ac.heading)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                selected = null,
                routeProgress = null,
                isLoadingDetails = true
            )
        }

        viewModelScope.launch {
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
                    selected = details.copy(opsCategory = opsCategoryFor(details.aircraft)),
                    isLoadingDetails = false
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

    fun toggleShowOnlyOps() = updateFilters { it.copy(showOnlyOps = !it.showOnlyOps) }

    /** Live classification for the details panel badge. */
    fun opsCategoryFor(ac: Aircraft): OpsCategory? =
        opsCategories[ac.icao24] ?: OpsClassifier.classify(ac, ownerByHex[ac.icao24])

    fun opsCategoryFor(hex: String): OpsCategory? {
        val ac = engine.aircraftByHex(hex) ?: return opsCategories[hex]
        return opsCategoryFor(ac)
    }

    /** Hides an emergency banner; it returns if the aircraft squawks again later. */
    fun dismissEmergency(hex: String) {
        dismissedEmergencyHexes.add(hex)
        _uiState.value = _uiState.value.copy(
            emergencies = _uiState.value.emergencies.filter { it.hex != hex }
        )
    }

    fun toggleRadar() {
        _uiState.value = _uiState.value.copy(radarOn = !_uiState.value.radarOn)
    }

    fun toggleRadarPlaying() {
        _uiState.value = _uiState.value.copy(radarPlaying = !_uiState.value.radarPlaying)
    }

    /** Opens the FR24-style airport page: weather + live boards + ground traffic. */
    fun openAirportPage(entry: Airports.Entry) {
        openAirportPageEntry = entry
        _uiState.value = _uiState.value.copy(
            airportPage = entry,
            airportMetar = null,
            airportBoard = null,
            airportOnGround = emptyList(),
            airportLoading = true
        )
        viewModelScope.launch {
            try {
                // METAR weather (best-effort, never blocks the page).
                val metar = try {
                    repository.fetchMetar(entry.icao)
                } catch (_: Exception) {
                    null
                }
                // Live departures/arrivals + on-ground traffic.
                val aircraft = engine.allAircraft(System.currentTimeMillis())
                val candidates = AirportBoardBuilder.candidateCallsigns(entry.lat, entry.lon, aircraft)
                val routes = candidates.associateWith { cs ->
                    try {
                        repository.fetchRoute(cs)
                    } catch (_: Exception) {
                        null
                    }
                }
                val board = AirportBoardBuilder.build(entry.iata, entry.lat, entry.lon, aircraft, routes)
                val onGround = aircraft.filter {
                    it.onGround && !it.latitude.isNaN() && !it.longitude.isNaN() &&
                        GeoMath.distanceMeters(entry.lat, entry.lon, it.latitude, it.longitude) < 15_000
                }
                if (openAirportPageEntry == entry) {
                    _uiState.value = _uiState.value.copy(
                        airportMetar = metar,
                        airportBoard = board,
                        airportOnGround = onGround,
                        airportLoading = false
                    )
                }
            } catch (_: Exception) {
                if (openAirportPageEntry == entry) {
                    _uiState.value = _uiState.value.copy(
                        airportBoard = AirportBoard(entry.iata, emptyList(), emptyList()),
                        airportLoading = false
                    )
                }
            }
        }
    }

    fun closeAirportPage() {
        openAirportPageEntry = null
        _uiState.value = _uiState.value.copy(
            airportPage = null, airportMetar = null, airportBoard = null,
            airportOnGround = emptyList(), airportLoading = false
        )
    }

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
    fun focusSearchResult(
        ac: Aircraft,
        onFocused: (Double, Double) -> Unit,
        onFlyTo3D: ((Double, Double, Float) -> Unit)? = null
    ) {
        if (ac.climbFpm < -300 && onFlyTo3D != null) {
            selectAircraft(ac.icao24, onFlyTo3D)
        } else {
            onFocused(ac.latitude, ac.longitude)
            selectAircraft(ac.icao24)
        }
        updateSearch("")
    }

    /** Flies to an airport's location (search fallback for airports). */
    fun flyToAirport(iata: String, onFocused: (Double, Double) -> Unit): Boolean {
        val entry = Airports.byIata(iata) ?: return false
        onFocused(entry.lat, entry.lon)
        return true
    }
}

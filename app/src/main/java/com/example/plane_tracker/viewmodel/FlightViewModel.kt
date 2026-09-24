package com.example.plane_tracker.viewmodel

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.example.plane_tracker.data.Aircraft
import com.example.plane_tracker.data.Airports
import com.example.plane_tracker.data.AirportBoard
import com.example.plane_tracker.data.AirportBoardBuilder
import com.example.plane_tracker.data.AisRepository
import com.example.plane_tracker.data.EmergencyDetector
import com.example.plane_tracker.data.EmergencyEvent
import com.example.plane_tracker.data.OpsCategory
import com.example.plane_tracker.data.OpsClassifier
import com.example.plane_tracker.data.FlightEngine
import com.example.plane_tracker.data.FlightRepository
import com.example.plane_tracker.data.Metar
import com.example.plane_tracker.data.SelectedFlight
import com.example.plane_tracker.data.RouteInfo
import com.example.plane_tracker.data.MapFrame
import com.example.plane_tracker.data.RadarFrame
import com.example.plane_tracker.data.Vessel
import com.example.plane_tracker.util.GeoMath
import com.example.plane_tracker.util.RouteProgress
import com.example.plane_tracker.util.RouteProgressCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** UI filter state for which planes to show. */
data class FilterState(
    val minAltitudeFt: Int = -2000,
    val maxAltitudeFt: Int = 50_000,
    val showAirports: Boolean = true,
    val showLabels: Boolean = true,
    val showTrail: Boolean = true,
    /** Show only blue-light aviation (coastguard, police, air ambulance, military). */
    val showOnlyOps: Boolean = false
) {
    val isDefault: Boolean
        get() = minAltitudeFt <= 0 && maxAltitudeFt >= 50_000 && !showOnlyOps
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
    val lifeboatSearchResults: List<Vessel> = emptyList(),
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
    val airportLoading: Boolean = false,
    /** Past + ongoing emergency-squawk events (survives banner dismissal). */
    val alertHistory: List<com.example.plane_tracker.data.EmergencyHistoryTracker.Entry> = emptyList(),
    /** Currently-classified blue-light / military aircraft. */
    val opsAircraft: List<Pair<Aircraft, OpsCategory>> = emptyList(),
    /** True once a fleet snapshot has landed, so an empty ops list is meaningful. */
    val opsReady: Boolean = false,
    /** METAR weather per airport ICAO for the map badges. */
    val airportWx: Map<String, Metar> = emptyMap(),
    /** Show tiny weather chips next to airport dots on the map. */
    val showAirportWx: Boolean = false,
    /** Flight replay state. */
    val replayFlight: Aircraft? = null,
    val replayPoints: List<com.example.plane_tracker.data.FlightHistoryStore.Point> = emptyList(),
    val replayIndex: Int = 0,
    val replayPlaying: Boolean = false,
    /** AIS Lifeboats. */
    val lifeboats: List<Vessel> = emptyList(),
    /** Currently selected lifeboat. */
    val selectedLifeboat: Vessel? = null
)

class FlightViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val FLEET_POLL_MS = 10_000L
        private const val FRAME_TICK_MS = 200L
        private const val HISTORY_KEEP_MS = 15 * 60_000L
        private const val RADAR_POLL_MS = 10 * 60_000L
        /** adsbdb attempts per hex before we give up for this session. */
        private const val MAX_OPS_LOOKUP_ATTEMPTS = 3
    }

    private val repository = FlightRepository()
    private val engine = FlightEngine()

    // --- Session + persistent history (feature: squawk history & 24h playback) ---
    private val emergencyHistory = com.example.plane_tracker.data.EmergencyHistoryTracker()
    private val historyStore = com.example.plane_tracker.data.FlightHistoryStore(application)

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
    /** Generation counter so stale airport loads can't write state. */
    private var airportLoadGen = 0
    /** Identifies the current airport refresh loop; opening a new airport cancels the old one. */
    private var airportRefreshTick = 0

    /** Cached special-ops classification per hex (coastguard, police, military...). */
    private val opsCategories = java.util.concurrent.ConcurrentHashMap<String, OpsCategory>()
    
    /** WebSocket for AIS */
    private val aisRepo = AisRepository(OkHttpClient())

    /** Owner names fetched from adsbdb, cached per hex. */
    private val ownerByHex = java.util.concurrent.ConcurrentHashMap<String, String>()
    /**
     * Owner-lookup attempts per hex. Deliberately a *capped counter* rather than a
     * "tried once, never again" set: adsbdb failures and misses are
     * indistinguishable from here, and one bad response used to blacklist an
     * aircraft for the rest of the session.
     */
    private val opsLookupAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    /**
     * Classification inputs already evaluated per hex, so repeat recomputes of the
     * ops list only pay for aircraft whose callsign/registration/owner/flags changed.
     */
    private val opsEvaluated = java.util.concurrent.ConcurrentHashMap<String, String>()
    /**
     * Hexes in adsb.lol's curated military fleet (refreshed every 5 min). Keeps
     * military tagging working when the local feed record carries no dbFlags.
     */
    private val militaryHexes = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Composition signature of the published ops list, to avoid needless churn. */
    private var opsSignature = ""

    init {
        aisRepo.start()
        viewModelScope.launch {
            aisRepo.lifeboats.collect { lifeboats ->
                val currentSelected = _uiState.value.selectedLifeboat
                val updatedSelected = currentSelected?.let { sel ->
                    lifeboats.find { it.mmsi == sel.mmsi } ?: sel
                }
                _uiState.value = _uiState.value.copy(
                    lifeboats = lifeboats,
                    selectedLifeboat = updatedSelected
                )
            }
        }
        startPolling()
        startFrameTicker()
        startRadarPolling()
        startOpsLookupLoop()
        startMilitaryHexLoop()
        startAirportWxLoop()
        startHistoryTrim()
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
                    // Record into session history (keeps ended events for review).
                    emergencyHistory.update(emergencies)
                    // Persist positions for 24h playback on IO dispatcher.
                    state?.aircraft?.let { acList ->
                        withContext(Dispatchers.IO) {
                            historyStore.insertAll(acList)
                        }
                    }
                    // A failed poll yields an empty "offline" snapshot: keep the
                    // last known counts/lists (engine still holds the fleet) and
                    // just flag the data source as offline.
                    if (state != null && state.aircraft.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            aircraftCount = state.aircraft.size,
                            source = state.source,
                            lastUpdateMs = state.fetchedAt,
                            emergencies = emergencies.filter { it.hex !in dismissedEmergencyHexes },
                            alertHistory = emergencyHistory.all(),
                            opsReady = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            source = state?.source ?: "offline",
                            alertHistory = emergencyHistory.all()
                        )
                    }
                    // Rebuild the ops list from the live fleet, never from the raw
                    // snapshot, so the sheet can't disagree with the map.
                    refreshOpsList(force = true)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(source = "offline")
                }
                delay(FLEET_POLL_MS)
            }
        }
    }

    /** Refreshes airport weather badges every 10 minutes (one batched request). */
    private fun startAirportWxLoop() {
        viewModelScope.launch {
            while (true) {
                try {
                    val icaos = Airports.byCode.values.map { it.icao }
                    val wx = repository.fetchMetarBatch(icaos)
                    if (wx.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(airportWx = wx)
                    }
                } catch (_: Exception) {
                    // Badges are best-effort.
                }
                delay(10 * 60_000L)
            }
        }
    }

    /** Trims the SQLite history daily-bounded; runs every 30 min. */
    private fun startHistoryTrim() {
        viewModelScope.launch {
            while (true) {
                delay(30 * 60_000L)
                try {
                    withContext(Dispatchers.IO) {
                        historyStore.trimOld()
                    }
                } catch (_: Exception) {
                }
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
                    .filter { (opsLookupAttempts[it] ?: 0) < MAX_OPS_LOOKUP_ATTEMPTS }
                    .take(20)
                if (pending.isEmpty()) {
                    delay(5_000)
                } else {
                    var classifiedSomething = false
                    for (hex in pending) {
                        // Counted per attempt, not per hex: adsbdb failures are
                        // indistinguishable from misses here, and a single bad
                        // response used to blacklist an aircraft for the session.
                        opsLookupAttempts[hex] = (opsLookupAttempts[hex] ?: 0) + 1
                        try {
                            val info = repository.fetchAircraftInfo(hex)
                            val owner = info?.registeredOwner
                            if (owner != null && ownerByHex.put(hex, owner) != owner) {
                                classifiedSomething = true
                            }
                            val fleetAc = engine.aircraftByHex(hex)
                            if (fleetAc != null) {
                                OpsClassifier.classify(
                                    fleetAc,
                                    owner,
                                    militaryHexes.contains(hex)
                                )?.let { cat ->
                                    if (opsCategories.put(hex, cat) != cat) {
                                        classifiedSomething = true
                                    }
                                    // Refresh the badge if this plane is currently selected.
                                    if (_uiState.value.selected?.aircraft?.icao24 == hex) {
                                        _uiState.value = _uiState.value.copy(
                                            selected = _uiState.value.selected?.copy(opsCategory = cat)
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Classification is best-effort; the attempt cap retries it.
                        }
                        delay(120) // gentle on adsbdb
                    }
                    // Publish newly-classified aircraft straight away rather than
                    // waiting up to a full poll interval for the sheet to catch up.
                    if (classifiedSomething) refreshOpsList()
                }
            }
        }
    }

    /**
     * Refreshes adsb.lol's curated military hex list (~150 KB every 5 min) so
     * military aircraft are recognised even when the fleet record for that hex
     * carries no dbFlags — e.g. after a fall back to a source that omits them.
     */
    private fun startMilitaryHexLoop() {
        viewModelScope.launch {
            while (true) {
                try {
                    val hexes = repository.fetchMilitaryHexes()
                    // Grow-only: a hex that left the list must not lose its tag and
                    // flicker out of the ops sheet.
                    if (hexes.isNotEmpty() && militaryHexes.addAll(hexes)) {
                        // Every affected hex changes signature, so the next rebuild
                        // re-classifies it as military.
                        refreshOpsList()
                    }
                } catch (_: Exception) {
                    // Best-effort: dbFlags, call signs and owner lookups still work.
                }
                delay(5 * 60_000L)
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

        // Aircraft actively squawking an emergency stay on the map whatever the
        // filters say: the banner and history still list them, so hiding the icon
        // would leave the alert pointing at nothing.
        val features = engine.allAircraft(now)
            .filter { ac ->
                EmergencyDetector.isEmergency(ac.squawk) ||
                    (if (ac.onGround) 0 else ac.altitudeFt) in filters.minAltitudeFt..filters.maxAltitudeFt
            }
            .filter { ac ->
                EmergencyDetector.isEmergency(ac.squawk) ||
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
            selected = null, selectedLifeboat = null, routeProgress = null, isFollowing = false, isLoadingDetails = false
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

    fun toggleAirportWx() {
        _uiState.value = _uiState.value.copy(showAirportWx = !_uiState.value.showAirportWx)
    }

    /** Live classification for the details panel badge and the ops list. */
    fun opsCategoryFor(ac: Aircraft): OpsCategory? {
        opsCategories[ac.icao24]?.let { return it }
        val signature = opsInputSignature(ac)
        // Memoised against the inputs that produced the result, so an aircraft is
        // only re-evaluated when its call sign, tail, owner or flags change.
        if (opsEvaluated[ac.icao24] == signature) return null
        val category = OpsClassifier.classify(
            ac,
            ownerByHex[ac.icao24],
            militaryHexes.contains(ac.icao24)
        )
        opsEvaluated[ac.icao24] = signature
        if (category != null) opsCategories[ac.icao24] = category
        return category
    }

    private fun opsInputSignature(ac: Aircraft): String = buildString {
        append(ac.callsign.trim()).append('|')
        append(ac.registration.orEmpty()).append('|')
        append(ac.dbFlags).append('|')
        append(ownerByHex[ac.icao24].orEmpty()).append('|')
        append(militaryHexes.contains(ac.icao24))
    }

    /**
     * Rebuilds the ops list from the **live fleet** — the same set the map draws —
     * so the sheet, the map badges and the AR overlay can never disagree. Only
     * emits state when the composition of the list changes unless [force] is set.
     */
    private fun refreshOpsList(force: Boolean = false) {
        val list = engine.allAircraft(System.currentTimeMillis())
            .mapNotNull { ac -> opsCategoryFor(ac)?.let { ac to it } }
            .sortedWith(
                compareBy(
                    { it.second.ordinal },
                    { it.first.callsign.ifEmpty { it.first.icao24 } }
                )
            )
        val signature = list.joinToString(",") { it.first.icao24 }
        if (!force && signature == opsSignature) return
        opsSignature = signature
        _uiState.value = _uiState.value.copy(opsAircraft = list)
    }

    fun opsCategoryFor(hex: String): OpsCategory? {
        val ac = engine.aircraftByHex(hex) ?: return opsCategories[hex]
        return opsCategoryFor(ac)
    }

    /** Live aircraft snapshot for the AR sky view. */
    fun aircraftForAr(): List<Aircraft> = engine.allAircraft(System.currentTimeMillis())

    /** Ops category labels by hex for the AR overlay badges. */
    fun opsLabelsForAr(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        _uiState.value.opsAircraft.forEach { (ac, cat) ->
            out[ac.icao24] = "${cat.emoji} ${cat.label}"
        }
        return out
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
        airportRefreshTick++
        _uiState.value = _uiState.value.copy(
            airportPage = entry,
            airportMetar = null,
            airportBoard = null,
            airportOnGround = emptyList(),
            airportLoading = true
        )
        loadAirportPageData(entry)
        startAirportRefreshLoop(entry)
    }

    /**
     * Loads the airport page data with **progressive** board rendering: METAR
     * and ground traffic land as soon as each resolves, and the departures /
     * arrivals board grows row by row as each route lookup completes instead
     * of appearing in one batch. Route lookups run 3 at a time (polite to
     * adsbdb, still ~1-2s for a full board).
     */
    private fun loadAirportPageData(entry: Airports.Entry) {
        val gen = ++airportLoadGen
        viewModelScope.launch {
            try {
                val limiter = Semaphore(3)

                // METAR weather: updates the moment it arrives.
                val metarJob = launch {
                    val metar = try {
                        repository.fetchMetar(entry.icao)
                    } catch (_: Exception) {
                        null
                    }
                    if (gen == airportLoadGen && metar != null) {
                        _uiState.value = _uiState.value.copy(airportMetar = metar)
                    }
                }

                val aircraft = engine.allAircraft(System.currentTimeMillis())

                // Ground traffic: show immediately.
                val onGround = aircraft.filter {
                    it.onGround && !it.latitude.isNaN() && !it.longitude.isNaN() &&
                        GeoMath.distanceMeters(entry.lat, entry.lon, it.latitude, it.longitude) < 15_000
                }
                if (gen == airportLoadGen) {
                    _uiState.value = _uiState.value.copy(airportOnGround = onGround)
                }

                // Route lookups: 3 concurrent, board rebuilt after each resolve.
                val routes = java.util.concurrent.ConcurrentHashMap<String, RouteInfo?>()
                val candidates = AirportBoardBuilder.candidateCallsigns(entry.lat, entry.lon, aircraft)
                coroutineScope {
                    candidates.forEach { cs ->
                        launch {
                            limiter.withPermit {
                                val route = try {
                                    repository.fetchRoute(cs)
                                } catch (_: Exception) {
                                    null
                                }
                                if (route != null && gen == airportLoadGen) {
                                    routes[cs] = route
                                    val now = engine.allAircraft(System.currentTimeMillis())
                                    val board = AirportBoardBuilder.build(entry.iata, entry.lat, entry.lon, now, routes)
                                    _uiState.value = _uiState.value.copy(
                                        airportBoard = board,
                                        airportLoading = board.total == 0
                                    )
                                }
                            }
                        }
                    }
                }

                // Batch complete: final authoritative board + ground list.
                if (gen == airportLoadGen) {
                    val now = engine.allAircraft(System.currentTimeMillis())
                    val board = AirportBoardBuilder.build(entry.iata, entry.lat, entry.lon, now, routes)
                    val groundNow = now.filter {
                        it.onGround && !it.latitude.isNaN() && !it.longitude.isNaN() &&
                            GeoMath.distanceMeters(entry.lat, entry.lon, it.latitude, it.longitude) < 15_000
                    }
                    _uiState.value = _uiState.value.copy(
                        airportBoard = board,
                        airportOnGround = groundNow,
                        airportLoading = false
                    )
                }
                metarJob.join()
            } catch (_: Exception) {
                if (gen == airportLoadGen) {
                    _uiState.value = _uiState.value.copy(airportLoading = false)
                }
            }
        }
    }

    /** Re-runs the airport page load every 30s while the sheet stays open. */
    private fun startAirportRefreshLoop(entry: Airports.Entry) {
        val tick = airportRefreshTick
        viewModelScope.launch {
            while (airportRefreshTick == tick && openAirportPageEntry == entry) {
                delay(30_000)
                if (airportRefreshTick != tick || openAirportPageEntry != entry) break
                loadAirportPageData(entry)
            }
        }
    }

    fun closeAirportPage() {
        openAirportPageEntry = null
        airportLoadGen++
        airportRefreshTick++
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
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                searchResults = emptyList(),
                lifeboatSearchResults = emptyList()
            )
            return
        }
        val q = query.trim().uppercase()
        val results = engine.allAircraft(System.currentTimeMillis())
            .filter { ac ->
                ac.callsign.uppercase().contains(q) || ac.icao24.uppercase().contains(q)
            }
            .take(8)

        val lbResults = _uiState.value.lifeboats
            .filter { lb ->
                lb.name.uppercase().contains(q) || lb.mmsi.uppercase().contains(q)
            }
            .take(5)

        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchResults = results,
            lifeboatSearchResults = lbResults
        )
    }

    /** Focus map on a lifeboat search result. */
    fun focusLifeboatResult(
        vessel: Vessel,
        onFocused: (Double, Double) -> Unit
    ) {
        onFocused(vessel.latitude, vessel.longitude)
        selectLifeboat(vessel.mmsi)
        updateSearch("")
    }

    /** Select a lifeboat by MMSI. */
    fun selectLifeboat(mmsi: String) {
        clearSelection()
        val lb = _uiState.value.lifeboats.find { it.mmsi == mmsi }
        _uiState.value = _uiState.value.copy(selectedLifeboat = lb)
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

    // ---------- Flight history playback ----------

    private var replayJob: kotlinx.coroutines.Job? = null

    /** Opens the replay sheet for an aircraft: loads its recorded track. */
    fun openReplay(hex: String, onLoaded: (Int) -> Unit = {}) {
        replayJob?.cancel()
        viewModelScope.launch {
            val ac = engine.aircraftByHex(hex)
            val points = withContext(Dispatchers.IO) {
                historyStore.trackFor(hex, hours = 2)
            }
            if (points.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    replayFlight = ac, replayPoints = emptyList(),
                    replayIndex = 0, replayPlaying = false
                )
                onLoaded(0)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                replayFlight = ac,
                replayPoints = points,
                replayIndex = 0,
                replayPlaying = false
            )
            onLoaded(points.size)
        }
    }

    fun closeReplay() {
        replayJob?.cancel()
        _uiState.value = _uiState.value.copy(
            replayFlight = null, replayPoints = emptyList(),
            replayIndex = 0, replayPlaying = false
        )
    }

    fun seekReplay(index: Int) {
        val points = _uiState.value.replayPoints
        if (points.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            replayIndex = index.coerceIn(0, points.lastIndex),
            replayPlaying = false
        )
    }

    /** Plays the recorded track, animating position along the stored samples. */
    fun playReplay() {
        val points = _uiState.value.replayPoints
        if (points.isEmpty()) return
        _uiState.value = _uiState.value.copy(replayPlaying = true)
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            val start = _uiState.value.replayIndex
            for (i in start until points.size) {
                if (!_uiState.value.replayPlaying) break
                _uiState.value = _uiState.value.copy(replayIndex = i)
                delay(400)
            }
            _uiState.value = _uiState.value.copy(replayPlaying = false)
        }
    }

    fun pauseReplay() {
        replayJob?.cancel()
        _uiState.value = _uiState.value.copy(replayPlaying = false)
    }

    override fun onCleared() {
        super.onCleared()
        aisRepo.stop()
    }
}

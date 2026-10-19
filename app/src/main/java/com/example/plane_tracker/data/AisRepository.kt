package com.example.plane_tracker.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Streams live AIS vessel positions from aisstream.io and publishes the
 * lifeboat subset.
 */
class AisRepository(
    private val client: OkHttpClient,
    private val lifeboatStore: LifeboatStore? = null
) {
    private val wsClient = client.newBuilder()
        .pingInterval(PING_INTERVAL_S, TimeUnit.SECONDS)
        .build()

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private val vessels = ConcurrentHashMap<String, Vessel>()
    private val dirtyPersistence = ConcurrentHashMap.newKeySet<String>()

    private val _lifeboats = MutableStateFlow<List<Vessel>>(emptyList())
    val lifeboats: StateFlow<List<Vessel>> = _lifeboats.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var resubscribeRunnable: Runnable? = null

    private val reconnecting = AtomicBoolean(false)
    private var reconnectAttempt = 0
    private var connectGeneration = 0

    @Volatile
    private var emissionPending = false
    @Volatile
    private var lastEmitMs = 0L
    @Volatile
    private var lastSubscriptionSentMs = 0L

    @Volatile
    private var liveSocketOpen = false

    private val trailRecorder = VesselTrailRecorder()

    @Volatile
    private var viewportBox: List<Double>? = null

    init {
        preloadLifeboats()
    }

    /**
     * Immediately populates the in-memory vessel cache with seeded station lifeboats
     * and overrides them with persisted SQLite records so lifeboats appear at 0ms on startup.
     */
    private fun preloadLifeboats() {
        val now = System.currentTimeMillis()
        for ((mmsi, seeded) in KnownLifeboats.seededStationVessels(now)) {
            vessels[mmsi] = seeded
        }
        val stored = lifeboatStore?.loadAllLifeboats().orEmpty()
        for ((mmsi, vessel) in stored) {
            vessels[mmsi] = vessel
        }
        if (vessels.isNotEmpty()) {
            emitLifeboats()
        }
    }

    /** Connects immediately to the AIS stream. */
    fun start() {
        if (webSocket != null) return
        if (com.example.plane_tracker.BuildConfig.AIS_STREAM_API_KEY.isBlank()) {
            Log.w(TAG, "AIS_STREAM_API_KEY is blank in local.properties; showing seeded/cached lifeboats only.")
            return
        }
        connect()
    }

    /**
     * Expands subscription coverage if the user pans outside the default UK/Europe boxes.
     * Never closes a live WebSocket — updates the subscription in-place (rate-limited to
     * respect aisstream.io's 1 update/sec limit).
     */
    fun setViewport(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) {
        if (webSocket == null) {
            start()
        }

        // If the camera is already inside our always-on UK/Europe default boxes
        // or the active custom viewport box, no subscription update is needed.
        if (isViewportInDefaultBoxes(minLat, minLon, maxLat, maxLon)) {
            if (viewportBox == null) return
            viewportBox = null
            scheduleInPlaceResubscribe()
            return
        }

        val currentBox = viewportBox
        if (liveSocketOpen && isViewportContained(minLat, minLon, maxLat, maxLon, currentBox)) {
            return
        }

        val box = clampedViewportBox(minLat, minLon, maxLat, maxLon)
        synchronized(this) {
            if (box == viewportBox) return
            viewportBox = box
        }

        scheduleInPlaceResubscribe()
    }

    /** Falls back to the home-waters default without tearing down the live socket. */
    fun clearViewport() {
        synchronized(this) {
            if (viewportBox == null) return
            viewportBox = null
        }
        scheduleInPlaceResubscribe()
    }

    /**
     * Sends an updated subscription message over the existing open WebSocket,
     * throttled to at least 1.5s between sends so aisstream.io never drops us.
     */
    private fun scheduleInPlaceResubscribe() {
        if (!liveSocketOpen) return
        resubscribeRunnable?.let { mainHandler.removeCallbacks(it) }
        val now = System.currentTimeMillis()
        val elapsed = now - lastSubscriptionSentMs
        val delayMs = (MIN_RESUBSCRIBE_INTERVAL_MS - elapsed).coerceAtLeast(0L)
        val runnable = Runnable {
            resubscribeRunnable = null
            val ws = webSocket
            if (liveSocketOpen && ws != null) {
                lastSubscriptionSentMs = System.currentTimeMillis()
                ws.send(resubscribeMessage().toString())
            }
        }
        resubscribeRunnable = runnable
        if (delayMs == 0L) {
            mainHandler.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    /**
     * Home waters (UK, Ireland, North Sea, NW Europe & Baltic) are always included
     * so moored RNLI/European lifeboats are never missed while panning locally.
     */
    private fun subscriptionBoxes(): JSONArray = JSONArray().apply {
        // Box 1: UK, Ireland, North Sea & NW Europe
        put(JSONArray().apply {
            put(JSONArray().apply { put(48.0); put(-12.0) })
            put(JSONArray().apply { put(62.0); put(12.0) })
        })
        // Box 2: Nordic / Baltic Sea
        put(JSONArray().apply {
            put(JSONArray().apply { put(54.0); put(4.0) })
            put(JSONArray().apply { put(71.0); put(31.0) })
        })
        // Extra viewport box when the user pans outside Europe
        viewportBox?.let { box ->
            put(JSONArray().apply {
                put(JSONArray().apply { put(box[0]); put(box[1]) })
                put(JSONArray().apply { put(box[2]); put(box[3]) })
            })
        }
    }

    private fun resubscribeMessage(): JSONObject = JSONObject().apply {
        put("APIKey", com.example.plane_tracker.BuildConfig.AIS_STREAM_API_KEY)
        put("BoundingBoxes", subscriptionBoxes())
        put("FilterMessageTypes", JSONArray().apply {
            put("PositionReport")
            put("StandardClassBPositionReport")
            put("ExtendedClassBPositionReport")
            put("ShipStaticData")
            put("StaticDataReport")
        })
    }

    private fun connect() {
        if (com.example.plane_tracker.BuildConfig.AIS_STREAM_API_KEY.isBlank()) return
        connectGeneration++
        liveSocketOpen = false
        val request = Request.Builder().url(STREAM_URL).build()
        val generation = connectGeneration
        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "AIS stream connected")
                reconnectAttempt = 0
                liveSocketOpen = generation == connectGeneration
                lastSubscriptionSentMs = System.currentTimeMillis()
                webSocket.send(resubscribeMessage().toString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handleMessage(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AIS msg: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "AIS stream closed: $code $reason")
                scheduleReconnect(generation)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "AIS stream failed: ${t.message}")
                scheduleReconnect(generation)
            }
        })
    }

    private fun handleMessage(text: String) {
        val msg = JSONObject(text)
        val meta = msg.optJSONObject("MetaData") ?: return
        val mmsi = meta.optString("MMSI").trim()
        if (mmsi.isBlank()) return

        var isNewVessel = false
        val vessel = vessels.getOrPut(mmsi) {
            isNewVessel = true
            Vessel(mmsi = mmsi)
        }

        var updated = false

        // 1. Read MetaData ShipName first so real broadcast names replace generic prefix placeholders
        val metaShipName = meta.optString("ShipName", "").trim()
        if (metaShipName.isNotEmpty() && (vessel.name.isBlank() || isPlaceholderName(vessel.name))) {
            vessel.name = metaShipName
            vessel.lifeboatCheckDirty = true
        }

        // 2. Fast-path known MMSI lookup
        if (isNewVessel || !vessel.isLifeboat) {
            val knownInfo = KnownLifeboats.getKnownDetails(mmsi)
            if (knownInfo != null) {
                val isExact = KnownLifeboats.isExactKnownLifeboat(mmsi)
                val excluded = EXCLUSIONS_REGEX.containsMatchIn(vessel.name) && !LIFEBOAT_REGEX.containsMatchIn(vessel.name)
                if (isExact || !excluded) {
                    vessel.isLifeboat = true
                    if (vessel.name.isBlank()) vessel.name = knownInfo.name
                    if (vessel.callSign.isBlank()) vessel.callSign = knownInfo.callSign
                    if (vessel.shipType == 0) vessel.shipType = knownInfo.shipType
                    if (!isValidCoord(vessel.latitude, vessel.longitude) &&
                        isValidCoord(knownInfo.defaultLat, knownInfo.defaultLon)
                    ) {
                        vessel.latitude = knownInfo.defaultLat
                        vessel.longitude = knownInfo.defaultLon
                    }
                    vessel.lifeboatCheckDirty = false
                    updated = true
                }
            }
        }

        // 3. Apply MetaData coordinates (filtering out 0.0, NaN, and AIS 91/181 sentinels)
        val metaLat = meta.optDouble("latitude", meta.optDouble("Latitude", Double.NaN))
        val metaLon = meta.optDouble("longitude", meta.optDouble("Longitude", Double.NaN))
        if (isValidCoord(metaLat, metaLon)) {
            vessel.latitude = metaLat
            vessel.longitude = metaLon
            vessel.lastSeen = System.currentTimeMillis()
            updated = true
        }

        // 4. Parse typed AIS message payload
        if (msg.has("Message")) {
            val messageNode = msg.getJSONObject("Message")
            when {
                messageNode.has("PositionReport") -> {
                    val pr = messageNode.getJSONObject("PositionReport")
                    updated = applyPosition(vessel, pr) || updated
                    applyKinematics(vessel, pr)
                    if (pr.has("NavigationalStatus")) {
                        vessel.navStatus = pr.optInt("NavigationalStatus", vessel.navStatus)
                    }
                }
                messageNode.has("StandardClassBPositionReport") -> {
                    val pr = messageNode.getJSONObject("StandardClassBPositionReport")
                    updated = applyPosition(vessel, pr) || updated
                    applyKinematics(vessel, pr)
                }
                messageNode.has("ExtendedClassBPositionReport") -> {
                    val pr = messageNode.getJSONObject("ExtendedClassBPositionReport")
                    updated = applyPosition(vessel, pr) || updated
                    applyKinematics(vessel, pr)
                    val name = pr.optString("Name", "").trim()
                    if (name.isNotEmpty() && name != vessel.name) {
                        vessel.name = name
                        vessel.lifeboatCheckDirty = true
                    }
                    val type = pr.optInt("Type", 0)
                    if (type > 0 && type != vessel.shipType) {
                        vessel.shipType = type
                        vessel.lifeboatCheckDirty = true
                    }
                    applyDimension(vessel, pr.optJSONObject("Dimension"))
                }
                messageNode.has("ShipStaticData") -> {
                    val ssd = messageNode.getJSONObject("ShipStaticData")
                    val name = ssd.optString("Name", "").trim()
                    if (name.isNotEmpty() && name != vessel.name) {
                        vessel.name = name
                        vessel.lifeboatCheckDirty = true
                    }
                    val cs = ssd.optString("CallSign", "").trim()
                    if (cs.isNotEmpty() && cs != vessel.callSign) {
                        vessel.callSign = cs
                        vessel.lifeboatCheckDirty = true
                    }
                    val dest = ssd.optString("Destination", "").trim()
                    if (dest.isNotEmpty()) vessel.destination = dest
                    val type = ssd.optInt("Type", 0)
                    if (type > 0 && type != vessel.shipType) {
                        vessel.shipType = type
                        vessel.lifeboatCheckDirty = true
                    }
                    val draught = ssd.optDouble("MaximumStaticDraught", 0.0)
                    if (!draught.isNaN() && draught > 0.0) vessel.draught = draught
                    applyDimension(vessel, ssd.optJSONObject("Dimension"))
                    val imo = ssd.optInt("ImoNumber", 0)
                    if (imo > 0) vessel.imoNumber = imo
                    updated = true
                }
                messageNode.has("StaticDataReport") -> {
                    val sdr = messageNode.getJSONObject("StaticDataReport")
                    if (sdr.has("ReportA")) {
                        val ra = sdr.getJSONObject("ReportA")
                        val name = ra.optString("Name", "").trim()
                        if (name.isNotEmpty() && name != vessel.name) {
                            vessel.name = name
                            vessel.lifeboatCheckDirty = true
                        }
                        val cs = ra.optString("CallSign", "").trim()
                        if (cs.isNotEmpty() && cs != vessel.callSign) {
                            vessel.callSign = cs
                            vessel.lifeboatCheckDirty = true
                        }
                        updated = true
                    }
                    if (sdr.has("ReportB")) {
                        val rb = sdr.getJSONObject("ReportB")
                        val csB = rb.optString("CallSign", "").trim()
                        if (csB.isNotEmpty() && csB != vessel.callSign) {
                            vessel.callSign = csB
                            vessel.lifeboatCheckDirty = true
                        }
                        val type = rb.optInt("ShipType", 0)
                        if (type > 0 && type != vessel.shipType) {
                            vessel.shipType = type
                            vessel.lifeboatCheckDirty = true
                        }
                        applyDimension(vessel, rb.optJSONObject("Dimension"))
                        updated = true
                    }
                }
            }
        }

        // 5. Evaluate lifeboat classification without un-classifying confirmed SAR vessels
        if (vessel.lifeboatCheckDirty) {
            val classified = checkIsLifeboat(vessel.name, vessel.callSign, vessel.shipType)
            val keepPrevious = vessel.isLifeboat &&
                (KnownLifeboats.isExactKnownLifeboat(mmsi) || vessel.shipType == 51 || !EXCLUSIONS_REGEX.containsMatchIn(vessel.name))
            vessel.isLifeboat = classified || keepPrevious
            vessel.lifeboatCheckDirty = false
            if (vessel.isLifeboat) {
                updated = true
            }
        }

        if (vessel.isLifeboat && isValidCoord(vessel.latitude, vessel.longitude)) {
            trailRecorder.record(mmsi, vessel.latitude, vessel.longitude, vessel.lastSeen)
        }

        if (updated && vessel.isLifeboat) {
            dirtyPersistence.add(mmsi)
            requestEmission()
        }
    }

    private fun isPlaceholderName(name: String): Boolean =
        name.startsWith("RNLI Lifeboat ") ||
            name.startsWith("KNRM Reddingboot ") ||
            name.startsWith("Sjöräddning Rescue ") ||
            name.startsWith("AIS-SART Beacon ") ||
            name.startsWith("Lifeboat ")

    private fun isValidCoord(lat: Double, lon: Double): Boolean =
        !lat.isNaN() && !lon.isNaN() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    private fun applyPosition(vessel: Vessel, pr: JSONObject): Boolean {
        val lat = pr.optDouble("Latitude", Double.NaN)
        val lon = pr.optDouble("Longitude", Double.NaN)
        if (isValidCoord(lat, lon)) {
            vessel.latitude = lat
            vessel.longitude = lon
            vessel.lastSeen = System.currentTimeMillis()
            return true
        }
        return false
    }

    private fun applyKinematics(vessel: Vessel, pr: JSONObject) {
        val sog = pr.optDouble("Sog", Double.NaN)
        if (!sog.isNaN() && sog in 0.0..102.2) {
            vessel.speedKnots = sog
        }
        val cog = pr.optDouble("Cog", Double.NaN)
        if (!cog.isNaN() && cog in 0.0..359.9) {
            vessel.heading = cog
        } else {
            val hdg = pr.optDouble("TrueHeading", Double.NaN)
            if (!hdg.isNaN() && hdg in 0.0..359.9) {
                vessel.heading = hdg
            }
        }
    }

    private fun applyDimension(vessel: Vessel, dim: JSONObject?) {
        if (dim == null) return
        val a = dim.optInt("A", 0)
        val b = dim.optInt("B", 0)
        val c = dim.optInt("C", 0)
        val d = dim.optInt("D", 0)
        if (a + b > 0) vessel.lengthMeters = a + b
        if (c + d > 0) vessel.widthMeters = c + d
    }

    private fun requestEmission() {
        if (emissionPending) return
        val now = System.currentTimeMillis()
        val sinceLast = now - lastEmitMs
        if (sinceLast >= EMIT_INTERVAL_MS) {
            lastEmitMs = now
            emitLifeboats()
        } else {
            emissionPending = true
            mainHandler.postDelayed({
                emissionPending = false
                lastEmitMs = System.currentTimeMillis()
                emitLifeboats()
            }, EMIT_INTERVAL_MS - sinceLast)
        }
    }

    private fun scheduleReconnect(failedGeneration: Int) {
        if (failedGeneration != connectGeneration) return
        if (!reconnecting.compareAndSet(false, true)) return
        val delayMs = RECONNECT_BASE_MS * (1L shl reconnectAttempt.coerceAtMost(5))
        reconnectAttempt++
        webSocket = null
        liveSocketOpen = false
        Log.i(TAG, "Reconnecting AIS stream in ${delayMs / 1000}s")
        mainHandler.postDelayed({
            reconnecting.set(false)
            connect()
        }, delayMs)
    }

    private fun checkIsLifeboat(name: String, callSign: String = "", type: Int = 0): Boolean {
        val text = "$name $callSign".trim()
        if (LIFEBOAT_REGEX.containsMatchIn(text)) {
            return true
        }
        if (EXCLUSIONS_REGEX.containsMatchIn(text)) {
            return false
        }
        return type == 51
    }

    fun stop() {
        connectGeneration++
        resubscribeRunnable?.let {
            mainHandler.removeCallbacks(it)
            resubscribeRunnable = null
        }
        webSocket?.close(1000, "User requested")
        webSocket = null
        liveSocketOpen = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun vesselTrail(mmsi: String): List<VesselTrailPoint> = trailRecorder.trailFor(mmsi)

    private fun emitLifeboats() {
        val now = System.currentTimeMillis()
        // Only evict non-lifeboat commercial vessels from memory so discovered lifeboats
        // and their station coordinates remain visible across 3-6 minute AIS intervals.
        vessels.entries.removeIf { !it.value.isLifeboat && now - it.value.lastSeen > STALE_NON_LIFEBOAT_MS }
        trailRecorder.retainAll(vessels.keys)

        val currentLifeboats = vessels.values
            .filter { it.isLifeboat && isValidCoord(it.latitude, it.longitude) }
            .map { it.copy() }
            .sortedBy { it.name.ifBlank { it.mmsi } }
            .toList()

        _lifeboats.value = currentLifeboats

        // Persist dirty lifeboats asynchronously off the WebSocket / UI threads
        if (lifeboatStore != null && dirtyPersistence.isNotEmpty()) {
            val toSave = dirtyPersistence.toList().mapNotNull { vessels[it]?.copy() }
            dirtyPersistence.clear()
            if (toSave.isNotEmpty()) {
                dbExecutor.execute {
                    lifeboatStore.saveAll(toSave)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AisRepository"
        private const val STREAM_URL = "wss://stream.aisstream.io/v0/stream"
        private const val VIEWPORT_PAD_DEG = 1.0
        private const val MIN_RESUBSCRIBE_INTERVAL_MS = 1_500L

        private val DEFAULT_BOX_UK_EUROPE = listOf(48.0, -12.0, 62.0, 12.0)
        private val DEFAULT_BOX_NORDIC = listOf(54.0, 4.0, 71.0, 31.0)

        fun isViewportInDefaultBoxes(
            minLat: Double,
            minLon: Double,
            maxLat: Double,
            maxLon: Double
        ): Boolean =
            isViewportContained(minLat, minLon, maxLat, maxLon, DEFAULT_BOX_UK_EUROPE) ||
                isViewportContained(minLat, minLon, maxLat, maxLon, DEFAULT_BOX_NORDIC)

        /**
         * Checks if the camera viewport [minLat, minLon, maxLat, maxLon] is fully
         * contained inside [box] ([latFrom, lonFrom, latTo, lonTo]).
         */
        fun isViewportContained(
            minLat: Double,
            minLon: Double,
            maxLat: Double,
            maxLon: Double,
            box: List<Double>?
        ): Boolean {
            if (box == null || box.size < 4) return false
            val latFrom = box[0]
            val lonFrom = box[1]
            val latTo = box[2]
            val lonTo = box[3]

            if (minLat < latFrom || maxLat > latTo) return false

            return if (lonTo >= lonFrom) {
                minLon >= lonFrom && maxLon <= lonTo
            } else {
                (minLon >= lonFrom || minLon <= lonTo) && (maxLon >= lonFrom || maxLon <= lonTo)
            }
        }

        /**
         * Clamps/caps a raw camera bounds rectangle into a single AIS box:
         * [latFrom, lonFrom, latTo, lonTo], padded and quantized outward to whole
         * degrees so the subscription covers the viewport plus a surrounding margin.
         */
        fun clampedViewportBox(
            minLat: Double,
            minLon: Double,
            maxLat: Double,
            maxLon: Double
        ): List<Double> {
            val paddedMinLat = (minLat - VIEWPORT_PAD_DEG).coerceIn(-90.0, 90.0)
            val paddedMaxLat = (maxLat + VIEWPORT_PAD_DEG).coerceIn(-90.0, 90.0)
            val paddedMinLon = minLon - VIEWPORT_PAD_DEG
            val paddedMaxLon = maxLon + VIEWPORT_PAD_DEG

            val latFrom = floor(paddedMinLat)
            val latTo = ceil(paddedMaxLat).coerceIn(latFrom, 90.0)

            val lonFrom = wrapLonStatic(floor(wrapLonStatic(paddedMinLon)))
            var lonTo = wrapLonStatic(ceil(wrapLonStatic(paddedMaxLon)))
            if (lonTo < lonFrom) lonTo += 360.0

            val latSpan = (latTo - latFrom).coerceAtMost(MAX_BOX_SPAN_DEG)
            val lonSpan = (lonTo - lonFrom).coerceAtMost(MAX_BOX_SPAN_DEG)
            val latMid = (latFrom + latTo) / 2.0
            val lonMid = (lonFrom + lonTo) / 2.0

            return listOf(
                roundHalfDown(latMid - latSpan / 2).coerceIn(-90.0, 90.0),
                wrapLonStatic(roundHalfDown(lonMid - lonSpan / 2)),
                roundHalfDown(latMid + latSpan / 2).coerceIn(-90.0, 90.0),
                wrapLonStatic(roundHalfDown(lonMid + lonSpan / 2))
            )
        }

        private fun roundHalfDown(v: Double): Double =
            floor(v + 0.5 - 1e-9)

        private fun wrapLonStatic(lon: Double): Double =
            ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

        private const val EMIT_INTERVAL_MS = 500L
        private const val PING_INTERVAL_S = 30L
        private const val STALE_NON_LIFEBOAT_MS = 10 * 60 * 1000L
        private const val RECONNECT_BASE_MS = 5_000L
        const val MAX_BOX_SPAN_DEG = 50.0

        private val LIFEBOAT_REGEX = Regex(
            "royal national lifeboat|\\brnli\\b|rnli\\d*|\\brnlb\\b|life ?boat|reddingboot|reddingsboot|knrm|dgzrs|seenotrett.*|seenotkreuzer|seenotretter|" +
                "snsm|sauvetage en mer|redningsselskapet|redningsskøyte|redningsskoyte|\\brs\\s*\\d{2,3}\\b|sjöräddning.*|\\bssrs\\b|\\bnsri\\b|rcmsar|" +
                "marine rescue|volunteer marine rescue|sea rescue|inshore rescue|humber rescue|search and rescue|search & rescue|\\bsar\\b|" +
                "^rescue\\s+[a-z0-9]|^\\d+\\s*lifeboat",
            RegexOption.IGNORE_CASE
        )

        private val EXCLUSIONS_REGEX = Regex(
            "\\btug\\b|supply|cargo|tanker|container|ferry|\\bpilot\\b|navy|warship|yacht|barge|workboat|dredger|fishing|trawler",
            RegexOption.IGNORE_CASE
        )
    }
}

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
import org.json.JSONArray
import org.json.JSONObject
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams live AIS vessel positions from aisstream.io and publishes the
 * lifeboat subset.
 *
 * Performance notes (this stream can be very chatty — thousands of messages
 * per minute across the bounding boxes):
 *  - UI emissions are throttled to at most one list update per [EMIT_INTERVAL_MS],
 *    so a burst of position reports can't flood recomposition.
 *  - The (expensive) lifeboat name regex only re-runs when a vessel's name or
 *    ship type actually changes; the verdict is cached on the vessel.
 *  - The stale-vessel cleanup sweep runs on the same throttle as emissions,
 *    never per message.
 *  - The socket self-heals: keepalive pings detect a silently-dead connection
 *    and failures/closes reconnect with exponential backoff.
 */
class AisRepository(private val client: OkHttpClient) {
    /**
     * Client dedicated to the stream with protocol-level pings enabled: pings
     * keep NAT timeouts at bay and fail the socket if a pong never comes back,
     * which surfaces as [onFailure] and a clean reconnect.
     */
    private val wsClient = client.newBuilder()
        .pingInterval(PING_INTERVAL_S, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val vessels = ConcurrentHashMap<String, Vessel>()
    private val _lifeboats = MutableStateFlow<List<Vessel>>(emptyList())
    val lifeboats: StateFlow<List<Vessel>> = _lifeboats.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null

    /** Prevents two concurrent reconnect chains after a rapid close+fail. */
    private val reconnecting = AtomicBoolean(false)
    private var reconnectAttempt = 0
    private var connectGeneration = 0

    /** Pending emission flag + timestamps for throttling list publishes. */
    @Volatile
    private var emissionPending = false
    @Volatile
    private var lastEmitMs = 0L

    fun start() {
        if (webSocket != null) return
        connect()
    }

    private fun connect() {
        connectGeneration++
        val request = Request.Builder().url(STREAM_URL).build()
        val generation = connectGeneration
        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "AIS stream connected")
                reconnectAttempt = 0
                val sub = JSONObject().apply {
                    put("APIKey", com.example.plane_tracker.BuildConfig.AIS_STREAM_API_KEY)
                    put("BoundingBoxes", JSONArray().apply {
                        // Home waters only: the app's audience and its rescue
                        // services (RNLI, KNRM, DGzRS, SSRS, Redningsselskapet)
                        // all live here. Subscribing worldwide pulled in the
                        // Mediterranean and US coasts — thousands of irrelevant
                        // messages a minute — which throttled parsing, drained
                        // battery and inflated the lifeboat count with vessels
                        // the user will never see on the UK-centred map.
                        // UK, Ireland, North Sea & NW Europe
                        put(JSONArray().apply {
                            put(JSONArray().apply { put(48.0); put(-12.0) })
                            put(JSONArray().apply { put(62.0); put(12.0) })
                        })
                        // Nordic / Baltic Sea (SSRS, KNRM, DGzRS, Redningsselskapet)
                        put(JSONArray().apply {
                            put(JSONArray().apply { put(54.0); put(4.0) })
                            put(JSONArray().apply { put(71.0); put(31.0) })
                        })
                    })
                    put("FilterMessageTypes", JSONArray().apply {
                        put("PositionReport")
                        put("StandardClassBPositionReport")
                        put("ExtendedClassBPositionReport")
                        put("ShipStaticData")
                        put("StaticDataReport")
                    })
                }
                webSocket.send(sub.toString())
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
        val mmsi = meta.optString("MMSI")
        if (mmsi.isBlank()) return

        val vessel = vessels.getOrPut(mmsi) { Vessel(mmsi = mmsi) }
        var updated = false

        val metaShipName = meta.optString("ShipName", "").trim()
        if (metaShipName.isNotEmpty() && vessel.name.isBlank()) {
            vessel.name = metaShipName
            vessel.lifeboatCheckDirty = true
        }

        val metaLat = meta.optDouble("latitude", meta.optDouble("Latitude", Double.NaN))
        val metaLon = meta.optDouble("longitude", meta.optDouble("Longitude", Double.NaN))
        if (!metaLat.isNaN() && !metaLon.isNaN() && metaLat != 0.0 && metaLon != 0.0) {
            vessel.latitude = metaLat
            vessel.longitude = metaLon
            vessel.lastSeen = System.currentTimeMillis()
            updated = true
        }

        if (msg.has("Message")) {
            val messageNode = msg.getJSONObject("Message")
            when {
                messageNode.has("PositionReport") -> {
                    val pr = messageNode.getJSONObject("PositionReport")
                    updated = applyPosition(vessel, pr) || updated
                    vessel.speedKnots = pr.optDouble("Sog", vessel.speedKnots)
                    vessel.heading = pr.optDouble("Cog", vessel.heading)
                    if (pr.has("NavigationalStatus")) {
                        vessel.navStatus = pr.optInt("NavigationalStatus", vessel.navStatus)
                    }
                }
                messageNode.has("StandardClassBPositionReport") -> {
                    val pr = messageNode.getJSONObject("StandardClassBPositionReport")
                    updated = applyPosition(vessel, pr) || updated
                    vessel.speedKnots = pr.optDouble("Sog", vessel.speedKnots)
                    vessel.heading = pr.optDouble("Cog", vessel.heading)
                }
                messageNode.has("ExtendedClassBPositionReport") -> {
                    val pr = messageNode.getJSONObject("ExtendedClassBPositionReport")
                    updated = applyPosition(vessel, pr) || updated
                    vessel.speedKnots = pr.optDouble("Sog", vessel.speedKnots)
                    vessel.heading = pr.optDouble("Cog", vessel.heading)
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
                    if (cs.isNotEmpty()) vessel.callSign = cs
                    val dest = ssd.optString("Destination", "").trim()
                    if (dest.isNotEmpty()) vessel.destination = dest
                    val type = ssd.optInt("Type", 0)
                    if (type > 0 && type != vessel.shipType) {
                        vessel.shipType = type
                        vessel.lifeboatCheckDirty = true
                    }
                    val draught = ssd.optDouble("MaximumStaticDraught", 0.0)
                    if (draught > 0.0) vessel.draught = draught
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
                        if (cs.isNotEmpty()) vessel.callSign = cs
                        updated = true
                    }
                    if (sdr.has("ReportB")) {
                        val rb = sdr.getJSONObject("ReportB")
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

        // Refresh the cached lifeboat verdict only when classification inputs
        // changed — the regex is too hot to run on every position report.
        if (vessel.lifeboatCheckDirty) {
            vessel.isLifeboat = checkIsLifeboat(vessel.name, vessel.shipType)
            vessel.lifeboatCheckDirty = false
            if (vessel.isLifeboat) updated = true
        }

        if (updated && vessel.isLifeboat) {
            requestEmission()
        }
    }

    private fun applyPosition(vessel: Vessel, pr: JSONObject): Boolean {
        val lat = pr.optDouble("Latitude", vessel.latitude)
        val lon = pr.optDouble("Longitude", vessel.longitude)
        if (lat != 0.0 && lon != 0.0) {
            vessel.latitude = lat
            vessel.longitude = lon
        }
        vessel.lastSeen = System.currentTimeMillis()
        return true
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

    /**
     * Coalesces lifeboat-list publishes to at most one per [EMIT_INTERVAL_MS].
     * A follow-up emission is scheduled immediately if messages arrive while
     * the throttle window is closed, so the UI never shows stale data for long.
     */
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

    /** Reconnects with exponential backoff (5s → 10s → 20s → … capped at 5 min). */
    private fun scheduleReconnect(failedGeneration: Int) {
        if (failedGeneration != connectGeneration) return // a newer socket owns the state
        if (webSocket == null || !reconnecting.compareAndSet(false, true)) return
        val delayMs = RECONNECT_BASE_MS * (1L shl reconnectAttempt.coerceAtMost(5))
        reconnectAttempt++
        webSocket = null
        Log.i(TAG, "Reconnecting AIS stream in ${delayMs / 1000}s")
        mainHandler.postDelayed({
            reconnecting.set(false)
            connect()
        }, delayMs)
    }

    private fun checkIsLifeboat(name: String, type: Int): Boolean {
        val n = name.trim()
        if (EXCLUSIONS_REGEX.containsMatchIn(n)) {
            return false
        }
        if (LIFEBOAT_REGEX.containsMatchIn(n)) {
            return true
        }
        if (type == 51 && n.isNotEmpty()) {
            return true
        }
        return false
    }

    fun stop() {
        connectGeneration++
        webSocket?.close(1000, "User requested")
        webSocket = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun emitLifeboats() {
        val now = System.currentTimeMillis()
        // Cleanup older than 30 minutes — piggybacks on the throttled emit
        // instead of scanning every message.
        vessels.entries.removeIf { now - it.value.lastSeen > STALE_MS }
        _lifeboats.value = vessels.values
            .filter { it.isLifeboat && it.latitude != 0.0 && it.longitude != 0.0 }
            .map { it.copy() }
            .toList()
    }

    companion object {
        private const val TAG = "AisRepository"
        private const val STREAM_URL = "wss://stream.aisstream.io/v0/stream"
        private const val EMIT_INTERVAL_MS = 2_000L
        private const val PING_INTERVAL_S = 30L
        private const val STALE_MS = 30 * 60 * 1000L
        private const val RECONNECT_BASE_MS = 5_000L

        private val LIFEBOAT_REGEX = Regex(
            "royal national lifeboat|rnli|rnli\\d*|life ?boat|reddingboot|reddingsboot|knrm|dgzrs|seenotrett.*|seenotkreuzer|seenotretter|" +
                "snsm|redningsselskapet|redningsskøyte|sjöräddningssällskapet|\\bssrs\\b|\\bnsri\\b|rcmsar|marine rescue|volunteer marine rescue|sea rescue|" +
                "^rescue\\s+[a-z0-9]|^\\d+\\s*lifeboat",
            RegexOption.IGNORE_CASE
        )

        private val EXCLUSIONS_REGEX = Regex(
            "\\bpatrol\\b|coastguard|coast guard|\\btug\\b|supply|cargo|tanker|container|ferry|\\bpilot\\b|\\bpolice\\b|navy|warship|yacht|barge|workboat|dredger",
            RegexOption.IGNORE_CASE
        )
    }
}

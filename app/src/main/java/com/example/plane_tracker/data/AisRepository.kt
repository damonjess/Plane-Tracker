package com.example.plane_tracker.data

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

class AisRepository(private val client: OkHttpClient) {
    private val vessels = ConcurrentHashMap<String, Vessel>()
    private val _lifeboats = MutableStateFlow<List<Vessel>>(emptyList())
    val lifeboats: StateFlow<List<Vessel>> = _lifeboats.asStateFlow()

    private var webSocket: WebSocket? = null

    fun start() {
        if (webSocket != null) return
        val request = Request.Builder().url("wss://stream.aisstream.io/v0/stream").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val sub = JSONObject().apply {
                    put("APIKey", com.example.plane_tracker.BuildConfig.AIS_STREAM_API_KEY)
                    put("BoundingBoxes", JSONArray().apply {
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
                        // Mediterranean & SW Europe
                        put(JSONArray().apply {
                            put(JSONArray().apply { put(35.0); put(-10.0) })
                            put(JSONArray().apply { put(46.0); put(36.0) })
                        })
                        // North America East Coast & Gulf
                        put(JSONArray().apply {
                            put(JSONArray().apply { put(24.0); put(-98.0) })
                            put(JSONArray().apply { put(48.0); put(-65.0) })
                        })
                        // Australia & NZ
                        put(JSONArray().apply {
                            put(JSONArray().apply { put(-45.0); put(110.0) })
                            put(JSONArray().apply { put(-10.0); put(180.0) })
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
                    val msg = JSONObject(text)
                    val meta = msg.optJSONObject("MetaData") ?: return
                    val mmsi = meta.optString("MMSI")
                    if (mmsi.isBlank()) return

                    val vessel = vessels.getOrPut(mmsi) { Vessel(mmsi = mmsi) }
                    var updated = false

                    val metaShipName = meta.optString("ShipName", "").trim()
                    if (metaShipName.isNotEmpty() && vessel.name.isBlank()) {
                        vessel.name = metaShipName
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
                        if (messageNode.has("PositionReport")) {
                            val pr = messageNode.getJSONObject("PositionReport")
                            val prLat = pr.optDouble("Latitude", vessel.latitude)
                            val prLon = pr.optDouble("Longitude", vessel.longitude)
                            if (prLat != 0.0 && prLon != 0.0) {
                                vessel.latitude = prLat
                                vessel.longitude = prLon
                            }
                            vessel.speedKnots = pr.optDouble("Sog", vessel.speedKnots)
                            vessel.heading = pr.optDouble("Cog", vessel.heading)
                            if (pr.has("NavigationalStatus")) {
                                vessel.navStatus = pr.optInt("NavigationalStatus", vessel.navStatus)
                            }
                            vessel.lastSeen = System.currentTimeMillis()
                            updated = true
                        } else if (messageNode.has("StandardClassBPositionReport")) {
                            val pr = messageNode.getJSONObject("StandardClassBPositionReport")
                            val prLat = pr.optDouble("Latitude", vessel.latitude)
                            val prLon = pr.optDouble("Longitude", vessel.longitude)
                            if (prLat != 0.0 && prLon != 0.0) {
                                vessel.latitude = prLat
                                vessel.longitude = prLon
                            }
                            vessel.speedKnots = pr.optDouble("Sog", vessel.speedKnots)
                            vessel.heading = pr.optDouble("Cog", vessel.heading)
                            vessel.lastSeen = System.currentTimeMillis()
                            updated = true
                        } else if (messageNode.has("ExtendedClassBPositionReport")) {
                            val pr = messageNode.getJSONObject("ExtendedClassBPositionReport")
                            val prLat = pr.optDouble("Latitude", vessel.latitude)
                            val prLon = pr.optDouble("Longitude", vessel.longitude)
                            if (prLat != 0.0 && prLon != 0.0) {
                                vessel.latitude = prLat
                                vessel.longitude = prLon
                            }
                            vessel.speedKnots = pr.optDouble("Sog", vessel.speedKnots)
                            vessel.heading = pr.optDouble("Cog", vessel.heading)
                            val name = pr.optString("Name", "").trim()
                            if (name.isNotEmpty()) vessel.name = name
                            val type = pr.optInt("Type", 0)
                            if (type > 0) vessel.shipType = type
                            val dim = pr.optJSONObject("Dimension")
                            if (dim != null) {
                                val a = dim.optInt("A", 0)
                                val b = dim.optInt("B", 0)
                                val c = dim.optInt("C", 0)
                                val d = dim.optInt("D", 0)
                                if (a + b > 0) vessel.lengthMeters = a + b
                                if (c + d > 0) vessel.widthMeters = c + d
                            }
                            vessel.lastSeen = System.currentTimeMillis()
                            updated = true
                        } else if (messageNode.has("ShipStaticData")) {
                            val ssd = messageNode.getJSONObject("ShipStaticData")
                            val name = ssd.optString("Name", "").trim()
                            if (name.isNotEmpty()) vessel.name = name
                            val cs = ssd.optString("CallSign", "").trim()
                            if (cs.isNotEmpty()) vessel.callSign = cs
                            val dest = ssd.optString("Destination", "").trim()
                            if (dest.isNotEmpty()) vessel.destination = dest
                            val type = ssd.optInt("Type", 0)
                            if (type > 0) vessel.shipType = type
                            val draught = ssd.optDouble("MaximumStaticDraught", 0.0)
                            if (draught > 0.0) vessel.draught = draught
                            val dim = ssd.optJSONObject("Dimension")
                            if (dim != null) {
                                val a = dim.optInt("A", 0)
                                val b = dim.optInt("B", 0)
                                val c = dim.optInt("C", 0)
                                val d = dim.optInt("D", 0)
                                if (a + b > 0) vessel.lengthMeters = a + b
                                if (c + d > 0) vessel.widthMeters = c + d
                            }
                            val imo = ssd.optInt("ImoNumber", 0)
                            if (imo > 0) vessel.imoNumber = imo
                            updated = true
                        } else if (messageNode.has("StaticDataReport")) {
                            val sdr = messageNode.getJSONObject("StaticDataReport")
                            if (sdr.has("ReportA")) {
                                val ra = sdr.getJSONObject("ReportA")
                                val name = ra.optString("Name", "").trim()
                                if (name.isNotEmpty()) vessel.name = name
                                val cs = ra.optString("CallSign", "").trim()
                                if (cs.isNotEmpty()) vessel.callSign = cs
                                updated = true
                            }
                            if (sdr.has("ReportB")) {
                                val rb = sdr.getJSONObject("ReportB")
                                val type = rb.optInt("ShipType", 0)
                                if (type > 0) vessel.shipType = type
                                val dim = rb.optJSONObject("Dimension")
                                if (dim != null) {
                                    val a = dim.optInt("A", 0)
                                    val b = dim.optInt("B", 0)
                                    val c = dim.optInt("C", 0)
                                    val d = dim.optInt("D", 0)
                                    if (a + b > 0) vessel.lengthMeters = a + b
                                    if (c + d > 0) vessel.widthMeters = c + d
                                }
                                updated = true
                            }
                        }
                    }

                    vessel.isLifeboat = checkIsLifeboat(vessel.name, vessel.shipType)

                    if (updated && vessel.isLifeboat) {
                        emitLifeboats()
                    }
                } catch (e: Exception) {
                    Log.e("AisRepository", "Error parsing AIS msg: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w("AisRepository", "WebSocket closed: $code $reason")
                this@AisRepository.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("AisRepository", "WebSocket failed: ${t.message}")
                this@AisRepository.webSocket = null
            }
        })
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
        webSocket?.close(1000, "User requested")
        webSocket = null
    }

    private fun emitLifeboats() {
        val now = System.currentTimeMillis()
        // Cleanup older than 30 minutes
        vessels.entries.removeIf { now - it.value.lastSeen > 30 * 60 * 1000 }
        _lifeboats.value = vessels.values.filter { it.isLifeboat && it.latitude != 0.0 && it.longitude != 0.0 }.toList()
    }

    companion object {
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

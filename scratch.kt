import okhttp3.*
import org.json.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main() {
    val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
    val request = Request.Builder().url("wss://stream.aisstream.io/v0/stream").build()
    client.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            println("Connected")
            val apiKey = "0e92db7d0cf0580ab1f6fd395dcec485585cab98"
            val msg = JSONObject().apply {
                put("APIKey", apiKey)
                put("BoundingBoxes", JSONArray().apply {
                    put(JSONArray().apply {
                        put(JSONArray().apply { put(48.0); put(-12.0) })
                        put(JSONArray().apply { put(62.0); put(12.0) })
                    })
                })
                put("FilterMessageTypes", JSONArray().apply {
                    put("PositionReport")
                })
            }
            webSocket.send(msg.toString())
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            println("MSG: ${text.take(100)}")
            exitProcess(0)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            println("Failure: ${t.message}")
            exitProcess(1)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            println("Closed: $code $reason")
            exitProcess(0)
        }
    })
    Thread.sleep(5000)
    println("Timeout")
    exitProcess(1)
}

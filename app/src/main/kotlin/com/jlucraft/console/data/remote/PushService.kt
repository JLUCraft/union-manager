package com.jlucraft.console.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Dual-channel push service: WebSocket (primary) + FCM (fallback). */
class PushService(
    serverUrl: String
) {
    @Volatile
    private var serverUrl: String = serverUrl

    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<WebSocketEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<WebSocketEvent> = _events

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    private var pushJob: Job? = null

    private val client = HttpClient(OkHttp) {
        install(WebSockets)
    }

    private var cachedWsUrl: String = wsUrl(serverUrl)

    private fun wsUrl(url: String): String {
        val hostPart = url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
        return "ws://$hostPart/v1/ws"
    }

    fun connect() {
        if (job?.isActive == true) return
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        job = newScope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                try {
                    client.webSocket(cachedWsUrl) {
                        backoffMs = INITIAL_BACKOFF_MS
                        while (true) {
                            when (val frame = incoming.receive()) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    try {
                                        val obj = json.decodeFromString<JsonObject>(text)
                                        val eventType = obj["type"]?.jsonPrimitive?.content ?: "unknown"
                                        _events.emit(WebSocketEvent(eventType, obj))
                                    } catch (_: Exception) {
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }

        if (pushJob?.isActive != true) {
            pushJob = newScope.launch {
                UnionPushReceiver.pushEvents.collect { event ->
                    _events.emit(event)
                }
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        pushJob?.cancel()
        pushJob = null
        scope?.cancel()
        scope = null
    }

    fun updateServerUrl(url: String) {
        if (url == serverUrl) return
        serverUrl = url
        cachedWsUrl = wsUrl(url)
        if (job?.isActive == true) {
            disconnect()
            connect()
        }
    }

    data class WebSocketEvent(
        val type: String,
        val data: JsonObject
    )

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}

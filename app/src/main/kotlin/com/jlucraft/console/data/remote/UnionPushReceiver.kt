package com.jlucraft.console.data.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** UnifiedPush messaging receiver — normalizes push events to [PushService.WebSocketEvent]. */
class UnionPushReceiver : MessagingReceiver() {

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        val text = message.content.decodeToString()
        val eventType = try {
            val obj = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(text)
            obj["type"]?.jsonPrimitive?.content ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        val payload = try {
            kotlinx.serialization.json.Json.decodeFromString<JsonObject>(text)
        } catch (_: Exception) {
            buildJsonObject { put("raw", text) }
        }
        _pushEvents.tryEmit(PushService.WebSocketEvent(eventType, payload))

        val (title, channel) = notificationMeta(eventType, payload)
        if (title != null) {
            NotificationHelper.show(context, title, notificationBody(eventType, payload), channel)
        }
    }

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        _endpointFlow.tryEmit(endpoint.url)
        _distributorInfo.tryEmit(resolveDistributorName(context, endpoint.url))
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        _pushEvents.tryEmit(
            PushService.WebSocketEvent(
                "registration_failed",
                buildJsonObject { put("reason", reason.name) }
            )
        )
    }

    override fun onUnregistered(context: Context, instance: String) {
        _endpointFlow.tryEmit("")
    }

    companion object {
        private val notificationEvents = setOf(
            "instance_crash", "node_offline", "alert_fired", "alert_resolved",
            "proposal_executed", "proposal_rejected", "match_dispute"
        )
        private val alertEvents = setOf("instance_crash", "node_offline", "alert_fired")

        private fun notificationMeta(eventType: String, payload: JsonObject): Pair<String?, String> {
            if (eventType !in notificationEvents) return null to NotificationHelper.CHANNEL_PUSH
            val channel = if (eventType in alertEvents) NotificationHelper.CHANNEL_ALERTS else NotificationHelper.CHANNEL_PUSH
            val title = when (eventType) {
                "instance_crash" -> "Instance Crash"
                "node_offline" -> "Node Offline"
                "alert_fired" -> "Alert Triggered"
                "alert_resolved" -> "Alert Resolved"
                "proposal_executed" -> "Proposal Executed"
                "proposal_rejected" -> "Proposal Rejected"
                "match_dispute" -> "Match Dispute"
                else -> null
            }
            return title to channel
        }

        private fun notificationBody(eventType: String, payload: JsonObject): String {
            val name = payload["name"]?.jsonPrimitive?.content
                ?: payload["instance_name"]?.jsonPrimitive?.content
                ?: payload["id"]?.jsonPrimitive?.content
                ?: payload["node_id"]?.jsonPrimitive?.content
            val message = payload["message"]?.jsonPrimitive?.content
            return when {
                name != null && message != null -> "$name: $message"
                name != null -> name
                message != null -> message
                else -> "Tap to view details"
            }
        }

        private val _pushEvents = MutableSharedFlow<PushService.WebSocketEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        val pushEvents: kotlinx.coroutines.flow.SharedFlow<PushService.WebSocketEvent> = _pushEvents

        private val _endpointFlow = MutableSharedFlow<String>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        val endpointFlow: kotlinx.coroutines.flow.SharedFlow<String> = _endpointFlow

        private val _distributorInfo = MutableSharedFlow<String>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        val distributorInfo: kotlinx.coroutines.flow.SharedFlow<String> = _distributorInfo

        fun setDistributorInfo(info: String) {
            _distributorInfo.tryEmit(info)
        }

        private fun resolveDistributorName(context: Context, endpointUrl: String): String {
            return if (endpointUrl.contains("fcm.googleapis.com")) {
                "Embedded FCM (内置)"
            } else {
                try {
                    val uri = java.net.URI(endpointUrl)
                    val host = uri.host ?: return "External Distributor"
                    when {
                        host.contains("ntfy") -> "ntfy"
                        host.contains("nextpush") -> "NextPush"
                        host.contains("gotify") -> "Gotify"
                        else -> "External Distributor"
                    }
                } catch (_: Exception) {
                    "External Distributor"
                }
            }
        }
    }
}

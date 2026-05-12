package com.jlucraft.console.data.remote

import android.content.Context
import android.util.Log
import com.jlucraft.console.data.model.AuthChallengeEventData
import com.jlucraft.console.data.model.AlertFiredEventData
import com.jlucraft.console.data.model.AlertResolvedEventData
import com.jlucraft.console.data.model.GenericPushEventData
import com.jlucraft.console.data.model.InstanceCrashEventData
import com.jlucraft.console.data.model.MatchEventData
import com.jlucraft.console.data.model.NodeOfflineEventData
import com.jlucraft.console.data.model.ProposalEventData
import com.jlucraft.console.data.model.PushEventPayload
import com.jlucraft.console.data.push.PushNotificationFormatter
import com.jlucraft.console.data.push.PushNotificationPolicy
import com.jlucraft.events.v1.EventEnvelope
import com.jlucraft.events.v1.PushNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

public open class UnionPushReceiver : MessagingReceiver() {

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        val (eventType, payload) = parseMessage(message)
        _pushEvents.tryEmit(PushService.PushEvent(eventType, payload))

        if (!PushNotificationPolicy.shouldNotify(eventType)) return

        val meta = PushNotificationFormatter.format(eventType, payload)
        if (meta.title != null) {
            NotificationHelper.show(
                context = context,
                title = meta.title,
                body = meta.body,
                channelId = meta.channelId,
                intentAction = meta.intentAction
            )
        }
    }

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        _endpointFlow.tryEmit(endpoint.url)
        _distributorInfo.tryEmit(resolveDistributorName(context, endpoint.url))
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        _pushEvents.tryEmit(
            PushService.PushEvent(
                "registration_failed",
                GenericPushEventData(raw = reason.name)
            )
        )
    }

    override fun onUnregistered(context: Context, instance: String) {
        _endpointFlow.tryEmit("")
    }

    companion object {
        private const val TAG = "UnionPushReceiver"


        private val notificationEvents = PushNotificationFormatter.NOTIFICATION_EVENTS


        private val alertEvents = PushNotificationFormatter.ALERT_EVENTS

        private fun parseMessage(message: PushMessage): Pair<String, PushEventPayload> {
            try {
                val notification = PushNotification.parseFrom(message.content)
                val type = when (notification.eventType) {
                    "AuthChallenge", "auth_challenge" -> "AuthChallenge"
                    "InstanceCrash", "instance_crash" -> "instance_crash"
                    else -> notification.eventType
                }
                if (type.isBlank()) throw IllegalArgumentException("empty push event type")
                val payload = GenericPushEventData(raw = notification.summary.ifEmpty { notification.body })
                return type to payload
            } catch (_: Exception) {
                Log.w(TAG, "PushNotification parse failed, falling back to EventEnvelope")
            }

            try {
                val envelope = EventEnvelope.parseFrom(message.content)
                val type = when (envelope.eventType) {
                    "auth_challenge" -> "AuthChallenge"
                    else -> envelope.eventType
                }
                if (type.isBlank()) throw IllegalArgumentException("empty envelope event type")
                val payload = mapProtoPayload(envelope)
                return type to payload
            } catch (_: Exception) {
                Log.w(TAG, "EventEnvelope parse also failed, returning unknown event")
                return "unknown" to GenericPushEventData(raw = "<proto: parse error>")
            }
        }

        private fun mapProtoPayload(envelope: EventEnvelope): PushEventPayload {
            return when (envelope.payloadCase) {
                EventEnvelope.PayloadCase.INSTANCE_UPDATE -> {
                    val p = envelope.instanceUpdate
                    InstanceCrashEventData(
                        instanceId = p.instanceId.nullIfEmpty(),
                        instanceName = p.name.nullIfEmpty(),
                        peerId = p.hostPeerId.nullIfEmpty(),
                        crashedAt = envelope.occurredAt.nullIfEmpty()
                    )
                }
                EventEnvelope.PayloadCase.NODE_READY -> {
                    val p = envelope.nodeReady
                    NodeOfflineEventData(
                        peerId = p.peerId.nullIfEmpty(),
                        lastSeen = p.startedAt.nullIfEmpty(),
                        offlineSince = envelope.occurredAt.nullIfEmpty()
                    )
                }
                EventEnvelope.PayloadCase.ALERT -> {
                    val p = envelope.alert
                    AlertFiredEventData(
                        alertId = p.alertId.nullIfEmpty(),
                        alertName = p.alertType.nullIfEmpty(),
                        description = p.message.nullIfEmpty(),
                        firedAt = envelope.occurredAt.nullIfEmpty()
                    )
                }
                EventEnvelope.PayloadCase.PROPOSAL -> {
                    val p = envelope.proposal
                    ProposalEventData(
                        proposalId = p.proposalId.nullIfEmpty(),
                        title = p.proposalType.nullIfEmpty(),
                        humanSummary = p.status.nullIfEmpty(),
                        actor = p.proposer.nullIfEmpty(),
                        actorPubkey = null
                    )
                }
                EventEnvelope.PayloadCase.AUTH_CHALLENGE -> {
                    val p = envelope.authChallenge
                    AuthChallengeEventData(
                        humanSummary = p.humanSummary.nullIfEmpty(),
                        actor = envelope.sourcePeerId.nullIfEmpty(),
                        actorPubkey = null,
                        cmdType = p.cmdType.nullIfEmpty()
                    )
                }
                EventEnvelope.PayloadCase.MATCH -> {
                    val p = envelope.match
                    MatchEventData(
                        matchId = p.matchId.nullIfEmpty(),
                        opponentName = p.status.nullIfEmpty()
                    )
                }
                EventEnvelope.PayloadCase.GENERIC -> GenericPushEventData(
                    raw = envelope.generic.attributesMap.entries.joinToString(", ") { "${it.key}=${it.value}" }
                        .ifEmpty { envelope.generic.eventType }
                )
                else -> GenericPushEventData(raw = "<proto: no payload>")
            }
        }

        private fun String.nullIfEmpty(): String? = if (isEmpty()) null else this

        private val _pushEvents = MutableSharedFlow<PushService.PushEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        val pushEvents: kotlinx.coroutines.flow.SharedFlow<PushService.PushEvent> = _pushEvents

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
                "Embedded FCM (\u5185\u7F6E)"
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
                    Log.w(TAG, "Failed to resolve distributor name for $endpointUrl")
                    "External Distributor"
                }
            }
        }
    }
}

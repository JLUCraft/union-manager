package com.jlucraft.console.data.remote

import android.content.Context
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
import com.jlucraft.events.v1.PushEventEnvelope
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
        /**
         * Event types that should produce notifications.
         * Kept for backward-compatibility; delegates to [PushNotificationFormatter.NOTIFICATION_EVENTS].
         */
        private val notificationEvents = PushNotificationFormatter.NOTIFICATION_EVENTS

        /**
         * Alert event types.
         * Kept for backward-compatibility; delegates to [PushNotificationFormatter.ALERT_EVENTS].
         */
        private val alertEvents = PushNotificationFormatter.ALERT_EVENTS

        private fun parseMessage(message: PushMessage): Pair<String, PushEventPayload> {
            try {
                val envelope = PushEventEnvelope.parseFrom(message.content)
                val type = when (envelope.eventType) {
                    "AuthChallenge", "auth_challenge" -> "AuthChallenge"
                    "InstanceCrash", "instance_crash" -> "instance_crash"
                    else -> envelope.eventType
                }
                val payload = mapProtoPayload(envelope)
                return type to payload
            } catch (_: Exception) {
                return "unknown" to GenericPushEventData(raw = "<proto: parse error>")
            }
        }

        private fun mapProtoPayload(envelope: PushEventEnvelope): PushEventPayload {
            return when (envelope.payloadCase) {
                PushEventEnvelope.PayloadCase.INSTANCE_CRASH -> {
                    val p = envelope.instanceCrash
                    InstanceCrashEventData(
                        instanceId = p.instanceId.nullIfEmpty(),
                        instanceName = p.instanceName.nullIfEmpty(),
                        peerId = p.peerId.nullIfEmpty(),
                        crashedAt = p.crashedAt.nullIfEmpty()
                    )
                }
                PushEventEnvelope.PayloadCase.NODE_OFFLINE -> {
                    val p = envelope.nodeOffline
                    NodeOfflineEventData(
                        peerId = p.peerId.nullIfEmpty(),
                        lastSeen = p.lastSeen.nullIfEmpty(),
                        offlineSince = p.offlineSince.nullIfEmpty()
                    )
                }
                PushEventEnvelope.PayloadCase.ALERT_FIRED -> {
                    val p = envelope.alertFired
                    AlertFiredEventData(
                        alertId = p.alertId.nullIfEmpty(),
                        alertName = p.alertName.nullIfEmpty(),
                        description = p.description.nullIfEmpty(),
                        firedAt = p.firedAt.nullIfEmpty()
                    )
                }
                PushEventEnvelope.PayloadCase.ALERT_RESOLVED -> {
                    val p = envelope.alertResolved
                    AlertResolvedEventData(
                        alertId = p.alertId.nullIfEmpty(),
                        alertName = p.alertName.nullIfEmpty(),
                        resolvedAt = p.resolvedAt.nullIfEmpty()
                    )
                }
                PushEventEnvelope.PayloadCase.PROPOSAL -> {
                    val p = envelope.proposal
                    ProposalEventData(
                        proposalId = p.proposalId.nullIfEmpty(),
                        title = p.title.nullIfEmpty(),
                        humanSummary = p.humanSummary.nullIfEmpty(),
                        actor = p.actor.nullIfEmpty(),
                        actorPubkey = p.actorPubkey.nullIfEmpty()
                    )
                }
                PushEventEnvelope.PayloadCase.AUTH_CHALLENGE -> {
                    val p = envelope.authChallenge
                    AuthChallengeEventData(
                        humanSummary = p.humanSummary.nullIfEmpty(),
                        actor = p.actor.nullIfEmpty(),
                        actorPubkey = p.actorPubkey.nullIfEmpty(),
                        cmdType = p.cmdType.nullIfEmpty()
                    )
                }
                PushEventEnvelope.PayloadCase.MATCH -> {
                    val p = envelope.match
                    MatchEventData(
                        matchId = p.matchId.nullIfEmpty(),
                        opponentName = p.opponentName.nullIfEmpty()
                    )
                }
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
                    "External Distributor"
                }
            }
        }
    }
}

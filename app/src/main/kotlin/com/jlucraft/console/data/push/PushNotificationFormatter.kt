package com.jlucraft.console.data.push

import com.jlucraft.console.data.model.*
import com.jlucraft.console.data.remote.NotificationHelper

object PushNotificationFormatter {

    val NOTIFICATION_EVENTS = setOf(
        "instance_crash", "node_offline", "alert_fired", "alert_resolved",
        "proposal_executed", "proposal_rejected", "match_dispute",
        "AuthChallenge", "TournamentCreated", "MatchResult"
    )

    val ALERT_EVENTS = setOf("instance_crash", "node_offline", "alert_fired")

    data class NotificationMeta(
        val title: String?,
        val channelId: String,
        val body: String,
        val intentAction: String?
    )

    fun format(eventType: String, payload: PushEventPayload): NotificationMeta {
        if (eventType !in NOTIFICATION_EVENTS) {
            return NotificationMeta(null, NotificationHelper.CHANNEL_PUSH, "", null)
        }

        val channelId = resolveChannel(eventType)
        val title = resolveTitle(eventType)
        val body = buildBody(eventType, payload)
        val intentAction = if (eventType == "AuthChallenge") "ACTION_AUTH_CHALLENGE" else null

        return NotificationMeta(title, channelId, body, intentAction)
    }

    private fun resolveChannel(eventType: String): String = when {
        eventType == "AuthChallenge" -> NotificationHelper.CHANNEL_AUTH_CHALLENGE
        eventType in ALERT_EVENTS -> NotificationHelper.CHANNEL_ALERTS
        else -> NotificationHelper.CHANNEL_PUSH
    }

    private fun resolveTitle(eventType: String): String? = when (eventType) {
        "instance_crash" -> "Instance Crash"
        "node_offline" -> "Node Offline"
        "alert_fired" -> "Alert Triggered"
        "alert_resolved" -> "Alert Resolved"
        "proposal_executed" -> "Proposal Executed"
        "proposal_rejected" -> "Proposal Rejected"
        "match_dispute" -> "Match Dispute"
        "AuthChallenge" -> "\uD83D\uDD10 \u8EAB\u4EFD\u9A8C\u8BC1\u8BF7\u6C42"
        "TournamentCreated" -> "\u8D5B\u4E8B\u5DF2\u521B\u5EFA"
        "MatchResult" -> "\u6BD4\u8D5B\u7ED3\u679C"
        else -> null
    }

    private fun buildBody(eventType: String, payload: PushEventPayload): String {
        val fields = extractFields(payload)
        val (name, message, actor) = fields

        return when (eventType) {
            "AuthChallenge" -> {
                val summary = message ?: "\u9700\u8981\u60A8\u7684\u786E\u8BA4\u4EE5\u5B8C\u6210\u64CD\u4F5C"
                val from = actor?.let { "\u6765\u81EA $it" } ?: ""
                "$summary $from".trim()
            }
            else -> when {
                name != null && message != null -> "$name: $message"
                name != null -> name
                message != null -> message
                else -> "Tap to view details"
            }
        }
    }

    private fun extractFields(payload: PushEventPayload): Triple<String?, String?, String?> = when (payload) {
        is InstanceCrashEventData -> Triple(payload.instanceId ?: payload.instanceName, payload.crashedAt, null)
        is NodeOfflineEventData -> Triple(payload.peerId, payload.offlineSince, null)
        is AlertFiredEventData -> Triple(payload.alertName ?: payload.alertId, payload.description, null)
        is AlertResolvedEventData -> Triple(payload.alertName ?: payload.alertId, payload.resolvedAt, null)
        is ProposalEventData -> Triple(payload.proposalId ?: payload.title, payload.humanSummary, payload.actor ?: payload.actorPubkey)
        is AuthChallengeEventData -> Triple(null, payload.humanSummary ?: payload.cmdType, payload.actor ?: payload.actorPubkey)
        is MatchEventData -> Triple(payload.opponentName ?: payload.matchId, null, null)
        is GenericPushEventData -> Triple(null, payload.raw, null)
    }
}

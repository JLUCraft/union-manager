package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface PushEventPayload

@Serializable
data class InstanceCrashEventData(
    @SerialName("instance_id") val instanceId: String? = null,
    @SerialName("instance_name") val instanceName: String? = null,
    @SerialName("peer_id") val peerId: String? = null,
    @SerialName("crashed_at") val crashedAt: String? = null
) : PushEventPayload

@Serializable
data class NodeOfflineEventData(
    @SerialName("peer_id") val peerId: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("offline_since") val offlineSince: String? = null
) : PushEventPayload

@Serializable
data class AlertFiredEventData(
    @SerialName("alert_id") val alertId: String? = null,
    @SerialName("alert_name") val alertName: String? = null,
    val description: String? = null,
    @SerialName("fired_at") val firedAt: String? = null
) : PushEventPayload

@Serializable
data class AlertResolvedEventData(
    @SerialName("alert_id") val alertId: String? = null,
    @SerialName("alert_name") val alertName: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null
) : PushEventPayload

@Serializable
data class ProposalEventData(
    @SerialName("proposal_id") val proposalId: String? = null,
    val title: String? = null,
    @SerialName("human_summary") val humanSummary: String? = null,
    val actor: String? = null,
    @SerialName("actor_pubkey") val actorPubkey: String? = null
) : PushEventPayload

@Serializable
data class AuthChallengeEventData(
    @SerialName("human_summary") val humanSummary: String? = null,
    val actor: String? = null,
    @SerialName("actor_pubkey") val actorPubkey: String? = null,
    @SerialName("cmd_type") val cmdType: String? = null
) : PushEventPayload

@Serializable
data class MatchEventData(
    @SerialName("match_id") val matchId: String? = null,
    @SerialName("opponent_name") val opponentName: String? = null
) : PushEventPayload

@Serializable
data class GenericPushEventData(
    val raw: String? = null
) : PushEventPayload

package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GovernanceEvent(
    @SerialName("event_id") val eventId: String,
    @SerialName("event_type") val eventType: GovernanceEventType,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("proposal_id") val proposalId: String? = null,
    @SerialName("actor_did") val actorDid: String? = null,
    val payload: GovernanceEventPayload? = null
)

@Serializable
enum class GovernanceEventType {
    @SerialName("proposal_created") PROPOSAL_CREATED,
    @SerialName("vote_cast") VOTE_CAST,
    @SerialName("proposal_executed") PROPOSAL_EXECUTED,
    @SerialName("proposal_rejected") PROPOSAL_REJECTED,
    @SerialName("lease_transferred") LEASE_TRANSFERRED,
    @SerialName("member_updated") MEMBER_UPDATED
}

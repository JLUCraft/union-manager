package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


 *
 *
@Serializable
data class DisputeMatch(
    @SerialName("dispute_id") val disputeId: String = "",
    @SerialName("match_id") val matchId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("submitted_by") val submittedBy: String? = null,
    val reason: String,
    @SerialName("evidence_urls") val evidenceUrls: List<String> = emptyList(),
    val status: String,
    val resolution: String? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null
) {

    val id: String get() = disputeId


    val evidence: List<String> get() = evidenceUrls


    val raisedBy: String get() = submittedBy ?: ""
}


@Serializable
data class CreateDisputeRequest(
    @SerialName("match_id") val matchId: String,
    val reason: String,
    @SerialName("evidence_urls") val evidenceUrls: List<String> = emptyList()
)


 *
@Serializable
data class ResolveDisputeRequest(
    val resolution: String,
    val note: String? = null
)

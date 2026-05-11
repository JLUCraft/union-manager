package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A dispute raised against a match result.
 *
 * Primary fields align with server `DisputeMatch`:
 *   dispute_id, tournament_id, match_id, status, reason,
 *   evidence_urls, submitted_by?, resolution?
 *
 * Compatibility properties (id, evidence, raisedBy) are derived
 * from the canonical fields so existing UI/ViewModel code keeps
 * building.
 */
@Serializable
data class DisputeMatch(
    @SerialName("dispute_id") val disputeId: String = "",
    @SerialName("match_id") val matchId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("submitted_by") val submittedBy: String? = null,
    val reason: String,
    @SerialName("evidence_urls") val evidenceUrls: List<String> = emptyList(),
    val status: String, // "open" | "under_review" | "resolved" | "dismissed"
    val resolution: String? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null
) {
    /** Backward-compatibility alias for [disputeId]. */
    val id: String get() = disputeId

    /** Backward-compatibility alias for [evidenceUrls]. */
    val evidence: List<String> get() = evidenceUrls

    /** Backward-compatibility alias for [submittedBy]. */
    val raisedBy: String get() = submittedBy ?: ""
}

/** Request to create a new dispute. */
@Serializable
data class CreateDisputeRequest(
    @SerialName("match_id") val matchId: String,
    val reason: String,
    @SerialName("evidence_urls") val evidenceUrls: List<String> = emptyList()
)

/** Request to resolve a dispute.
 *
 * Aligned with server POST /v1/disputes/{dispute_id}/resolve
 * which expects { resolution, note? } and returns DisputeMatch.
 * The server hardcodes the resolved status — do NOT send a status field.
 */
@Serializable
data class ResolveDisputeRequest(
    val resolution: String,
    val note: String? = null
)

package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Oracle-verified player score, retrieved from the cluster's oracle subsystem.
 *
 * The oracle periodically publishes score commitments (Merkle roots) to the
 * audit chain. Each player can request their individual score + proof from
 * GET /v1/oracle/scores/{player_id}, then locally verify the proof against
 * the published root.
 */
@Serializable
data class OracleScore(
    @SerialName("player_id") val playerId: String,
    val scores: Map<String, Double> = emptyMap(),
    @SerialName("aggregate_score") val aggregateScore: Double = 0.0,
    val proof: OracleProof,
    @SerialName("verified_at") val verifiedAt: String,
    @SerialName("server_verified") val serverVerified: Boolean = false
)

/**
 * Merkle proof that a player's score leaf is included in the published oracle root.
 * The client can locally verify by recomputing the root from [leafHash] through
 * [proofNodes] and comparing with the published [root].
 */
@Serializable
data class OracleProof(
    /** Published Merkle root (also committed to the audit chain). */
    val root: String,
    /** Ordered list of sibling hashes on the path from leaf to root. */
    @SerialName("proof_nodes") val proofNodes: List<String> = emptyList(),
    /** 0-based index of this player's leaf in the score tree. */
    @SerialName("leaf_index") val leafIndex: Int = 0,
    /** Hash of this player's individual score leaf (pre-image commitment). */
    @SerialName("leaf_hash") val leafHash: String,
    /** Hash algorithm used (default: "SHA-256"). */
    val algorithm: String = "SHA-256"
)

/**
 * Result of a local (client-side) oracle proof verification.
 */
data class OracleVerificationResult(
    /** True if the locally recomputed root matches [OracleProof.root]. */
    val locallyVerified: Boolean,
    /** True if the server confirmed the proof was verified server-side. */
    val serverVerified: Boolean,
    /** The recomputed root hash (for audit/review). */
    val computedRoot: String,
    /** Any warnings or informational messages from the verification process. */
    val messages: List<String> = emptyList()
)

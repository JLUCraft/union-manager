package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


 *
@Serializable
data class OracleScore(
    @SerialName("player_id") val playerId: String,
    val scores: Map<String, Double> = emptyMap(),
    @SerialName("aggregate_score") val aggregateScore: Double = 0.0,
    val proof: OracleProof,
    @SerialName("verified_at") val verifiedAt: String,
    @SerialName("server_verified") val serverVerified: Boolean = false
)


@Serializable
data class OracleProof(

    val root: String,

    @SerialName("proof_nodes") val proofNodes: List<String> = emptyList(),

    @SerialName("leaf_index") val leafIndex: Int = 0,

    @SerialName("leaf_hash") val leafHash: String,

    val algorithm: String = "SHA-256"
)


data class OracleVerificationResult(

    val locallyVerified: Boolean,

    val serverVerified: Boolean,

    val computedRoot: String,

    val messages: List<String> = emptyList()
)

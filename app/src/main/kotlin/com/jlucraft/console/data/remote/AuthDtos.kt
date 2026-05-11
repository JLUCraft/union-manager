package com.jlucraft.console.data.remote

import com.jlucraft.console.data.model.AuthPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    @SerialName("cmd_type") val cmd_type: String,
    val payload: AuthPayload,
    @SerialName("public_key") val public_key: String
)

@Serializable
data class AuthChallenge(
    @SerialName("challenge_id") val challenge_id: String,
    val nonce: String,
    @SerialName("issued_at") val issued_at: String,
    @SerialName("expires_at") val expires_at: String,
    @SerialName("ttl_seconds") val ttl_seconds: Long,
    @SerialName("cmd_type") val cmd_type: String,
    @SerialName("payload_hash") val payload_hash: String,
    @SerialName("human_summary") val human_summary: String,
    @SerialName("risk_level") val risk_level: String,
    @SerialName("required_role") val required_role: String
)

@Serializable
data class SignResponse(
    @SerialName("challenge_id") val challenge_id: String,
    @SerialName("device_id") val device_id: String,
    @SerialName("subject_did") val subject_did: String,
    @SerialName("signature_alg") val signature_alg: String = "Ed25519",
    val nonce: String,
    val signature: String
)

@Serializable
data class AuthResult(
    val success: Boolean,
    val message: String
)

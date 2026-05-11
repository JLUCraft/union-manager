package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A federation member with role, credential status, and registered devices. */
@Serializable
data class Member(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("display_name") val displayName: String,
    val role: String, // "president" | "admin" | "member" | "guest"
    @SerialName("club_code") val clubCode: String? = null,
    @SerialName("credential_status") val credentialStatus: String, // "valid" | "expired" | "revoked" | "pending"
    val devices: List<MemberDevice> = emptyList()
)

@Serializable
data class MemberDevice(
    @SerialName("device_id") val deviceId: String,
    @SerialName("public_key") val publicKey: String,
    val status: String, // "active" | "revoked"
    @SerialName("last_seen_at") val lastSeenAt: String? = null
)

/** Request body for granting a role to a member. */
@Serializable
data class GrantRoleRequest(
    @SerialName("subject_did") val subjectDid: String,
    val role: String
)

/** Request body for issuing/revoking a credential. */
@Serializable
data class CredentialActionRequest(
    @SerialName("subject_did") val subjectDid: String,
    val reason: String? = null
)

/** Response returned by POST /v1/vc/revoked — VC revocation by id or by subject DID.

 * Server returns one of two shapes:
 *   { revoked: true, vc_id: "...", revoked_at: "..." }
 *   { revoked: true, subject_did: "...", revoked_count: N }
 *
 * All optional fields (vcId, subjectDid, revokedCount, revokedAt) are nullable
 * to handle both variants gracefully.
 */
@Serializable
data class RevokeCredentialResponse(
    val revoked: Boolean,
    @SerialName("vc_id") val vcId: String? = null,
    @SerialName("subject_did") val subjectDid: String? = null,
    @SerialName("revoked_count") val revokedCount: Int? = null,
    @SerialName("revoked_at") val revokedAt: String? = null
)

// ── Lightweight admin member DTOs (P0-A: DTO alignment) ──
// These match the actual server response shapes for admin member endpoints:
//   GET /v1/admin/members        → [{subject_did, role}]
//   POST /v1/admin/members/{did}/role → {subject_did, granted_role, status}
//   POST /v1/admin/members/{did}/credentials → {subject_did, credential_type, proposal_id, status}

/** Compact member entry returned by GET /v1/admin/members. */
@Serializable
data class MemberSummary(
    @SerialName("subject_did") val subjectDid: String,
    val role: String
)

/** Response for POST /v1/admin/members/{did}/role — role grant confirmation. */
@Serializable
data class GrantRoleResponse(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("granted_role") val grantedRole: String,
    val status: String
)

/** Response for POST /v1/admin/members/{did}/credentials — credential issuance pending governance. */
@Serializable
data class IssueCredentialResponse(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("credential_type") val credentialType: String,
    @SerialName("proposal_id") val proposalId: String,
    val status: String
)

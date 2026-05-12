package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Member(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    @SerialName("club_code") val clubCode: String? = null,
    @SerialName("credential_status") val credentialStatus: String,
    val devices: List<MemberDevice> = emptyList()
)

@Serializable
data class MemberDevice(
    @SerialName("device_id") val deviceId: String,
    @SerialName("public_key") val publicKey: String,
    val status: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null
)


@Serializable
data class GrantRoleRequest(
    @SerialName("subject_did") val subjectDid: String,
    val role: String
)


@Serializable
data class CredentialActionRequest(
    @SerialName("subject_did") val subjectDid: String,
    val reason: String? = null
)



 *
@Serializable
data class RevokeCredentialResponse(
    val revoked: Boolean,
    @SerialName("vc_id") val vcId: String? = null,
    @SerialName("subject_did") val subjectDid: String? = null,
    @SerialName("revoked_count") val revokedCount: Int? = null,
    @SerialName("revoked_at") val revokedAt: String? = null
)








@Serializable
data class MemberSummary(
    @SerialName("subject_did") val subjectDid: String,
    val role: String
)


@Serializable
data class GrantRoleResponse(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("granted_role") val grantedRole: String,
    val status: String
)


@Serializable
data class IssueCredentialResponse(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("credential_type") val credentialType: String,
    @SerialName("proposal_id") val proposalId: String,
    val status: String
)


@Serializable
data class IssueCredentialRequest(
    @SerialName("subject_did") val subjectDid: String,
    val role: String = "member",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("club_code") val clubCode: String? = null,
    val permissions: List<String> = emptyList(),
    @SerialName("member_since") val memberSince: String? = null
)




data class VcTemplate(
    val id: String,
    val name: String,
    val role: String = "member",
    val displayName: String = "",
    val clubCode: String? = null,
    val permissions: List<String> = emptyList(),
    val memberSince: String? = null
)

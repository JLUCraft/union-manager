package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


 *
 *
@Serializable
data class VerifiableCredential(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    val type: List<String> = listOf("VerifiableCredential", "UnionMemberCredential"),
    val id: String,
    val issuer: String,
    @SerialName("issuance_date") val issuanceDate: String,
    @SerialName("expiration_date") val expirationDate: String? = null,
    @SerialName("credential_subject") val credentialSubject: CredentialSubject,
    val proof: VcProof? = null
)

@Serializable
data class CredentialSubject(
    val id: String,
    val role: String,
    @SerialName("club_code") val clubCode: String? = null,
    @SerialName("display_name") val displayName: String,
    val permissions: List<String> = emptyList(),
    @SerialName("member_since") val memberSince: String? = null
)

@Serializable
data class VcProof(
    val type: String = "Ed25519Signature2020",
    val created: String,
    @SerialName("verification_method") val verificationMethod: String,
    @SerialName("proof_purpose") val proofPurpose: String = "assertionMethod",
    @SerialName("proof_value") val proofValue: String
)




 *
 *
@Serializable
data class DidDocument(
    val id: String,
    @SerialName("verification_method") val verificationMethod: List<DidVerificationMethod> = emptyList(),
    val authentication: List<String> = emptyList(),
    @SerialName("assertion_method") val assertionMethod: List<String> = emptyList(),
    val service: List<DidService> = emptyList()
)

@Serializable
data class DidVerificationMethod(
    val id: String,
    val type: String = "Ed25519VerificationKey2020",
    val controller: String,
    @SerialName("public_key_multibase") val publicKeyMultibase: String
)

@Serializable
data class DidService(
    val id: String,
    val type: String,
    @SerialName("service_endpoint") val serviceEndpoint: String
)




 *
@Serializable
data class LocalAdminRegistration(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    val pubkey: String,
    @SerialName("credential_id") val credentialId: String? = null,
    @SerialName("registered_at_ms") val registeredAtMs: Long = System.currentTimeMillis()
)




 *
@Serializable
data class DidServerResponse(
    @SerialName("did_document") val didDocument: DidDocument? = null,
    @SerialName("did_document_metadata") val didDocumentMetadata: DidDocumentMetadata? = null,
    @SerialName("did_resolution_metadata") val didResolutionMetadata: DidResolutionMetadata? = null
)

@Serializable
data class DidDocumentMetadata(
    val created: String? = null
)

@Serializable
data class DidResolutionMetadata(
    @SerialName("content_type") val contentType: String? = null,
    val error: String? = null,
    @SerialName("error_message") val errorMessage: String? = null
)


sealed interface DidResolutionError {
    val did: String
    val message: String

    data class InvalidDid(override val did: String, override val message: String) : DidResolutionError
    data class NotFound(override val did: String, override val message: String) : DidResolutionError
    data class MethodNotSupported(override val did: String, override val message: String) : DidResolutionError
    data class ResolutionFailed(override val did: String, override val message: String) : DidResolutionError
    data class NetworkError(override val did: String, override val message: String) : DidResolutionError
}




sealed interface DidResolutionResult {
    data class Success(val document: DidDocument) : DidResolutionResult
    data class NotFound(val did: String) : DidResolutionResult
    data class Invalid(val did: String, val reason: String) : DidResolutionResult
    data class Error(val did: String, val cause: Throwable) : DidResolutionResult

    data class ServerError(val did: String, val error: DidResolutionError) : DidResolutionResult
}


@Serializable
data class VcVerificationResult(
    val valid: Boolean,
    val issuer: String? = null,
    val subject: String? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)




@Serializable
data class DidResolveRequest(
    val did: String
)


@Serializable
data class DidResolveResponse(
    @SerialName("did_document") val didDocument: DidDocument,
    @SerialName("resolver_metadata") val resolverMetadata: Map<String, String>? = null
)


@Serializable
data class VcVerifyRequest(
    @SerialName("vc_jwt") val vcJwt: String,
    @SerialName("vc_json") val vcJson: VerifiableCredential
)

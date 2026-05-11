package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * W3C Verifiable Credential minimal data model for Union Manager federation.
 * Used for member credential issuance, verification, and VC-based instance admission.
 *
 * Note on serialization: The server serializes VC fields in snake_case
 * (e.g. "issuance_date", "expiration_date") rather than W3C standard camelCase.
 * This is a deliberate server convention for API consistency.
 * External VC issuers following W3C standard should be normalized at the API gateway.
 *
 * See design §2.3 (Credential lifecycle) and §4.4 (AdmissionPolicy).
 */
@Serializable
data class VerifiableCredential(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    val type: List<String> = listOf("VerifiableCredential", "UnionMemberCredential"),
    val id: String,
    val issuer: String,                          // DID of the issuing admin/club
    @SerialName("issuance_date") val issuanceDate: String,
    @SerialName("expiration_date") val expirationDate: String? = null,
    @SerialName("credential_subject") val credentialSubject: CredentialSubject,
    val proof: VcProof? = null
)

@Serializable
data class CredentialSubject(
    val id: String,                              // subject DID
    val role: String,                            // "president" | "admin" | "member" | "guest"
    @SerialName("club_code") val clubCode: String? = null,
    @SerialName("display_name") val displayName: String,
    val permissions: List<String> = emptyList(), // e.g. ["create-tournament", "sign-proposal"]
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

// ── DID Resolution infrastructure ──

/**
 * DID document minimal data model for local DID resolution.
 *
 * Supports did:key and did:web methods as used in the Union Manager federation.
 * The client can resolve a DID to its public key for signature verification.
 *
 * Note: The server uses snake_case serialization for all DID document fields.
 * This is consistent with the server API convention, not W3C DID Core camelCase.
 */
@Serializable
data class DidDocument(
    val id: String,                              // e.g. "did:key:z6Mk..."
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

// ── Local registration ──

/**
 * Local registration entry for an AdminCredential/DID pair.
 * Cached after first successful authentication to avoid repeated DID resolution.
 *
 * The [registeredAt] field defaults to the current time on construction.
 * On deserialization, kotlinx.serialization calls the constructor with
 * the serialized value if present, or falls back to the default expression
 * (current time) — which means a restored registration's timestamp will
 * reflect the restore time, not the original registration time.
 * For accurate timestamps, store [registeredAtMs] in persistent storage.
 */
@Serializable
data class LocalAdminRegistration(
    @SerialName("subject_did") val subjectDid: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    val pubkey: String,
    @SerialName("credential_id") val credentialId: String? = null,
    @SerialName("registered_at_ms") val registeredAtMs: Long = System.currentTimeMillis()
)

// ── DID Server Response ──

/**
 * Response from server DID resolution endpoint.
 *
 * Error semantics are conveyed via error codes in the response body:
 *  - 400 invalidDid
 *  - 404 notFound
 *  - 501 methodNotSupported
 *  - 502 resolutionFailed
 */
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

/**
 * Client-side structured DID resolution error.
 */
sealed interface DidResolutionError {
    val did: String
    val message: String

    data class InvalidDid(override val did: String, override val message: String) : DidResolutionError
    data class NotFound(override val did: String, override val message: String) : DidResolutionError
    data class MethodNotSupported(override val did: String, override val message: String) : DidResolutionError
    data class ResolutionFailed(override val did: String, override val message: String) : DidResolutionError
    data class NetworkError(override val did: String, override val message: String) : DidResolutionError
}

// ── Resolution / Verification results ──

/**
 * Result of a local DID resolution attempt.
 */
sealed interface DidResolutionResult {
    data class Success(val document: DidDocument) : DidResolutionResult
    data class NotFound(val did: String) : DidResolutionResult
    data class Invalid(val did: String, val reason: String) : DidResolutionResult
    data class Error(val did: String, val cause: Throwable) : DidResolutionResult
    /** Server-side structured error. */
    data class ServerError(val did: String, val error: DidResolutionError) : DidResolutionResult
}

/**
 * VC verification result from local or remote verification.
 */
@Serializable
data class VcVerificationResult(
    val valid: Boolean,
    val issuer: String? = null,
    val subject: String? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

// ── API request/response types ──

/**
 * Request body sent to server for remote DID resolution (did:web).
 */
@Serializable
data class DidResolveRequest(
    val did: String
)

/**
 * Response from server DID resolution endpoint.
 */
@Serializable
data class DidResolveResponse(
    @SerialName("did_document") val didDocument: DidDocument,
    @SerialName("resolver_metadata") val resolverMetadata: Map<String, String>? = null
)

/**
 * Request body sent to server for VC verification.
 * Protocol: flat JSON `{ "vc_jwt": "<credential-id>", "vc_json": <VerifiableCredential> }`.
 */
@Serializable
data class VcVerifyRequest(
    @SerialName("vc_jwt") val vcJwt: String,
    @SerialName("vc_json") val vcJson: VerifiableCredential
)

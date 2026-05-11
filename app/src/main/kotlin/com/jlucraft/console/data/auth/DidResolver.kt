package com.jlucraft.console.data.auth

import com.jlucraft.console.data.model.DidDocument
import com.jlucraft.console.data.model.DidResolutionError
import com.jlucraft.console.data.model.DidResolutionResult
import com.jlucraft.console.data.model.VcVerificationResult
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Local DID resolver and VC verifier.
 *
 * Supports:
 *  - did:key resolution (offline — extract public key from the DID identifier)
 *  - did:web resolution (online — via libp2p DID endpoint)
 *  - VC signature verification against resolved DID documents
 *
 * This is a client-side complement to the server's DID/VC infrastructure.
 * The server remains authoritative for federation-wide verification.
 */
class DidResolver(
    private val client: Libp2pClient
) {
    private val cache = ConcurrentHashMap<String, DidResolutionResult>()
    private val cacheMutex = Mutex()

    suspend fun resolve(did: String): DidResolutionResult {
        cache[did]?.let { return it }

        return cacheMutex.withLock {
            cache[did]?.let { return@withLock it }

            val result = try {
                when {
                    did.startsWith("did:key:") -> resolveDidKey(did)
                    did.startsWith("did:web:") -> resolveDidWeb(did)
                    else -> resolveRemote(did)
                }
            } catch (e: Exception) {
                DidResolutionResult.Error(did, e)
            }
            cache[did] = result
            result
        }
    }

    suspend fun verifyVcProof(
        verificationMethodId: String,
        credentialJson: String,
        signatureB64: String
    ): VcVerificationResult {
        val issuerDid = verificationMethodId.substringBefore("#")

        val resolution = resolve(issuerDid)
        if (resolution !is DidResolutionResult.Success) {
            return VcVerificationResult(
                valid = false,
                issuer = issuerDid,
                errors = listOf("Cannot resolve issuer DID: ${(resolution as? DidResolutionResult.Error)?.cause?.message ?: resolution}")
            )
        }

        val document = resolution.document

        val vm = document.verificationMethod.firstOrNull { it.id == verificationMethodId }
            ?: document.verificationMethod.firstOrNull()
            ?: return VcVerificationResult(
                valid = false,
                issuer = issuerDid,
                errors = listOf("Verification method '$verificationMethodId' not found in DID document")
            )

        val publicKey = try {
            decodeMultibaseEd25519(vm.publicKeyMultibase)
        } catch (e: Exception) {
            return VcVerificationResult(
                valid = false,
                issuer = issuerDid,
                errors = listOf("Failed to decode public key: ${e.message}")
            )
        }

        val payloadBytes = credentialJson.toByteArray(Charsets.UTF_8)
        val verified = try {
            val signer = Signature.getInstance("Ed25519")
            signer.initVerify(publicKey)
            signer.update(payloadBytes)
            signer.verify(Base64.getDecoder().decode(signatureB64))
        } catch (e: Exception) {
            return VcVerificationResult(
                valid = false,
                issuer = issuerDid,
                errors = listOf("Signature verification error: ${e.message}")
            )
        }

        return VcVerificationResult(
            valid = verified,
            issuer = issuerDid,
            subject = document.id,
            errors = if (verified) emptyList() else listOf("Signature mismatch"),
            warnings = if (vm.id != verificationMethodId)
                listOf("Verification method mismatch: used ${vm.id} instead of $verificationMethodId")
            else emptyList()
        )
    }

    // ── Private helpers ──

    private fun resolveDidKey(did: String): DidResolutionResult {
        val multibase = did.removePrefix("did:key:")
        if (multibase.length < 2) return DidResolutionResult.Invalid(did, "Invalid did:key format")

        val prefix = multibase.first()
        val encoded = multibase.substring(1)

        if (prefix != 'z') {
            return DidResolutionResult.Invalid(did, "Unsupported multibase encoding: $prefix")
        }

        val raw = try {
            decodeBase58Btc(encoded)
        } catch (e: Exception) {
            return DidResolutionResult.Invalid(did, "Invalid base58btc encoding: ${e.message}")
        }

        if (raw.size < 2 || raw[0] != 0xED.toByte() || raw[1] != 0x01.toByte()) {
            return DidResolutionResult.Invalid(did, "Unsupported multicodec: expected ed25519-pub (0xed01)")
        }

        val ed25519Key = raw.sliceArray(2 until raw.size)
        if (ed25519Key.size != 32) {
            return DidResolutionResult.Invalid(did, "Invalid Ed25519 public key length: ${ed25519Key.size}")
        }

        val publicKeyMultibase = buildPublicKeyMultibase(ed25519Key)

        val vm = com.jlucraft.console.data.model.DidVerificationMethod(
            id = "$did#${ed25519Key.take(4).joinToString("") { "%02x".format(it) }}",
            type = "Ed25519VerificationKey2020",
            controller = did,
            publicKeyMultibase = publicKeyMultibase
        )

        return DidResolutionResult.Success(
            DidDocument(
                id = did,
                verificationMethod = listOf(vm),
                authentication = listOf(vm.id),
                assertionMethod = listOf(vm.id)
            )
        )
    }

    private suspend fun resolveDidWeb(did: String): DidResolutionResult {
        val response = client.resolveDid(did)
        return response.fold(
            onSuccess = { serverResponse ->
                val document = serverResponse.didDocument
                if (document != null) {
                    DidResolutionResult.Success(document)
                } else {
                    DidResolutionResult.ServerError(
                        did,
                        DidResolutionError.NotFound(did, "Server returned empty DID document")
                    )
                }
            },
            onFailure = { DidResolutionResult.ServerError(did, classifyDidError(did, it)) }
        )
    }

    private suspend fun resolveRemote(did: String): DidResolutionResult {
        val response = client.resolveDid(did)
        return response.fold(
            onSuccess = { serverResponse ->
                val document = serverResponse.didDocument
                if (document != null) {
                    DidResolutionResult.Success(document)
                } else {
                    val meta = serverResponse.didResolutionMetadata
                    val errorType = meta?.error
                    if (errorType == "methodNotSupported") {
                        val errorMessage = meta.errorMessage ?: "Unsupported DID method"
                        DidResolutionResult.ServerError(
                            did,
                            DidResolutionError.MethodNotSupported(did, errorMessage)
                        )
                    } else {
                        DidResolutionResult.ServerError(
                            did,
                            DidResolutionError.NotFound(did, "Server returned empty DID document")
                        )
                    }
                }
            },
            onFailure = { DidResolutionResult.ServerError(did, classifyDidError(did, it)) }
        )
    }

    private fun classifyDidError(did: String, error: Throwable): DidResolutionError {
        val msg = error.message ?: "Unknown error"
        return when {
            msg.contains("400") || msg.contains("Bad Request") ->
                DidResolutionError.InvalidDid(did, msg)
            msg.contains("404") || msg.contains("Not Found") ->
                DidResolutionError.NotFound(did, msg)
            msg.contains("501") || msg.contains("Not Implemented") ->
                DidResolutionError.MethodNotSupported(did, msg)
            msg.contains("502") || msg.contains("Bad Gateway") ->
                DidResolutionError.ResolutionFailed(did, msg)
            else ->
                DidResolutionError.NetworkError(did, msg)
        }
    }

    private fun decodeBase58Btc(encoded: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val base = alphabet.length
        var result = 0.toBigInteger()
        for (char in encoded) {
            val digit = alphabet.indexOf(char)
            if (digit < 0) throw IllegalArgumentException("Invalid base58 character: $char")
            result = result * base.toBigInteger() + digit.toBigInteger()
        }
        val leadingZeros = encoded.takeWhile { it == '1' }.count()
        val bytes = result.toByteArray()
        val start = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) 1 else 0
        val resultBytes = bytes.sliceArray(start until bytes.size)
        return ByteArray(leadingZeros + resultBytes.size).apply {
            resultBytes.copyInto(this, leadingZeros)
        }
    }

    private fun buildPublicKeyMultibase(rawKey: ByteArray): String {
        val multicodec = byteArrayOf(0xED.toByte(), 0x01.toByte())
        val combined = multicodec + rawKey
        return "z${encodeBase58Btc(combined)}"
    }

    private fun encodeBase58Btc(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val base = alphabet.length
        var num = 0.toBigInteger()
        for (byte in data) {
            num = num * 256.toBigInteger() + (byte.toInt() and 0xFF).toBigInteger()
        }
        if (num == 0.toBigInteger()) return alphabet[0].toString()

        val result = StringBuilder()
        while (num > 0.toBigInteger()) {
            val (quotient, remainder) = num.divideAndRemainder(base.toBigInteger())
            result.insert(0, alphabet[remainder.toInt()])
            num = quotient
        }

        val leadingZeros = data.takeWhile { it == 0.toByte() }.count()
        return "1".repeat(leadingZeros) + result.toString()
    }

    private fun decodeMultibaseEd25519(multibase: String): PublicKey {
        if (multibase.length < 3) throw IllegalArgumentException("Invalid multibase key")

        val prefix = multibase.first()
        val encoded = multibase.substring(1)

        val raw = when (prefix) {
            'z' -> decodeBase58Btc(encoded)
            else -> throw IllegalArgumentException("Unsupported multibase prefix: $prefix")
        }

        if (raw.size < 2 || raw[0] != 0xED.toByte() || raw[1] != 0x01.toByte()) {
            throw IllegalArgumentException("Not an Ed25519 public key")
        }
        val keyBytes = raw.sliceArray(2 until raw.size)

        val spki = byteArrayOf(
            0x30.toByte(), 0x2A.toByte(), 0x30.toByte(), 0x05.toByte(),
            0x06.toByte(), 0x03.toByte(), 0x2B.toByte(), 0x65.toByte(),
            0x70.toByte(), 0x03.toByte(), 0x21.toByte(), 0x00.toByte()
        ) + keyBytes

        val spec = X509EncodedKeySpec(spki)
        val kf = KeyFactory.getInstance("Ed25519")
        return kf.generatePublic(spec)
    }
}

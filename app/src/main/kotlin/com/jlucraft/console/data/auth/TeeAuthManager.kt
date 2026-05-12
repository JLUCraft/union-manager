package com.jlucraft.console.data.auth

import android.content.Context
import android.util.Log
import android.security.keystore.KeyInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.remote.SignResponse
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class TeeAuthManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "TeeAuthManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jlucraft_tee_ed25519_key"
        private const val PROBE_KEY_ALIAS = "jlucraft_tee_probe_ed25519_key"


         *
         *
        fun buildCanonicalChallengeMessage(challenge: AuthChallenge): ByteArray {
            return "JLUCraftAuthV1||${challenge.challenge_id}||${challenge.nonce}||${challenge.cmd_type}||${challenge.payload_hash}||${challenge.expires_at}"
                .toByteArray(Charsets.UTF_8)
        }
    }


    var capability: TeeCapability
        private set


    val isTeeBacked: Boolean get() = capability !is TeeCapability.NoHardwareBackedKey

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }


    private val keyLock = ReentrantLock()

    @Volatile
    private var cachedPublicKey: String? = null

    init {
        capability = detectCapability()
    }


     *
     *
    private fun detectCapability(): TeeCapability {
        val hasStrongBox = context.packageManager
            .hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (hasStrongBox) {
            return TeeCapability.StrongBoxAvailable
        }

        return try {
            probeTeeCapability()
        } catch (e: Exception) {
            TeeCapability.NoHardwareBackedKey(
                "无法验证 TEE 能力: ${e.javaClass.simpleName} — ${e.message ?: "unknown"}"
            )
        }
    }


    private fun probeTeeCapability(): TeeCapability {
        val builder = KeyGenParameterSpec.Builder(
            PROBE_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN
        )
            .setDigests(KeyProperties.DIGEST_NONE)

        val generator: KeyPairGenerator
        try {
            generator = KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
        } catch (e: Exception) {
            return TeeCapability.NoHardwareBackedKey(
                "Ed25519 KeyPairGenerator 不可用: ${e.javaClass.simpleName}"
            )
        }

        try {
            generator.initialize(builder.build())
            val keyPair = generator.generateKeyPair()
            val keyFactory = KeyFactory.getInstance(keyPair.private.algorithm, ANDROID_KEYSTORE)
            val keyInfo = keyFactory.getKeySpec(keyPair.private, KeyInfo::class.java)

            return when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ->
                    TeeCapability.TeeOnlyAvailable
                KeyProperties.SECURITY_LEVEL_STRONGBOX ->
                    TeeCapability.StrongBoxAvailable
                else ->
                    TeeCapability.NoHardwareBackedKey(
                        "Ed25519 探测密钥安全级别为 ${keyInfo.securityLevel}，非 TEE/StrongBox"
                    )
            }
        } catch (e: ProviderException) {
            return TeeCapability.NoHardwareBackedKey(
                "Keystore 操作失败: ${e.message ?: "ProviderException"}"
            )
        } catch (e: Exception) {
            return TeeCapability.NoHardwareBackedKey(
                "TEE 探测失败: ${e.javaClass.simpleName}"
            )
        } finally {
            try {
                keyStore.deleteEntry(PROBE_KEY_ALIAS)
            } catch (_: Exception) {
                Log.w(TAG, "Probe key deletion failed during TEE capability detection")
            }
        }
    }


    private fun ensureKeyPairOnce() {
        if (!isTeeBacked) {
            throw IllegalStateException(
                "TEE key generation is not supported: ${(capability as? TeeCapability.NoHardwareBackedKey)?.reason}"
            )
        }

        keyLock.withLock {

            if (hasKeyUnsafe()) return

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN
            )
                .setDigests(KeyProperties.DIGEST_NONE)

            if (capability is TeeCapability.StrongBoxAvailable) {
                builder.setIsStrongBoxBacked(true)
            }

            val generator = KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
            generator.initialize(builder.build())
            try {
                val keyPair = generator.generateKeyPair()
                verifyHardwareCapability(keyPair.private)
                cachedPublicKey = null
            } catch (e: java.security.ProviderException) {

                if (!hasKeyUnsafe()) {
                    throw IllegalStateException("Failed to generate TEE key pair", e)
                }
            }
        }
    }


    private fun hasKeyUnsafe(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (_: Exception) {
            Log.w(TAG, "Failed to check key alias in Keystore")
            false
        }
    }

    fun hasKey(): Boolean = keyLock.withLock { hasKeyUnsafe() }

    private fun verifyHardwareCapability(privateKey: PrivateKey) {
        val keyFactory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
        val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
        capability = when (keyInfo.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> TeeCapability.StrongBoxAvailable
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> TeeCapability.TeeOnlyAvailable
            else -> {
                keyStore.deleteEntry(KEY_ALIAS)
                TeeCapability.NoHardwareBackedKey(
                    "Ed25519 key is not hardware-backed (securityLevel=${keyInfo.securityLevel})"
                )
            }
        }

        if (!isTeeBacked) {
            throw IllegalStateException((capability as TeeCapability.NoHardwareBackedKey).reason)
        }
    }


    fun sign(message: ByteArray): ByteArray {
        if (!isTeeBacked) {
            throw IllegalStateException("Signing is not available: hardware-backed key unavailable")
        }
        ensureKeyPairOnce()
        val entry = keyStore.getEntry(KEY_ALIAS, null)
            ?: throw IllegalStateException("TEE key not found in Keystore")

        val privateKey = (entry as KeyStore.PrivateKeyEntry).privateKey
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(message)
        return signer.sign()
    }


    fun trySign(message: ByteArray): Result<ByteArray> = runCatching { sign(message) }


    fun getPublicKey(): String {
        if (!isTeeBacked) {
            throw IllegalStateException("Public key not available: hardware-backed key unavailable")
        }
        return keyLock.withLock {
            cachedPublicKey?.let { return@withLock it }
            ensureKeyPairOnce()
            val entry = keyStore.getEntry(KEY_ALIAS, null)
                ?: throw IllegalStateException("TEE key not found in Keystore")

            val publicKey = (entry as KeyStore.PrivateKeyEntry).certificate.publicKey
            val encoded = Base64.getEncoder().encodeToString(publicKey.encoded)
            cachedPublicKey = encoded
            encoded
        }
    }


    fun tryGetPublicKey(): Result<String> = runCatching { getPublicKey() }




     *
     *
     *
    fun signCanonicalChallenge(
        challenge: AuthChallenge,
        deviceId: String,
        subjectDid: String
    ): Result<SignResponse> {
        return trySign(buildCanonicalChallengeMessage(challenge)).map { sigBytes ->
            SignResponse(
                challenge_id = challenge.challenge_id,
                device_id = deviceId,
                subject_did = subjectDid,
                signature_alg = "Ed25519",
                nonce = challenge.nonce,
                signature = Base64.getEncoder().encodeToString(sigBytes)
            )
        }
    }

}

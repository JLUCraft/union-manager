package com.jlucraft.console.data.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.Base64

/** Ed25519 authentication backed by Android Keystore (TEE/StrongBox). */
class TeeAuthManager(
    private val context: Context
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jlucraft_tee_ed25519_key"
    }

    /** True when the device supports TEE-backed Ed25519 key generation. */
    val isTeeBacked: Boolean

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    @Volatile
    private var cachedPublicKey: String? = null

    init {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "TEEAuthManager requires Android 13+ (API 33) for Ed25519 Keystore support. " +
                "Current API level: ${Build.VERSION.SDK_INT}"
        }
        isTeeBacked = true
    }

    private fun ensureKeyPair() {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN
        )
            .setKeySize(256)
            .setDigests(KeyProperties.DIGEST_NONE)
            .setUserAuthenticationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val hasStrongBox = context.packageManager
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            builder.setIsStrongBoxBacked(hasStrongBox)
        }

        val generator = KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
        generator.initialize(builder.build())
        try {
            generator.generateKeyPair()
            cachedPublicKey = null
        } catch (_: java.security.ProviderException) {
            // Key already exists or other keystore error; ignore
        }
    }

    /**
     * Sign the given message using the TEE-resident Ed25519 key.
     * The private key never leaves the Keystore.
     */
    fun sign(message: ByteArray): ByteArray {
        ensureKeyPair()
        val entry = keyStore.getEntry(KEY_ALIAS, null)
            ?: throw IllegalStateException("TEE key not found in Keystore")

        val privateKey = (entry as KeyStore.PrivateKeyEntry).privateKey

        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(message)
        return signer.sign()
    }

    /** Returns the Base64-encoded Ed25519 public key. */
    fun getPublicKey(): String {
        cachedPublicKey?.let { return it }
        ensureKeyPair()
        val entry = keyStore.getEntry(KEY_ALIAS, null)
            ?: throw IllegalStateException("TEE key not found in Keystore")

        val publicKey = (entry as KeyStore.PrivateKeyEntry).certificate.publicKey
        return Base64.getEncoder().encodeToString(publicKey.encoded).also {
            cachedPublicKey = it
        }
    }

    fun hasKey(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (_: Exception) {
            false
        }
    }
}

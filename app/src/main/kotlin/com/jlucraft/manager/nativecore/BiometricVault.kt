package com.jlucraft.manager.nativecore

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.jlucraft.manager.R
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only authenticated ciphers can unwrap an identity. No plaintext key is written. */
class BiometricVault(private val activity: Activity) {
    private val alias = "union-device-wrap-v1"
    private val stored = AtomicFile(File(activity.noBackupFilesDir, "union-device.enc"))
    private var pending: CancellationSignal? = null
    private var generation = 0L

    fun hasIdentity(): Boolean = stored.baseFile.exists() || File(stored.baseFile.path + ".bak").exists()

    fun cancel() {
        generation++
        pending?.cancel()
        pending = null
    }

    fun authenticate(title: String, success: (ByteArray) -> Unit, failure: (String) -> Unit) {
        cancel()
        val current = generation
        val manager = activity.getSystemService(BiometricManager::class.java)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            failure(activity.getString(R.string.vault_unavailable))
            return
        }
        try {
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            // Never regenerate a lost or invalidated wrapping key for an existing identity.
            val existing = stored.baseFile.exists() || File(stored.baseFile.path + ".bak").exists()
            if (!keys.containsAlias(alias)) {
                check(!existing) { activity.getString(R.string.vault_recovery_required) }
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                        .setInvalidatedByBiometricEnrollment(true)
                        .build())
                    generateKey()
                }
            }
            val key = keys.getKey(alias, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val envelope = if (existing) stored.openRead().use {
                it.readNBytes(8193).also { bytes -> check(bytes.size in 30..8192 && bytes[0] == 1.toByte()) }
            } else null
            if (envelope == null) cipher.init(Cipher.ENCRYPT_MODE, key)
            else cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
            val signal = CancellationSignal()
            pending = signal
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(activity.getString(R.string.vault_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton(activity.getString(R.string.vault_cancel), activity.mainExecutor) { _, _ ->
                    if (generation == current) { pending = null; failure(activity.getString(R.string.vault_cancelled)) }
                }
                .build().authenticate(BiometricPrompt.CryptoObject(cipher), signal, activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(code: Int, message: CharSequence) {
                            if (generation == current) { pending = null; failure(message.toString()) }
                        }
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (generation != current) return
                            pending = null
                            var secret: ByteArray? = null
                            try {
                                val unlocked = checkNotNull(result.cryptoObject?.cipher)
                                // Per-use keys cannot process AAD until this operation has an auth token.
                                unlocked.updateAAD("jlucraft-manager-identity-v1".toByteArray(Charsets.UTF_8))
                                val material = if (envelope != null) unlocked.doFinal(envelope.copyOfRange(13, envelope.size))
                                    else UnionNative.generateSecret()
                                secret = material
                                check(material.size in 1..4096)
                                UnionNative.peerId(material) // Validate the sole supported native identity format.
                                if (envelope == null) {
                                    val encrypted = byteArrayOf(1) + unlocked.iv + unlocked.doFinal(material)
                                    val output = stored.startWrite()
                                    try { output.write(encrypted); stored.finishWrite(output) }
                                    catch (error: Exception) { stored.failWrite(output); throw error }
                                }
                                success(material)
                            } catch (error: Exception) {
                                failure(activity.getString(R.string.vault_unlock_failed))
                            } finally { secret?.fill(0) }
                        }
                    })
        } catch (error: Exception) {
            failure(activity.getString(R.string.vault_locked))
        }
    }
}

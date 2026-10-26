package com.jlucraft.manager.nativecore

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.jlucraft.manager.BuildConfig
import com.jlucraft.manager.R
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One authenticated identity, two wrappers:
 * - the identity itself stays behind a fresh biometric prompt for explicit writes;
 * - the remembered session is sealed by a device-unlocked Keystore key so plain reads,
 *   identity synchronization and the saved business connection can run without another dialog.
 * The session is bound to the exact encrypted identity file, so replacing that file invalidates it.
 */
class BiometricVault(private val activity: Activity) {
    private val alias = "union-device-wrap-v1"
    private val sessionAlias = "union-device-session-v1"
    private val stored = AtomicFile(File(activity.noBackupFilesDir, "union-device.enc"))
    private val remembered = AtomicFile(File(activity.noBackupFilesDir, "union-device.session.enc"))
    private val sessionAad = "jlucraft-manager-session-v1".toByteArray(Charsets.UTF_8)
    private var pending: CancellationSignal? = null
    private var generation = 0L

    fun hasIdentity(): Boolean = stored.exists()

    fun hasRememberedSession(): Boolean = remembered.exists()

    /** Both the enrolled identity and its sealed session must be present; read errors stay visible. */
    fun hasRestorableSession(): Boolean = hasIdentity() && hasRememberedSession()

    fun cancel() {
        generation++
        pending?.cancel()
        pending = null
    }

    /**
     * Decrypt the remembered identity without a biometric prompt. The key is only usable while the
     * device is unlocked, and the sealed payload must still match the encrypted identity file.
     */
    fun restoreSession(): ByteArray? {
        if (!hasIdentity()) return null
        return readRememberedSession()
    }

    fun authenticate(title: String, success: (ByteArray) -> Unit, failure: (String) -> Unit, onUnavailable: () -> Unit = {}) {
        cancel()
        // Debug acceptance, when switched on, uses only the already enrolled sealed session.
        if (BuildConfig.DEBUG && BuildConfig.SKIP_BIOMETRIC_FOR_ACCEPTANCE) {
            val material = runCatching { restoreSession() }.getOrNull()
            if (material == null) {
                failure(activity.getString(R.string.vault_recovery_required))
                return
            }
            try { success(material) } finally { material.fill(0) }
            return
        }
        val current = generation
        val manager = activity.getSystemService(BiometricManager::class.java)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnavailable()
            val weakOnly = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
            failure(activity.getString(if (weakOnly) R.string.vault_weak_biometric else R.string.vault_unavailable))
            return
        }
        try {
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            // Never regenerate a lost or invalidated wrapping key for an existing identity.
            val existing = hasIdentity()
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
                                    restrictToOwner(stored.baseFile)
                                    restrictToOwner(File(stored.baseFile.path + ".bak"))
                                }
                                // The initial successful authentication is also the enrollment of the
                                // remembered session. Explicit writes keep using this prompt.
                                val sealed = sealRememberedSession(material)
                                // An existing encrypted identity does not imply its remembered session
                                // was ever enrolled, so every successful authentication must seal it.
                                check(sealed)
                                // Later launches read only the sealed session, so no plaintext
                                // identity file is kept on the device.
                                runCatching { File(activity.noBackupFilesDir, "union-device.debug").delete() }
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

    private fun readRememberedSession(): ByteArray? {
        if (!remembered.exists()) return null
        var payload: ByteArray? = null
        try {
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keys.containsAlias(sessionAlias)) return null
            val key = keys.getKey(sessionAlias, null) as SecretKey
            val sealed = remembered.openRead().use { it.readNBytes(8193) }
            check(sealed.size in 30..8192 && sealed[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed.copyOfRange(1, 13)))
            cipher.updateAAD(sessionAad)
            payload = cipher.doFinal(sealed.copyOfRange(13, sealed.size))
            check(payload.size in 34..4129 && payload[0] == 1.toByte())
            val binding = identityBinding()
            check(MessageDigest.isEqual(binding, payload.copyOfRange(1, 33)))
            val secret = payload.copyOfRange(33, payload.size)
            check(secret.size in 1..4096)
            try {
                UnionNative.peerId(secret)
            } catch (error: Exception) {
                secret.fill(0)
                throw error
            }
            return secret
        } catch (_: Exception) {
            return null
        } finally {
            payload?.fill(0)
        }
    }

    private fun sealRememberedSession(secret: ByteArray): Boolean {
        if (!hasIdentity()) return false
        val binding = runCatching { identityBinding() }.getOrNull() ?: return false
        val payload = ByteArray(33 + secret.size)
        payload[0] = 1.toByte()
        binding.copyInto(payload, 1)
        secret.copyInto(payload, 33)
        return try {
            val key = sessionKey(create = true) ?: return false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(sessionAad)
            val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(payload)
            val output = remembered.startWrite()
            try {
                output.write(encrypted)
                remembered.finishWrite(output)
            } catch (error: Exception) {
                remembered.failWrite(output)
                throw error
            }
            restrictToOwner(remembered.baseFile)
            restrictToOwner(File(remembered.baseFile.path + ".bak"))
            true
        } catch (_: Exception) {
            false
        } finally {
            payload.fill(0)
        }
    }

    private fun sessionKey(create: Boolean): SecretKey? {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keys.containsAlias(sessionAlias)) {
            if (!create) return null
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(sessionAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .setUnlockedDeviceRequired(true)
                    .build())
                generateKey()
            }
        }
        return keys.getKey(sessionAlias, null) as SecretKey
    }

    private fun identityBinding(): ByteArray {
        val bytes = stored.openRead().use { it.readBytes() }
        return try {
            MessageDigest.getInstance("SHA-256").digest(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun restrictToOwner(file: File) {
        if (!file.exists()) return
        runCatching { Os.chmod(file.path, OsConstants.S_IRUSR or OsConstants.S_IWUSR) }
    }

    private fun AtomicFile.exists(): Boolean =
        baseFile.exists() || File(baseFile.path + ".bak").exists()
}

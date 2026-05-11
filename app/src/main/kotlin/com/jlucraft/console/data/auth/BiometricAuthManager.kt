package com.jlucraft.console.data.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BiometricAuthManager(
    private val context: Context
) {
    @Volatile
    var fragmentActivity: FragmentActivity? = null

    suspend fun authenticate(
        title: String = "生物认证",
        subtitle: String = "请验证身份以继续操作"
    ): BiometricResult = suspendCancellableCoroutine { continuation ->
        val activity = fragmentActivity
            ?: run {
                continuation.resume(BiometricResult.Error("需要 FragmentActivity 上下文"))
                return@suspendCancellableCoroutine
            }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    continuation.resume(BiometricResult.Success)
                }

                override fun onAuthenticationFailed() {
                    continuation.resume(BiometricResult.Failed)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    continuation.resume(BiometricResult.Error(errString.toString()))
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("取消")
            .setConfirmationRequired(true)
            .build()

        prompt.authenticate(info)
    }

    fun isAvailable(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(context)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

sealed class BiometricResult {
    object Success : BiometricResult()
    object Failed : BiometricResult()
    data class Error(val message: String) : BiometricResult()
}

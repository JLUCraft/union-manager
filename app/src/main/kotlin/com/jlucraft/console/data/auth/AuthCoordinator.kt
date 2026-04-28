package com.jlucraft.console.data.auth

import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.SignResponse
import kotlinx.serialization.json.JsonObject
import java.util.Base64

class AuthCoordinator(
    private val api: ApiService,
    private val teeAuth: TeeAuthManager,
    private val biometricAuth: BiometricAuthManager
) {

    private fun biometricError(bioResult: BiometricResult): Exception =
        Exception(
            when (bioResult) {
                is BiometricResult.Error -> bioResult.message
                else -> "生物认证失败"
            }
        )

    suspend fun authenticateForOperation(
        cmdType: String,
        payload: JsonObject,
        title: String = "生物认证",
        subtitle: String = "请验证身份以继续操作"
    ): Result<Unit> {
        val bioResult = biometricAuth.authenticate(title, subtitle)
        if (bioResult != BiometricResult.Success) {
            return Result.failure(biometricError(bioResult))
        }

        val publicKey = teeAuth.getPublicKey()
        val request = AuthRequest(
            cmd_type = cmdType,
            payload = payload,
            public_key = publicKey
        )

        val challengeResult = api.requestChallenge(request)
        val challenge = challengeResult.getOrElse {
            return Result.failure(Exception("获取挑战失败: ${it.message}"))
        }

        val message = "${challenge.nonce}|$cmdType|${challenge.payload_hash}".toByteArray()
        val signature = try {
            val sigBytes = teeAuth.sign(message)
            Base64.getEncoder().encodeToString(sigBytes)
        } catch (e: Exception) {
            return Result.failure(Exception("签名失败: ${e.message}"))
        }

        val response = SignResponse(
            nonce = challenge.nonce,
            signature = signature
        )

        val verifyResult = api.verifySignature(response)
        val authResult = verifyResult.getOrElse {
            return Result.failure(Exception("验证签名失败: ${it.message}"))
        }

        return if (authResult.success) {
            api.setAuthHeaders(challenge.nonce, signature)
            Result.success(Unit)
        } else {
            Result.failure(Exception(authResult.message))
        }
    }

    fun clearAuth() {
        api.clearAuthHeaders()
    }

    /** Biometric-gated TEE signing (no server challenge). */
    suspend fun signWithBiometric(
        title: String,
        subtitle: String,
        message: ByteArray
    ): Result<String> {
        val bioResult = biometricAuth.authenticate(title, subtitle)
        if (bioResult != BiometricResult.Success) {
            return Result.failure(biometricError(bioResult))
        }
        return try {
            val sigBytes = teeAuth.sign(message)
            Result.success(Base64.getEncoder().encodeToString(sigBytes))
        } catch (e: Exception) {
            Result.failure(Exception("签名失败: ${e.message}"))
        }
    }
}

suspend inline fun <R> AuthCoordinator.withAuthenticatedOperation(
    cmdType: String,
    payload: JsonObject,
    title: String = "生物认证",
    subtitle: String = "请验证身份以继续操作",
    operation: () -> R
): Result<R> {
    val authResult = authenticateForOperation(cmdType, payload, title, subtitle)
    if (authResult.isFailure) {
        return Result.failure(authResult.exceptionOrNull()!!)
    }
    return try {
        Result.success(operation())
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        clearAuth()
    }
}

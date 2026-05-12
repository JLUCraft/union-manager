package com.jlucraft.console.data.auth

import com.jlucraft.console.data.model.AuthPayload
import com.jlucraft.console.data.model.LocalAdminRegistration
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.SignResponse
import com.jlucraft.console.data.remote.libp2p.AuthContext
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import java.util.Base64

enum class AuthRiskLevel(val minRequired: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    companion object {
        fun fromString(s: String): AuthRiskLevel =
            entries.firstOrNull { it.minRequired.equals(s, ignoreCase = true) } ?: MEDIUM
    }
}

object RequiredRoleGate {
    val ADMIN_GATED = setOf(
        "revoke-device", "emergency-revoke-device", "grant-role",
        "revoke-credential", "execute-proposal", "archive-season"
    )

    val MEMBER_GATED = setOf(
        "create-proposal", "sign-proposal", "create-tournament",
        "start-instance", "stop-instance", "migrate-instance",
        "delete-instance", "create-instance"
    )

    fun preCheck(cmdType: String, userRole: String): Boolean {
        val normalizedRole = userRole.lowercase()
        return when {
            cmdType in ADMIN_GATED -> normalizedRole in setOf("admin", "president")
            cmdType in MEMBER_GATED -> normalizedRole in setOf("admin", "president", "member")
            else -> true
        }
    }

    fun roleLevel(role: String): Int {
        return when (role.lowercase()) {
            "president" -> 3
            "admin" -> 2
            "member" -> 1
            else -> 0
        }
    }

}

class AuthCoordinator(
    private val client: Libp2pClient,
    private val teeAuth: TeeAuthManager,
    private val biometricAuth: BiometricAuthManager
) {
    @Volatile
    var localRegistration: LocalAdminRegistration? = null
        private set

    private fun biometricError(bioResult: BiometricResult): Exception =
        Exception(
            when (bioResult) {
                is BiometricResult.Error -> bioResult.message
                else -> "生物认证失败"
            }
        )

    fun requireWriteCapability(): Result<Unit> {
        AuthStateHolder.setReadOnlyModeFrom(teeAuth)
        return if (teeAuth.isTeeBacked) {
            Result.success(Unit)
        } else {
            Result.failure(ReadOnlyDeviceException.fromCapability(teeAuth.capability))
        }
    }

    suspend fun authenticateForOperation(
        cmdType: String,
        payload: AuthPayload,
        title: String = "生物认证",
        subtitle: String = "请验证身份以继续操作"
    ): Result<Unit> = authenticateForOperationWithDeviceKey(
        cmdType = cmdType,
        payload = { payload },
        title = title,
        subtitle = subtitle
    ).map { Unit }

    suspend fun authenticateForOperationWithDeviceKey(
        cmdType: String,
        payload: (devicePublicKey: String) -> AuthPayload,
        title: String = "生物认证",
        subtitle: String = "请验证身份以继续操作"
    ): Result<String> {
        requireWriteCapability().onFailure { return Result.failure(it) }
        AuthStateHolder.startVerifying()


        val bioResult = biometricAuth.authenticate(title, subtitle)
        if (bioResult != BiometricResult.Success) {
            AuthStateHolder.onVerificationFailed()
            return Result.failure(biometricError(bioResult))
        }


        val userRole = localRegistration?.role ?: "guest"
        if (!RequiredRoleGate.preCheck(cmdType, userRole)) {
            AuthStateHolder.onVerificationFailed()
            return Result.failure(
                Exception("权限不足: 操作 $cmdType 需要管理员或成员角色, 当前为 $userRole")
            )
        }


        val publicKey = teeAuth.tryGetPublicKey().getOrElse {
            AuthStateHolder.onVerificationFailed()
            return Result.failure(ReadOnlyDeviceException.fromCapability(teeAuth.capability))
        }
        val request = AuthRequest(
            cmd_type = cmdType,
            payload = payload(publicKey),
            public_key = publicKey
        )

        val challengeResult = client.requestChallenge(request)
        val challenge = challengeResult.getOrElse {
            AuthStateHolder.onVerificationFailed()
            return Result.failure(Exception("获取挑战失败: ${it.message}"))
        }


        if (challenge.ttl_seconds <= 0) {
            AuthStateHolder.onVerificationFailed()
            return Result.failure(Exception("挑战已过期"))
        }

        val riskLevel = AuthRiskLevel.fromString(challenge.risk_level)
        if (riskLevel == AuthRiskLevel.CRITICAL) {
            AuthStateHolder.onVerificationFailed()
            return Result.failure(
                Exception("此操作安全级别为 CRITICAL，需要推送通道二次确认")
            )
        }

        val requiredRole = challenge.required_role.lowercase()
        if (requiredRole.isNotEmpty() && requiredRole != "guest" && localRegistration != null) {
            val userLevel = RequiredRoleGate.roleLevel(userRole)
            val requiredLevel = RequiredRoleGate.roleLevel(requiredRole)
            if (userLevel < requiredLevel) {
                AuthStateHolder.onVerificationFailed()
                return Result.failure(
                    Exception("服务端要求 $requiredRole 角色权限, 当前为 $userRole")
                )
            }
        }


        AuthStateHolder.openSignWindow(challenge.nonce)

        val message = TeeAuthManager.buildCanonicalChallengeMessage(challenge)
        val signature = try {
            val sigBytes = teeAuth.sign(message)
            Base64.getEncoder().encodeToString(sigBytes)
        } catch (e: Exception) {
            AuthStateHolder.closeSignWindow()
            return Result.failure(Exception("签名失败: ${e.message}"))
        }


        val subjectDid = localRegistration?.subjectDid ?: ""
        val response = SignResponse(
            challenge_id = challenge.challenge_id,
            device_id = publicKey,
            subject_did = subjectDid,
            signature_alg = "Ed25519",
            nonce = challenge.nonce,
            signature = signature
        )

        val verifyResult = client.verifySignature(response)
        val authResult = verifyResult.getOrElse {
            AuthStateHolder.closeSignWindow()
            return Result.failure(Exception("验证签名失败: ${it.message}"))
        }

        return if (authResult.success) {
            client.setAuthContext(
                AuthContext(
                    nonce = challenge.nonce,
                    signature = signature,
                    challengeId = challenge.challenge_id,
                    subjectDid = subjectDid
                )
            )
            AuthStateHolder.onUnlockSuccess()
            Result.success(publicKey)
        } else {
            AuthStateHolder.closeSignWindow()
            Result.failure(Exception(authResult.message))
        }
    }

    fun clearAuth() {
        client.clearAuthContext()
        AuthStateHolder.lock()
    }

    suspend fun signWithBiometric(
        title: String,
        subtitle: String,
        message: ByteArray
    ): Result<String> {
        requireWriteCapability().onFailure { return Result.failure(it) }
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

    suspend fun signWithBiometricAndDeviceKey(
        title: String,
        subtitle: String,
        message: ByteArray
    ): Result<SignedDeviceMessage> {
        requireWriteCapability().onFailure { return Result.failure(it) }
        val publicKey = teeAuth.tryGetPublicKey().getOrElse {
            return Result.failure(ReadOnlyDeviceException.fromCapability(teeAuth.capability))
        }
        val signature = signWithBiometric(title, subtitle, message).getOrElse {
            return Result.failure(it)
        }
        return Result.success(SignedDeviceMessage(publicKey, signature))
    }

    fun registerLocalIdentity(subjectDid: String, role: String, displayName: String): Result<Unit> {
        val pubkey = teeAuth.tryGetPublicKey().getOrElse {
            return Result.failure(Exception("无法获取设备公钥: ${it.message}"))
        }
        localRegistration = LocalAdminRegistration(
            subjectDid = subjectDid,
            displayName = displayName,
            role = role,
            pubkey = pubkey
        )
        return Result.success(Unit)
    }
}

suspend inline fun <R> AuthCoordinator.withAuthenticatedOperationUsingDeviceKey(
    cmdType: String,
    noinline payload: (devicePublicKey: String) -> AuthPayload,
    title: String = "生物认证",
    subtitle: String = "请验证身份以继续操作",
    operation: suspend (devicePublicKey: String) -> R
): Result<R> {
    val authResult = authenticateForOperationWithDeviceKey(cmdType, payload, title, subtitle)
    if (authResult.isFailure) {
        return Result.failure(authResult.exceptionOrNull() ?: RuntimeException("Authentication failed"))
    }
    val devicePublicKey = authResult.getOrThrow()
    return try {
        Result.success(operation(devicePublicKey))
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        clearAuth()
    }
}

suspend inline fun <R> AuthCoordinator.withAuthenticatedOperation(
    cmdType: String,
    payload: AuthPayload,
    title: String = "生物认证",
    subtitle: String = "请验证身份以继续操作",
    operation: suspend () -> R
): Result<R> {
    val authResult = authenticateForOperation(cmdType, payload, title, subtitle)
    if (authResult.isFailure) {
        return Result.failure(authResult.exceptionOrNull() ?: RuntimeException("Authentication failed"))
    }
    return try {
        Result.success(operation())
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        clearAuth()
    }
}

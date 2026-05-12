package com.jlucraft.console.data.repository

import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.AuthPayload
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.CommandResult
import com.jlucraft.console.data.remote.libp2p.Libp2pClient


 *
class CommandRepository(
    private val client: Libp2pClient,
    private val teeAuth: TeeAuthManager
) {
    @Volatile
    var pendingChallenge: AuthChallenge? = null
        private set

    suspend fun createChallenge(
        cmdType: String,
        payload: AuthPayload,
        publicKey: String
    ): Result<AuthChallenge> {
        val request = AuthRequest(
            cmd_type = cmdType,
            payload = payload,
            public_key = publicKey
        )
        val challenge = client.createCommand(request).getOrElse {
            pendingChallenge = null
            return Result.failure(Exception("创建命令挑战失败: ${it.message}"))
        }
        pendingChallenge = challenge
        return Result.success(challenge)
    }

    suspend fun signAndRespond(
        deviceId: String,
        subjectDid: String
    ): Result<CommandResult> {
        val challenge = pendingChallenge
            ?: return Result.failure(Exception("没有待处理的挑战"))
        val signResult = teeAuth.signCanonicalChallenge(challenge, deviceId, subjectDid)
        val signResponse = signResult.getOrElse {
            pendingChallenge = null
            return Result.failure(Exception("TEE 签名失败: ${it.message}"))
        }
        val result = client.respondCommand(challenge.challenge_id, signResponse).getOrElse {
            pendingChallenge = null
            return Result.failure(Exception("提交签名响应失败: ${it.message}"))
        }
        pendingChallenge = null
        return Result.success(result)
    }

    fun clearPendingChallenge() {
        pendingChallenge = null
    }
}

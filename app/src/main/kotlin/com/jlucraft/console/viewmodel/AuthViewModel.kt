package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.AuthPayload
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.SignResponse
import com.jlucraft.console.data.remote.libp2p.AuthContext
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.launch
import java.util.Base64

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class ChallengeReady(
        val cmdType: String,
        val payloadSummary: String,
        val nonce: String,
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit
    ) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    object Success : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val teeAuth: TeeAuthManager
) : ViewModel() {

    private val _uiState = mutableStateOf<AuthUiState>(AuthUiState.Idle)
    val uiState: State<AuthUiState> = _uiState

    private var pendingNonce: String? = null
    private var pendingChallengeId: String? = null
    private var pendingCmdType: String? = null
    private var pendingPayloadHash: String? = null
    private var pendingExpiresAt: String? = null
    private var pendingPublicKey: String? = null

    fun initiateAuth(cmdType: String, payload: AuthPayload) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val publicKey = teeAuth.getPublicKey()
                val request = AuthRequest(
                    cmd_type = cmdType,
                    payload = payload,
                    public_key = publicKey
                )

                val result = client.requestChallenge(request)
                val challenge = result.getOrThrow()
                pendingChallengeId = challenge.challenge_id
                pendingNonce = challenge.nonce
                pendingCmdType = challenge.cmd_type
                pendingPayloadHash = challenge.payload_hash
                pendingExpiresAt = challenge.expires_at
                pendingPublicKey = publicKey

                _uiState.value = AuthUiState.ChallengeReady(
                    cmdType = cmdType,
                    payloadSummary = payload.summary,
                    nonce = challenge.nonce,
                    onConfirm = { confirmAndSign() },
                    onCancel = { cancel() }
                )
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("获取挑战失败: ${e.message}")
            }
        }
    }

    private fun confirmAndSign() {
        val nonce = pendingNonce ?: return
        val challengeId = pendingChallengeId ?: return
        val cmdType = pendingCmdType ?: return
        val payloadHash = pendingPayloadHash ?: return
        val expiresAt = pendingExpiresAt ?: return
        val publicKey = pendingPublicKey ?: return

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val message = "JLUCraftAuthV1||$challengeId||$nonce||$cmdType||$payloadHash||$expiresAt".toByteArray()
                val signature = teeAuth.sign(message)
                val signatureB64 = Base64.getEncoder().encodeToString(signature)

                val response = SignResponse(
                    challenge_id = challengeId,
                    device_id = publicKey,
                    subject_did = "",
                    signature_alg = "Ed25519",
                    nonce = nonce,
                    signature = signatureB64
                )

                val result = client.verifySignature(response)
                val authResult = result.getOrThrow()

                if (authResult.success) {
                    client.setAuthContext(
                        AuthContext(
                            nonce = nonce,
                            signature = signatureB64,
                            challengeId = challengeId,
                            subjectDid = ""
                        )
                    )
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error(authResult.message)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("签名验证失败: ${e.message}")
            }
        }
    }

    private fun cancel() {
        pendingNonce = null
        pendingChallengeId = null
        pendingCmdType = null
        pendingPayloadHash = null
        pendingExpiresAt = null
        pendingPublicKey = null
        _uiState.value = AuthUiState.Idle
    }

    fun reset() {
        pendingNonce = null
        pendingChallengeId = null
        pendingCmdType = null
        pendingPayloadHash = null
        pendingExpiresAt = null
        pendingPublicKey = null
        client.clearAuthContext()
        _uiState.value = AuthUiState.Idle
    }
}

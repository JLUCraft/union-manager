package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.SignResponse
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

class AuthViewModel(
    private val api: ApiService,
    private val teeAuth: TeeAuthManager
) : ViewModel() {

    private val _uiState = mutableStateOf<AuthUiState>(AuthUiState.Idle)
    val uiState: State<AuthUiState> = _uiState

    private var pendingNonce: String? = null
    private var pendingCmdType: String? = null
    private var pendingPayloadHash: String? = null

    fun initiateAuth(cmdType: String, payload: kotlinx.serialization.json.JsonObject) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val publicKey = teeAuth.getPublicKey()
                val request = AuthRequest(
                    cmd_type = cmdType,
                    payload = payload,
                    public_key = publicKey
                )

                val result = api.requestChallenge(request)
                val challenge = result.getOrThrow()
                pendingNonce = challenge.nonce
                pendingCmdType = cmdType
                pendingPayloadHash = challenge.payload_hash

                _uiState.value = AuthUiState.ChallengeReady(
                    cmdType = cmdType,
                    payloadSummary = payload.toString(),
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
        val cmdType = pendingCmdType ?: return
        val payloadHash = pendingPayloadHash ?: return

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val message = "$nonce|$cmdType|$payloadHash".toByteArray()
                val signature = teeAuth.sign(message)
                val signatureB64 = Base64.getEncoder().encodeToString(signature)

                val response = SignResponse(
                    nonce = nonce,
                    signature = signatureB64
                )

                val result = api.verifySignature(response)
                val authResult = result.getOrThrow()

                if (authResult.success) {
                    api.setAuthHeaders(nonce, signatureB64)
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
        pendingCmdType = null
        pendingPayloadHash = null
        _uiState.value = AuthUiState.Idle
    }

    fun reset() {
        pendingNonce = null
        pendingCmdType = null
        pendingPayloadHash = null
        api.clearAuthHeaders()
        _uiState.value = AuthUiState.Idle
    }
}

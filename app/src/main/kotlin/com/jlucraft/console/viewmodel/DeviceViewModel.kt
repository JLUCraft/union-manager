package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.model.RevokeDevicePayload
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.launch

data class DeviceUiState(
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val revokeSuccess: String? = null,
    val revokeError: String? = null
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val teeAuth: TeeAuthManager,
    private val authCoordinator: AuthCoordinator
) : ViewModel() {

    private val _uiState = mutableStateOf(DeviceUiState())
    val uiState: State<DeviceUiState> = _uiState

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            client.listDevices()
                .onSuccess { devices ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        devices = devices
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun revokeDevice(pubkey: String, reason: String) {
        val revokedBy = teeAuth.getPublicKey()
        performRevocation(
            pubkey = pubkey,
            reason = reason,
            revokedBy = revokedBy,
            cmdType = "revoke-device",
            title = "吊销设备",
            subtitle = "请验证身份以执行此操作",
            successMessage = "设备已吊销",
            revokeCall = { client.revokeDevice(pubkey, reason, revokedBy) }
        )
    }

    fun emergencyRevokeDevice(pubkey: String, reason: String) {
        val revokedBy = teeAuth.getPublicKey()
        performRevocation(
            pubkey = pubkey,
            reason = reason,
            revokedBy = revokedBy,
            cmdType = "emergency-revoke-device",
            title = "紧急吊销设备",
            subtitle = "请验证身份以执行此操作",
            successMessage = "设备已紧急吊销",
            revokeCall = { client.emergencyRevokeDevice(pubkey, reason, revokedBy) }
        )
    }

    private fun performRevocation(
        pubkey: String,
        reason: String,
        revokedBy: String,
        cmdType: String,
        title: String,
        subtitle: String,
        successMessage: String,
        revokeCall: suspend () -> Result<Device>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                revokeSuccess = null,
                revokeError = null
            )

            val payload = RevokeDevicePayload(pubkey, reason, revokedBy)
            val wrappedResult = authCoordinator.withAuthenticatedOperation(
                cmdType = cmdType,
                payload = payload,
                title = title,
                subtitle = subtitle
            ) {
                revokeCall()
            }

            if (wrappedResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    revokeError = wrappedResult.exceptionOrNull()?.message ?: "认证失败"
                )
                return@launch
            }

            val result = wrappedResult.getOrThrow()
            result
                .onSuccess {
                    _uiState.value = _uiState.value.copy(revokeSuccess = successMessage)
                    loadDevices()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        revokeError = error.message
                    )
                }
        }
    }

    fun getCurrentPubkey(): String = teeAuth.getPublicKey()

    fun clearRevokeStatus() {
        _uiState.value = _uiState.value.copy(revokeSuccess = null, revokeError = null)
    }
}

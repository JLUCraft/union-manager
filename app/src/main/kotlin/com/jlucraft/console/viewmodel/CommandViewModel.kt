package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.AuthPayload
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.repository.CommandRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


 *
data class CommandUiState(
    val challenge: AuthChallenge? = null,
    val countdownSeconds: Long = 0,
    val resultMessage: String? = null,
    val isExecuting: Boolean = false
)


 *
@HiltViewModel
class CommandViewModel @Inject constructor(
    private val commandRepository: CommandRepository,
    private val teeAuth: TeeAuthManager,
    private val authCoordinator: AuthCoordinator
) : ViewModel() {

    private val _uiState = mutableStateOf(CommandUiState())
    val uiState: State<CommandUiState> = _uiState


    fun initiateCommand(
        cmdType: String,
        payload: AuthPayload
    ) {
        val writeCapability = authCoordinator.requireWriteCapability()
        if (writeCapability.isFailure) {
            _uiState.value = CommandUiState(resultMessage = writeCapability.exceptionOrNull()?.message)
            return
        }
        viewModelScope.launch {
            _uiState.value = CommandUiState(isExecuting = true)
            val publicKey = teeAuth.tryGetPublicKey().getOrElse {
                _uiState.value = CommandUiState(
                    resultMessage = authCoordinator.requireWriteCapability().exceptionOrNull()?.message
                        ?: "设备公钥不可用"
                )
                return@launch
            }

            val challengeResult = commandRepository.createChallenge(
                cmdType = cmdType,
                payload = payload,
                publicKey = publicKey
            )
            val challenge = challengeResult.getOrElse {
                _uiState.value = CommandUiState(
                    resultMessage = "创建挑战失败: ${it.message}"
                )
                return@launch
            }

            _uiState.value = CommandUiState(
                challenge = challenge,
                countdownSeconds = challenge.ttl_seconds,
                isExecuting = false
            )
            startCountdown(challenge.ttl_seconds)
        }
    }


    fun confirmChallenge() {
        val challenge = _uiState.value.challenge ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExecuting = true)
            val publicKey = teeAuth.tryGetPublicKey().getOrElse {
                _uiState.value = CommandUiState(
                    resultMessage = authCoordinator.requireWriteCapability().exceptionOrNull()?.message
                        ?: "设备公钥不可用"
                )
                return@launch
            }

            val result = commandRepository.signAndRespond(
                deviceId = publicKey,
                subjectDid = publicKey
            )
            _uiState.value = result.fold(
                onSuccess = { cmdResult ->
                    CommandUiState(
                        resultMessage = "执行成功: ${cmdResult.result}"
                    )
                },
                onFailure = { e ->
                    CommandUiState(
                        resultMessage = "执行失败: ${e.message}"
                    )
                }
            )
        }
    }


    fun cancelChallenge() {
        commandRepository.clearPendingChallenge()
        _uiState.value = CommandUiState()
    }


    fun clearResult() {
        _uiState.value = _uiState.value.copy(resultMessage = null)
    }

    private fun startCountdown(initialSeconds: Long) {
        viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0 && _uiState.value.challenge != null) {
                delay(1000L)
                remaining--
                if (_uiState.value.challenge != null) {
                    _uiState.value = _uiState.value.copy(countdownSeconds = remaining)
                }
            }

            if (remaining <= 0 && _uiState.value.challenge != null) {
                commandRepository.clearPendingChallenge()
                _uiState.value = CommandUiState(resultMessage = "挑战已过期，请重新发起操作")
            }
        }
    }
}

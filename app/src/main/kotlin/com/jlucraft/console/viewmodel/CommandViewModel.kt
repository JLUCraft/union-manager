package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.AuthPayload
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.repository.CommandRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * UI state for the [AuthChallengeSheet] driven by [CommandRepository].
 *
 * @property challenge the pending challenge (null if idle)
 * @property countdownSeconds remaining TTL in seconds for the countdown timer
 * @property resultMessage message after command execution (success or error)
 * @property isExecuting true while the sign+respond step is in flight
 */
data class CommandUiState(
    val challenge: AuthChallenge? = null,
    val countdownSeconds: Long = 0,
    val resultMessage: String? = null,
    val isExecuting: Boolean = false
)

/**
 * ViewModel that wraps [CommandRepository] for Compose UI consumption.
 *
 * Provides the canonical three-step signed command flow:
 *   1. create → display challenge in AuthChallengeSheet
 *   2. user confirms → TEE-sign the canonical message
 *   3. respond → show CommandResult
 */
@HiltViewModel
class CommandViewModel @Inject constructor(
    private val commandRepository: CommandRepository,
    private val teeAuth: TeeAuthManager
) : ViewModel() {

    private val _uiState = mutableStateOf(CommandUiState())
    val uiState: State<CommandUiState> = _uiState

    /**
     * Initiate a signed command. Creates a server challenge and populates
     * the UI state so [AuthChallengeSheet] can render it.
     */
    fun initiateCommand(
        cmdType: String,
        payload: AuthPayload
    ) {
        viewModelScope.launch {
            _uiState.value = CommandUiState(isExecuting = true)
            val publicKey = teeAuth.getPublicKey()

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

    /**
     * User confirms the challenge in the UI → TEE-sign + submit response.
     * Called from [AuthChallengeSheet]'s confirm button.
     */
    fun confirmChallenge() {
        val challenge = _uiState.value.challenge ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExecuting = true)
            val publicKey = teeAuth.getPublicKey()

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

    /**
     * User cancels the challenge → clear pending state.
     */
    fun cancelChallenge() {
        commandRepository.clearPendingChallenge()
        _uiState.value = CommandUiState()
    }

    /** Dismiss the result message banner. */
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
            // Challenge expired
            if (remaining <= 0 && _uiState.value.challenge != null) {
                commandRepository.clearPendingChallenge()
                _uiState.value = CommandUiState(resultMessage = "挑战已过期，请重新发起操作")
            }
        }
    }
}

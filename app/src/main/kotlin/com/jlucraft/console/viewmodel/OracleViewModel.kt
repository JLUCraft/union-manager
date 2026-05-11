package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.model.OracleScore
import com.jlucraft.console.data.model.OracleVerificationResult
import com.jlucraft.console.data.repository.OracleRepository
import kotlinx.coroutines.launch

/**
 * UI state for the oracle proof screen.
 *
 * @property playerId the player being queried
 * @property oracleScore the fetched oracle score + proof
 * @property verificationResult local proof verification result
 * @property isLoading true while the score is being fetched
 * @property error fetch error message
 */
data class OracleUiState(
    val playerId: String = "",
    val oracleScore: OracleScore? = null,
    val verificationResult: OracleVerificationResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for oracle proof operations:
 *   - GET /v1/oracle/scores/{player_id}
 *   - Local Merkle proof verification
 *   - Audit verify integration (server-side root verification)
 */
@HiltViewModel
class OracleViewModel @Inject constructor(
    private val oracleRepository: OracleRepository
) : ViewModel() {

    private val _uiState = mutableStateOf(OracleUiState())
    val uiState: State<OracleUiState> = _uiState

    /**
     * Fetch an oracle score with Merkle proof for the given player.
     * After fetching, automatically performs local proof verification.
     */
    fun loadOracleScore(playerId: String) {
        viewModelScope.launch {
            _uiState.value = OracleUiState(
                playerId = playerId,
                isLoading = true,
                error = null
            )
            oracleRepository.getOracleScore(playerId)
                .onSuccess { score ->
                    // Immediately verify the proof locally
                    val verificationResult = oracleRepository.verifyProofLocally(score.proof)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        oracleScore = score,
                        verificationResult = verificationResult
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取预言机分数失败: ${error.message}"
                    )
                }
        }
    }

    /** Re-verify the current proof locally. */
    fun verifyLocally() {
        val score = _uiState.value.oracleScore ?: return
        val verificationResult = oracleRepository.verifyProofLocally(score.proof)
        _uiState.value = _uiState.value.copy(verificationResult = verificationResult)
    }

}

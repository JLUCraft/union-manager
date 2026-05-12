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


 *
data class OracleUiState(
    val playerId: String = "",
    val oracleScore: OracleScore? = null,
    val verificationResult: OracleVerificationResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)


@HiltViewModel
class OracleViewModel @Inject constructor(
    private val oracleRepository: OracleRepository
) : ViewModel() {

    private val _uiState = mutableStateOf(OracleUiState())
    val uiState: State<OracleUiState> = _uiState


    fun loadOracleScore(playerId: String) {
        viewModelScope.launch {
            _uiState.value = OracleUiState(
                playerId = playerId,
                isLoading = true,
                error = null
            )
            oracleRepository.getOracleScore(playerId)
                .onSuccess { score ->

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


    fun verifyLocally() {
        val score = _uiState.value.oracleScore ?: return
        val verificationResult = oracleRepository.verifyProofLocally(score.proof)
        _uiState.value = _uiState.value.copy(verificationResult = verificationResult)
    }

}

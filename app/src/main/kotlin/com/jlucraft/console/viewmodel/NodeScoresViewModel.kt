package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.launch

data class NodeScoresUiState(
    val isLoading: Boolean = false,
    val nodeScores: List<NodeScore> = emptyList(),
    val selectedNodeScore: NodeScore? = null,
    val error: String? = null
)

@HiltViewModel
class NodeScoresViewModel @Inject constructor(
    private val client: Libp2pClient
) : ViewModel() {

    private val _uiState = mutableStateOf(NodeScoresUiState())
    val uiState: State<NodeScoresUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            client.listNodeScores()
                .onSuccess { scores ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        nodeScores = scores
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

}

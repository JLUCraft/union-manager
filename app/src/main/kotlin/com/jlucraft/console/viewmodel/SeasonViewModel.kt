package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.model.Leaderboard
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.launch

data class SeasonUiState(
    val isLoading: Boolean = false,
    val seasons: List<Season> = emptyList(),
    val currentSeason: Season? = null,
    val selectedSeason: Season? = null,
    val selectedLeaderboard: Leaderboard? = null,
    val leaderboardLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SeasonViewModel @Inject constructor(
    private val client: Libp2pClient
) : ViewModel() {

    private val _uiState = mutableStateOf(SeasonUiState())
    val uiState: State<SeasonUiState> = _uiState

    init {
        loadSeasons()
    }

    fun loadSeasons() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            client.listSeasons()
                .onSuccess { seasons ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        seasons = seasons
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

    fun loadCurrentSeason() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            client.getCurrentSeason()
                .onSuccess { season ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentSeason = season
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

    fun selectSeason(season: Season) {
        _uiState.value = _uiState.value.copy(
            selectedSeason = season,
            selectedLeaderboard = null,
            leaderboardLoading = false
        )
        loadSeasonLeaderboard(season.id)
    }

    private fun loadSeasonLeaderboard(seasonId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(leaderboardLoading = true)
            client.getSeasonLeaderboard(seasonId)
                .onSuccess { leaderboard ->
                    _uiState.value = _uiState.value.copy(
                        leaderboardLoading = false,
                        selectedLeaderboard = leaderboard
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        leaderboardLoading = false,
                        error = error.message
                    )
                }
        }
    }
}

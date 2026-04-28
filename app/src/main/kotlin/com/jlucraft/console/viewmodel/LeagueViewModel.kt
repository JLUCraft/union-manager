package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.CreateTournamentRequest
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.MatchSchedule
import com.jlucraft.console.data.model.ScoringRules
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.data.model.Leaderboard
import com.jlucraft.console.data.model.Team
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.model.TournamentSchedule
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class LeagueUiState(
    val isLoading: Boolean = false,
    val tournaments: List<Tournament> = emptyList(),
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val createError: String? = null,
    val selectedTournament: Tournament? = null,
    val matches: List<Match> = emptyList(),
    val teams: List<Team> = emptyList(),
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeason: Season? = null,
    val leaderboard: Leaderboard? = null,
    val seasonLoading: Boolean = false,
    val seasonError: String? = null
)

class LeagueViewModel(
    private val repository: NodeRepository,
    private val teeAuth: TeeAuthManager
) : ViewModel() {

    private val _uiState = mutableStateOf(LeagueUiState())
    val uiState: State<LeagueUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            ServiceLocator.pushService.events.collect { event ->
                when (event.type) {
                    "tournament_created", "tournament_updated", "tournament_deleted",
                    "match_scheduled", "match_result", "match_dispute",
                    "season_created", "season_archived", "leaderboard_updated" -> refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.listTournaments()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                tournaments = result.getOrNull() ?: emptyList(),
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true, createError = null)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false, createError = null)
    }

    fun createTournament(
        name: String,
        gameType: String,
        mode: String,
        maxParticipants: Int,
        minMemberScore: Int,
        registrationOpen: String,
        registrationClose: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(createError = null)
            val request = CreateTournamentRequest(
                name = name,
                game_type = gameType,
                mode = mode,
                schedule = TournamentSchedule(
                    registration_open = registrationOpen,
                    registration_close = registrationClose,
                    matches = emptyList()
                ),
                scoring = ScoringRules(),
                min_member_score = minMemberScore,
                max_participants = maxParticipants,
                created_by = teeAuth.getPublicKey()
            )
            val result = repository.createTournament(request)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(showCreateDialog = false)
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(
                    createError = result.exceptionOrNull()?.message ?: "创建失败"
                )
            }
        }
    }

    fun selectTournament(tournament: Tournament) {
        _uiState.value = _uiState.value.copy(
            selectedTournament = tournament,
            matches = emptyList(),
            teams = emptyList(),
            detailError = null
        )
        loadTournamentDetail(tournament.id)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTournament = null,
            matches = emptyList(),
            teams = emptyList(),
            detailError = null
        )
    }

    private fun loadTournamentDetail(tournamentId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(detailLoading = true, detailError = null)
            val matchesDeferred = async { repository.listMatches(tournamentId) }
            val teamsDeferred = async { repository.listTeams() }
            val matchesResult = matchesDeferred.await()
            val teamsResult = teamsDeferred.await()
            _uiState.value = _uiState.value.copy(
                detailLoading = false,
                matches = matchesResult.getOrNull() ?: emptyList(),
                teams = teamsResult.getOrNull() ?: emptyList(),
                detailError = matchesResult.exceptionOrNull()?.message
                    ?: teamsResult.exceptionOrNull()?.message
            )
        }
    }

    fun updateTournamentStatus(status: String) {
        val tournament = _uiState.value.selectedTournament ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(detailLoading = true, detailError = null)
            val result = repository.updateTournamentStatus(tournament.id, status)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    selectedTournament = result.getOrNull(),
                    detailLoading = false
                )
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(
                    detailLoading = false,
                    detailError = result.exceptionOrNull()?.message ?: "更新失败"
                )
            }
        }
    }

    fun loadSeasons() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(seasonLoading = true, seasonError = null)
            val result = repository.listSeasons()
            _uiState.value = _uiState.value.copy(
                seasonLoading = false,
                seasons = result.getOrNull() ?: emptyList(),
                seasonError = result.exceptionOrNull()?.message
            )
        }
    }

    fun selectSeason(season: Season) {
        _uiState.value = _uiState.value.copy(selectedSeason = season, leaderboard = null)
        loadLeaderboard(season.id)
    }

    fun clearSeasonSelection() {
        _uiState.value = _uiState.value.copy(selectedSeason = null, leaderboard = null)
    }

    fun archiveSelectedSeason() {
        val season = _uiState.value.selectedSeason ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(seasonLoading = true, seasonError = null)
            val result = repository.archiveSeason(season.id)
            if (result.isSuccess) {
                val seasonsResult = repository.listSeasons()
                _uiState.value = _uiState.value.copy(
                    selectedSeason = result.getOrNull(),
                    seasons = seasonsResult.getOrNull() ?: _uiState.value.seasons,
                    seasonLoading = false,
                    seasonError = seasonsResult.exceptionOrNull()?.message
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    seasonLoading = false,
                    seasonError = result.exceptionOrNull()?.message ?: "归档失败"
                )
            }
        }
    }

    fun createSeason(name: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(seasonLoading = true, seasonError = null)
            val result = repository.createSeason(name, startDate, endDate)
            if (result.isSuccess) {
                val seasonsResult = repository.listSeasons()
                _uiState.value = _uiState.value.copy(
                    seasons = seasonsResult.getOrNull() ?: _uiState.value.seasons,
                    seasonLoading = false,
                    seasonError = seasonsResult.exceptionOrNull()?.message
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    seasonLoading = false,
                    seasonError = result.exceptionOrNull()?.message ?: "创建赛季失败"
                )
            }
        }
    }

    private fun loadLeaderboard(seasonId: String) {
        viewModelScope.launch {
            val result = repository.getSeasonLeaderboard(seasonId)
            _uiState.value = _uiState.value.copy(
                leaderboard = result.getOrNull()
            )
        }
    }
}

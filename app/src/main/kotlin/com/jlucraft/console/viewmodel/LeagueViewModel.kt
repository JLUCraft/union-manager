package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.domain.auth.AuthenticatedOperationUseCase
import com.jlucraft.console.data.model.ArchiveSeasonPayload
import com.jlucraft.console.data.model.AuthPayload
import com.jlucraft.console.data.model.CreateDisputePayload
import com.jlucraft.console.data.model.CreateDisputeRequest
import com.jlucraft.console.data.model.CreateProposalAuthPayload
import com.jlucraft.console.data.model.CreateSeasonPayload
import com.jlucraft.console.data.model.CreateTournamentPayload
import com.jlucraft.console.data.model.CreateTournamentProposalPayload
import com.jlucraft.console.data.model.CreateTournamentRequest
import com.jlucraft.console.data.model.DisputeMatch
import com.jlucraft.console.data.model.DisputeResolveProposalPayload
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.ScoringRules
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.data.model.Leaderboard
import com.jlucraft.console.data.model.Team
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.model.TournamentSchedule
import com.jlucraft.console.data.model.TournamentStatus
import com.jlucraft.console.data.model.UpdateTournamentStatusPayload
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
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
    val seasonError: String? = null,
    // Dispute state
    val disputes: List<DisputeMatch> = emptyList(),
    val selectedDispute: DisputeMatch? = null,
    val disputesLoading: Boolean = false,
    val disputesError: String? = null,
    val showCreateDisputeDialog: Boolean = false,
    val disputeCreateError: String? = null,
    // Multi-sig proposal tracking for large tournaments and dispute resolutions
    val pendingProposal: Proposal? = null,
    val proposalError: String? = null,
    val isCreatingProposal: Boolean = false
)

@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val teeAuth: TeeAuthManager,
    private val authCoordinator: AuthCoordinator,
    pushService: PushService,
) : ViewModel() {

    private val authenticatedOperation = AuthenticatedOperationUseCase(authCoordinator)

    private val _uiState = mutableStateOf(LeagueUiState())
    val uiState: State<LeagueUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            pushService.events.collect { event ->
                when (event.type) {
                    "tournament_created", "tournament_updated", "tournament_deleted",
                    "match_scheduled", "match_result", "match_dispute",
                    "season_created", "season_archived", "leaderboard_updated",
                    "proposal_executed" -> refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = client.listTournaments()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                tournaments = result.getOrNull() ?: emptyList(),
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true, createError = null, pendingProposal = null)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false, createError = null)
    }

    // ── Direct tournament creation (for small/quick tournaments) ──

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
            val payload = CreateTournamentPayload(
                name = name,
                gameType = gameType,
                mode = mode,
                maxParticipants = maxParticipants,
                minMemberScore = minMemberScore,
                registrationOpen = registrationOpen,
                registrationClose = registrationClose,
                createdBy = teeAuth.getPublicKey()
            )
            val authResult = authCoordinator.withAuthenticatedOperation(
                cmdType = "create-tournament",
                payload = payload,
                title = "创建赛事",
                subtitle = "请验证身份以创建赛事"
            ) {
                client.createTournament(request)
            }
            if (authResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    createError = authResult.exceptionOrNull()?.message ?: "认证失败"
                )
                return@launch
            }
            val result = authResult.getOrThrow()
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

    /**
     * Create a large tournament via multi-sig governance proposal.
     *
     * This is the required path when maxParticipants exceeds a federation-configured
     * threshold (e.g. > 64), or when the tournament spans multiple clubs. The proposal
     * must be signed by a quorum of admins before the tournament is created.
     */
    fun createTournamentViaProposal(
        name: String,
        gameType: String,
        mode: String,
        maxParticipants: Int,
        minMemberScore: Int,
        registrationOpen: String,
        registrationClose: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingProposal = true, createError = null)

            val proposer = teeAuth.getPublicKey()
            val propPayload = CreateTournamentProposalPayload(
                name = name,
                gameType = gameType,
                mode = mode,
                maxParticipants = maxParticipants,
                minMemberScore = minMemberScore,
                registrationOpen = registrationOpen,
                registrationClose = registrationClose,
                scoring = ScoringRules(
                    win = 10,
                    kill = 2,
                    survive_minute = 0.5,
                    placement_1 = 10,
                    placement_2 = 7,
                    placement_3 = 5
                )
            )

            val authResult = authCoordinator.withAuthenticatedOperation(
                cmdType = "create-proposal",
                payload = CreateProposalAuthPayload("create-tournament", propPayload, proposer),
                title = "创建赛事提案",
                subtitle = "大型赛事需要多签审批，请验证身份以提交提案"
            ) {
                client.createProposal("create-tournament", propPayload, proposer)
            }
            if (authResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isCreatingProposal = false,
                    createError = authResult.exceptionOrNull()?.message ?: "认证失败"
                )
                return@launch
            }

            val result = authResult.getOrThrow()

            result
                .onSuccess { proposal ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingProposal = false,
                        showCreateDialog = false,
                        pendingProposal = proposal,
                        createError = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingProposal = false,
                        createError = error.message ?: "提案创建失败"
                    )
                }
        }
    }

    /**
     * Resolve a match dispute via multi-sig governance proposal.
     *
     * Disputes that affect tournament rankings or involve contested evidence
     * are routed through a governance proposal rather than direct admin action.
     * This ensures quorum consensus for dispute resolution.
     */
    fun resolveDisputeViaProposal(
        disputeId: String,
        resolution: String,
        status: String, // "resolved" | "dismissed"
        tournamentId: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingProposal = true, disputesError = null)

            val proposer = teeAuth.getPublicKey()
            val propPayload = DisputeResolveProposalPayload(
                disputeId = disputeId,
                resolution = resolution,
                status = status,
                tournamentId = tournamentId
            )

            val authResult = authCoordinator.withAuthenticatedOperation(
                cmdType = "create-proposal",
                payload = CreateProposalAuthPayload("dispute-resolve", propPayload, proposer),
                title = "争议处理提案",
                subtitle = "争议处理需要多签审批，请验证身份以提交提案"
            ) {
                client.createProposal("dispute-resolve", propPayload, proposer)
            }
            if (authResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isCreatingProposal = false,
                    disputesError = authResult.exceptionOrNull()?.message ?: "认证失败"
                )
                return@launch
            }

            val result = authResult.getOrThrow()

            result
                .onSuccess { proposal ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingProposal = false,
                        pendingProposal = proposal,
                        disputesError = null
                    )
                    loadDisputes()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingProposal = false,
                        disputesError = error.message ?: "提案创建失败"
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
            val matchesDeferred = async { client.listMatches(tournamentId) }
            val teamsDeferred = async { client.listTeams() }
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

    fun updateTournamentStatus(status: TournamentStatus) {
        val tournament = _uiState.value.selectedTournament ?: return
        performAuthenticatedOperation(
            cmdType = "update-tournament-status",
            payload = UpdateTournamentStatusPayload(tournament.id, status.name.lowercase()),
            title = "更新赛事状态",
            subtitle = "请验证身份以更新赛事状态",
            operation = { client.updateTournamentStatus(tournament.id, status) },
            failureMessage = "状态更新失败"
        )
    }

    fun loadSeasons() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(seasonLoading = true, seasonError = null)
            val result = client.listSeasons()
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
        performAuthenticatedOperation(
            cmdType = "archive-season",
            payload = ArchiveSeasonPayload(season.id),
            title = "归档赛季",
            subtitle = "请验证身份以归档赛季",
            operation = { client.archiveSeason(season.id) },
            failureMessage = "归档失败"
        )
    }

    fun createSeason(name: String, startDate: String, endDate: String) {
        performAuthenticatedOperation(
            cmdType = "create-season",
            payload = CreateSeasonPayload(name, startDate, endDate),
            title = "创建赛季",
            subtitle = "请验证身份以创建赛季",
            operation = { client.createSeason(name, startDate, endDate) },
            failureMessage = "创建赛季失败"
        )
    }

    private fun loadLeaderboard(seasonId: String) {
        viewModelScope.launch {
            val result = client.getSeasonLeaderboard(seasonId)
            _uiState.value = _uiState.value.copy(
                leaderboard = result.getOrNull()
            )
        }
    }

    // ── Dispute handling ───────────────────────────────────────────

    fun loadDisputes(tournamentId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(disputesLoading = true, disputesError = null)
            val result = client.listDisputes(tournamentId)
            _uiState.value = _uiState.value.copy(
                disputesLoading = false,
                disputes = result.getOrNull() ?: emptyList(),
                disputesError = result.exceptionOrNull()?.message
            )
        }
    }

    fun showCreateDisputeDialog() {
        _uiState.value = _uiState.value.copy(showCreateDisputeDialog = true, disputeCreateError = null)
    }

    fun dismissCreateDisputeDialog() {
        _uiState.value = _uiState.value.copy(showCreateDisputeDialog = false, disputeCreateError = null)
    }

    fun createDispute(matchId: String, reason: String, evidence: List<String> = emptyList()) {
        val tournamentId = _uiState.value.selectedTournament?.id ?: return
        performAuthenticatedOperation(
            cmdType = "create-dispute",
            payload = CreateDisputePayload(tournamentId, matchId, reason),
            title = "创建争议",
            subtitle = "请验证身份以提交争议",
            operation = {
                val request = CreateDisputeRequest(matchId, reason, evidence)
                client.createDispute(tournamentId, request)
            },
            failureMessage = "创建争议失败"
        )
    }

    fun selectDispute(dispute: DisputeMatch) {
        _uiState.value = _uiState.value.copy(selectedDispute = dispute)
    }

    fun clearDisputeSelection() {
        _uiState.value = _uiState.value.copy(selectedDispute = null)
    }

    // resolveDispute removed — dispute resolution must go through
    // resolveDisputeViaProposal (multi-sig governance proposal) to
    // prevent single-admin bypass of the governance quorum.

    /** Clear the pending proposal tracker. */
    fun clearPendingProposal() {
        _uiState.value = _uiState.value.copy(pendingProposal = null)
    }

    private fun <T> performAuthenticatedOperation(
        cmdType: String,
        payload: AuthPayload,
        title: String,
        subtitle: String,
        operation: suspend () -> Result<T>,
        failureMessage: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(detailLoading = true, detailError = null)
            val result = authenticatedOperation.execute(cmdType, payload, title, subtitle, operation, failureMessage)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(detailLoading = false)
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(
                    detailLoading = false,
                    detailError = result.exceptionOrNull()?.message ?: failureMessage
                )
            }
        }
    }
}

package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.Team
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.di.ServiceLocator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeagueViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: NodeRepository
    private lateinit var teeAuth: TeeAuthManager
    private lateinit var pushEvents: MutableSharedFlow<PushService.WebSocketEvent>
    private lateinit var viewModel: LeagueViewModel

    private val mockTournaments = listOf(
        Tournament(
            id = "t-1",
            name = "Summer Cup",
            game_type = "MOBA",
            mode = "1v1",
            status = "open",
            max_participants = 32,
            min_member_score = 10,
            created_at = "2024-01-01T00:00:00Z",
            created_by = "pk-1"
        ),
        Tournament(
            id = "t-2",
            name = "Winter League",
            game_type = "FPS",
            mode = "3v3",
            status = "active",
            max_participants = 64,
            min_member_score = 20,
            created_at = "2024-02-01T00:00:00Z",
            created_by = "pk-2"
        )
    )

    private val mockMatches = listOf(
        Match(
            id = "m-1",
            tournament_id = "t-1",
            round = 1,
            participants = listOf("pk-1", "pk-2"),
            status = "scheduled",
            scheduled_at = "2024-06-01T00:00:00Z"
        )
    )

    private val mockTeams = listOf(
        Team(
            id = "team-1",
            name = "Alpha",
            members = listOf("pk-1", "pk-2"),
            total_score = 100
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        teeAuth = mockk()

        pushEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        val pushService = mockk<PushService>()
        every { pushService.events } returns pushEvents

        ServiceLocator.pushService = pushService
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()

        ServiceLocator.pushService = mockk()
    }

    private fun mockListTournaments(tournaments: List<Tournament> = mockTournaments) {
        coEvery { repository.listTournaments() } returns Result.success(tournaments)
    }

    private fun createViewModel() {
        viewModel = LeagueViewModel(repository, teeAuth)
    }

    private fun makeEvent(type: String) = PushService.WebSocketEvent(
        type = type,
        data = JsonObject(emptyMap())
    )

    // ---------------------------------------------------------------
    // 1. Initial state loads tournaments
    // ---------------------------------------------------------------

    @Test
    fun initial_state_loads_tournaments() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockTournaments, viewModel.uiState.value.tournaments)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        coVerify(exactly = 1) { repository.listTournaments() }
    }

    // ---------------------------------------------------------------
    // 2. Push event "tournament_created" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_tournament_created_triggers_refresh() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("tournament_created"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.listTournaments() }
    }

    // ---------------------------------------------------------------
    // 3. Push event "tournament_updated" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_tournament_updated_triggers_refresh() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("tournament_updated"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.listTournaments() }
    }

    // ---------------------------------------------------------------
    // 4. Push event "match_scheduled" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_match_scheduled_triggers_refresh() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("match_scheduled"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.listTournaments() }
    }

    // ---------------------------------------------------------------
    // 5. Push event "match_result" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_match_result_triggers_refresh() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("match_result"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.listTournaments() }
    }

    // ---------------------------------------------------------------
    // 6. Push event "season_created" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_season_created_triggers_refresh() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("season_created"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.listTournaments() }
    }

    // ---------------------------------------------------------------
    // 7. Non-matching push event does not refresh
    // ---------------------------------------------------------------

    @Test
    fun non_matching_push_event_does_not_refresh() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("unrelated_event"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.listTournaments() }
    }

    // ---------------------------------------------------------------
    // 8. SelectTournament loads detail
    // ---------------------------------------------------------------

    @Test
    fun selectTournament_loads_detail() {
        mockListTournaments()
        coEvery { repository.listMatches("t-1") } returns Result.success(mockMatches)
        coEvery { repository.listTeams() } returns Result.success(mockTeams)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectTournament(mockTournaments.first())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockTournaments.first(), viewModel.uiState.value.selectedTournament)
        assertEquals(mockMatches, viewModel.uiState.value.matches)
        assertEquals(mockTeams, viewModel.uiState.value.teams)
        assertFalse(viewModel.uiState.value.detailLoading)
        assertNull(viewModel.uiState.value.detailError)

        coVerify(exactly = 1) { repository.listMatches("t-1") }
        coVerify(exactly = 1) { repository.listTeams() }
    }

    // ---------------------------------------------------------------
    // 9. ClearSelection clears detail state
    // ---------------------------------------------------------------

    @Test
    fun clearSelection_clears_detail_state() {
        mockListTournaments()
        coEvery { repository.listMatches("t-1") } returns Result.success(mockMatches)
        coEvery { repository.listTeams() } returns Result.success(mockTeams)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectTournament(mockTournaments.first())
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.selectedTournament)
        assertTrue(viewModel.uiState.value.matches.isNotEmpty())

        viewModel.clearSelection()

        assertNull(viewModel.uiState.value.selectedTournament)
        assertTrue(viewModel.uiState.value.matches.isEmpty())
        assertTrue(viewModel.uiState.value.teams.isEmpty())
        assertNull(viewModel.uiState.value.detailError)
    }

    // ---------------------------------------------------------------
    // 10. ShowCreateDialog / dismissCreateDialog work
    // ---------------------------------------------------------------

    @Test
    fun showCreateDialog_opens_dialog() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCreateDialog)

        viewModel.showCreateDialog()
        assertTrue(viewModel.uiState.value.showCreateDialog)
        assertNull(viewModel.uiState.value.createError)
    }

    @Test
    fun dismissCreateDialog_closes_dialog() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCreateDialog()
        assertTrue(viewModel.uiState.value.showCreateDialog)

        viewModel.dismissCreateDialog()
        assertFalse(viewModel.uiState.value.showCreateDialog)
        assertNull(viewModel.uiState.value.createError)
    }

    // ---------------------------------------------------------------
    // 11. Error state is handled
    // ---------------------------------------------------------------

    @Test
    fun error_state_is_handled() {
        coEvery { repository.listTournaments() } returns Result.failure(Exception("Network error"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.tournaments.isEmpty())
    }

    // ---------------------------------------------------------------
    // 12. Loading state is set correctly
    // ---------------------------------------------------------------

    @Test
    fun loading_state_is_set_correctly() {
        mockListTournaments()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loading_state_is_false_after_error() {
        coEvery { repository.listTournaments() } returns Result.failure(Exception("fail"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }
}

package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.ProposalSignature
import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.di.ServiceLocator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GovernanceViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var apiService: ApiService
    private lateinit var teeAuth: TeeAuthManager
    private lateinit var authCoordinator: AuthCoordinator
    private lateinit var pushEvents: MutableSharedFlow<PushService.WebSocketEvent>
    private lateinit var viewModel: GovernanceViewModel

    private val mockProposals = listOf(
        Proposal(
            id = "proposal-1",
            proposalType = "config",
            payload = buildJsonObject { put("key", JsonPrimitive("value")) },
            proposer = "pk-1",
            expiresAt = "2024-12-31T00:00:00Z",
            signatures = emptyList(),
            status = "active",
            createdAt = "2024-01-01T00:00:00Z"
        ),
        Proposal(
            id = "proposal-2",
            proposalType = "membership",
            payload = buildJsonObject { put("member", JsonPrimitive("pk-2")) },
            proposer = "pk-1",
            expiresAt = "2024-12-31T00:00:00Z",
            signatures = listOf(ProposalSignature(pubkey = "pk-3", signature = "sig1", signedAt = "2024-01-02T00:00:00Z")),
            status = "pending",
            createdAt = "2024-01-02T00:00:00Z"
        ),
        Proposal(
            id = "proposal-3",
            proposalType = "config",
            payload = buildJsonObject { put("key", JsonPrimitive("value2")) },
            proposer = "pk-2",
            expiresAt = "2024-12-31T00:00:00Z",
            signatures = emptyList(),
            status = "executed",
            createdAt = "2024-01-03T00:00:00Z",
            executedAt = "2024-01-04T00:00:00Z"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiService = mockk()
        teeAuth = mockk()
        authCoordinator = mockk()

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

    private fun mockListProposals(proposals: List<Proposal> = mockProposals) {
        coEvery { apiService.listProposals() } returns Result.success(proposals)
    }

    private fun createViewModel() {
        viewModel = GovernanceViewModel(apiService, teeAuth, authCoordinator)
    }

    private fun makeEvent(type: String) = PushService.WebSocketEvent(
        type = type,
        data = JsonObject(emptyMap())
    )

    // ---------------------------------------------------------------
    // 1. Initial state loads proposals
    // ---------------------------------------------------------------

    @Test
    fun initial_state_loads_proposals() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockProposals, viewModel.uiState.value.proposals)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        coVerify(exactly = 1) { apiService.listProposals() }
    }

    // ---------------------------------------------------------------
    // 2. Push event "proposal_created" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_proposal_created_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("proposal_created"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { apiService.listProposals() }
    }

    // ---------------------------------------------------------------
    // 3. Push event "proposal_signed" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_proposal_signed_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("proposal_signed"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { apiService.listProposals() }
    }

    // ---------------------------------------------------------------
    // 4. Push event "proposal_executed" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_proposal_executed_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("proposal_executed"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { apiService.listProposals() }
    }

    // ---------------------------------------------------------------
    // 5. Push event "proposal_rejected" triggers refresh
    // ---------------------------------------------------------------

    @Test
    fun push_event_proposal_rejected_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("proposal_rejected"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { apiService.listProposals() }
    }

    // ---------------------------------------------------------------
    // 6. Non-matching push event does not refresh
    // ---------------------------------------------------------------

    @Test
    fun non_matching_push_event_does_not_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makeEvent("unrelated_event"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { apiService.listProposals() }
    }

    // ---------------------------------------------------------------
    // 7. StatusFilter filters proposals correctly
    // ---------------------------------------------------------------

    @Test
    fun statusFilter_filters_proposals_correctly() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.proposals.size)

        coEvery { apiService.listProposals() } returns Result.success(mockProposals)
        viewModel.setStatusFilter("active")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("active", viewModel.uiState.value.statusFilter)
        assertEquals(1, viewModel.uiState.value.proposals.size)
        assertEquals("proposal-1", viewModel.uiState.value.proposals.first().id)
    }

    // ---------------------------------------------------------------
    // 8. ShowCreateDialog opens dialog
    // ---------------------------------------------------------------

    @Test
    fun showCreateDialog_opens_dialog() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCreateDialog)

        viewModel.showCreateDialog()
        assertTrue(viewModel.uiState.value.showCreateDialog)
        assertNull(viewModel.uiState.value.createError)
    }

    // ---------------------------------------------------------------
    // 9. DismissCreateDialog closes dialog
    // ---------------------------------------------------------------

    @Test
    fun dismissCreateDialog_closes_dialog() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCreateDialog()
        assertTrue(viewModel.uiState.value.showCreateDialog)

        viewModel.dismissCreateDialog()
        assertFalse(viewModel.uiState.value.showCreateDialog)
        assertNull(viewModel.uiState.value.createError)
    }

    // ---------------------------------------------------------------
    // 10. ShowProposalDetail selects proposal
    // ---------------------------------------------------------------

    @Test
    fun showProposalDetail_selects_proposal() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedProposal)

        viewModel.showProposalDetail(mockProposals.first())
        assertEquals(mockProposals.first(), viewModel.uiState.value.selectedProposal)
        assertNull(viewModel.uiState.value.signError)
    }

    // ---------------------------------------------------------------
    // 11. DismissProposalDetail clears selection
    // ---------------------------------------------------------------

    @Test
    fun dismissProposalDetail_clears_selection() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showProposalDetail(mockProposals.first())
        assertNotNull(viewModel.uiState.value.selectedProposal)

        viewModel.dismissProposalDetail()
        assertNull(viewModel.uiState.value.selectedProposal)
        assertNull(viewModel.uiState.value.signError)
    }

    // ---------------------------------------------------------------
    // 12. Error state is handled
    // ---------------------------------------------------------------

    @Test
    fun error_state_is_handled() {
        coEvery { apiService.listProposals() } returns Result.failure(Exception("Network error"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.proposals.isEmpty())
    }

    // ---------------------------------------------------------------
    // 13. Loading state is set correctly
    // ---------------------------------------------------------------

    @Test
    fun loading_state_is_set_correctly() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loading_state_is_false_after_error() {
        coEvery { apiService.listProposals() } returns Result.failure(Exception("fail"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }
}

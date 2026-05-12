package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.MemberSummary
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.ProposalSignature
import com.jlucraft.console.data.model.GenericPushEventData
import com.jlucraft.console.data.model.AddNodeProposalPayload
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.EventEnvelope
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
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
    private lateinit var client: Libp2pClient
    private lateinit var teeAuth: TeeAuthManager
    private lateinit var authCoordinator: AuthCoordinator
    private lateinit var pushEvents: MutableSharedFlow<PushService.PushEvent>
    private lateinit var pushService: PushService
    private lateinit var viewModel: GovernanceViewModel
    private lateinit var governanceEvents: MutableSharedFlow<EventEnvelope>

    private val mockProposals = listOf(
        Proposal(
            id = "proposal-1",
            proposalType = "config",
            payload = AddNodeProposalPayload(description = "node-1", target = "pk-1"),
            proposer = "pk-1",
            expiresAt = "2024-12-31T00:00:00Z",
            signatures = emptyList(),
            status = "active",
            createdAt = "2024-01-01T00:00:00Z"
        ),
        Proposal(
            id = "proposal-2",
            proposalType = "membership",
            payload = AddNodeProposalPayload(description = "node-2", target = "pk-2"),
            proposer = "pk-1",
            expiresAt = "2024-12-31T00:00:00Z",
            signatures = listOf(ProposalSignature(pubkey = "pk-3", signature = "sig1", signedAt = "2024-01-02T00:00:00Z")),
            status = "pending",
            createdAt = "2024-01-02T00:00:00Z"
        ),
        Proposal(
            id = "proposal-3",
            proposalType = "config",
            payload = AddNodeProposalPayload(description = "node-3", target = "pk-3"),
            proposer = "pk-2",
            expiresAt = "2024-12-31T00:00:00Z",
            signatures = emptyList(),
            status = "executed",
            createdAt = "2024-01-03T00:00:00Z",
            executedAt = "2024-01-04T00:00:00Z"
        )
    )

    private val mockMembers = listOf(
        MemberSummary(
            subjectDid = "did:member:1",
            role = "member"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        client = mockk()
        teeAuth = mockk()
        authCoordinator = mockk()
        governanceEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 8)

        pushEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        pushService = mockk()
        every { pushService.events } returns pushEvents
        coEvery { client.subscribeGovernanceEvents() } returns governanceEvents
        coEvery { client.listMembers() } returns Result.success(mockMembers)
        every { authCoordinator.clearAuth() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockListProposals(proposals: List<Proposal> = mockProposals) {
        coEvery { client.listProposals() } returns Result.success(proposals)
    }

    private fun createViewModel() {
        viewModel = GovernanceViewModel(client, teeAuth, authCoordinator, pushService)
    }

    private fun makePushEvent(type: String) = PushService.PushEvent(
        type = type,
        data = GenericPushEventData(raw = "")
    )

    private fun makeGovernanceEvent(
        eventType: String,
    ) = EventEnvelope(
        topic = "governance",
        eventType = eventType,
        timestamp = "2026-05-05T00:00:00Z",
        payload = ByteArray(0)
    )





    @Test
    fun initial_state_loads_proposals() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockProposals, viewModel.uiState.value.proposals)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        coVerify(exactly = 1) { client.listProposals() }
    }





    @Test
    fun push_event_proposal_created_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makePushEvent("proposal_created"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.listProposals() }
    }





    @Test
    fun push_event_proposal_signed_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makePushEvent("proposal_signed"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.listProposals() }
    }





    @Test
    fun push_event_proposal_executed_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makePushEvent("proposal_executed"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.listProposals() }
    }





    @Test
    fun push_event_proposal_rejected_triggers_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makePushEvent("proposal_rejected"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.listProposals() }
    }





    @Test
    fun non_matching_push_event_does_not_refresh() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        pushEvents.tryEmit(makePushEvent("unrelated_event"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { client.listProposals() }
    }





    @Test
    fun statusFilter_filters_proposals_correctly() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.proposals.size)

        coEvery { client.listProposals() } returns Result.success(mockProposals)
        viewModel.setStatusFilter("active")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("active", viewModel.uiState.value.statusFilter)
        assertEquals(1, viewModel.uiState.value.proposals.size)
        assertEquals("proposal-1", viewModel.uiState.value.proposals.first().id)
    }





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





    @Test
    fun error_state_is_handled() {
        coEvery { client.listProposals() } returns Result.failure(Exception("Network error"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.proposals.isEmpty())
    }





    @Test
    fun loading_state_is_set_correctly() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loading_state_is_false_after_error() {
        coEvery { client.listProposals() } returns Result.failure(Exception("fail"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun governance_stream_event_updates_status_and_last_event() {
        mockListProposals()
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        governanceEvents.tryEmit(makeGovernanceEvent("proposal_created"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(StreamStatus.CONNECTED, viewModel.uiState.value.streamStatus)
        assertEquals("2026-05-05T00:00:00Z", viewModel.uiState.value.lastEventTime)
        coVerify(exactly = 2) { client.listProposals() }
    }

    @Test
    fun member_update_event_refreshes_members_when_members_loaded() {
        mockListProposals()
        createViewModel()
        viewModel.setActiveTab("members")
        testDispatcher.scheduler.advanceUntilIdle()

        governanceEvents.tryEmit(makeGovernanceEvent("member_updated"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(StreamStatus.CONNECTED, viewModel.uiState.value.streamStatus)
        assertEquals(mockMembers, viewModel.uiState.value.members)
        coVerify(atLeast = 2) { client.listMembers() }
    }
}

package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.GenericPushEventData
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.remote.ClusterHealthResponse
import com.jlucraft.console.data.remote.NetworkSnapshot
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import com.jlucraft.console.data.repository.NodeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var client: Libp2pClient
    private lateinit var repository: NodeRepository
    private lateinit var pushEvents: MutableSharedFlow<PushService.PushEvent>
    private lateinit var pushService: PushService

    private val health = ClusterHealthResponse(
        status = "healthy",
        peer_id = "peer-1",
        connected_peers = 3,
        running_instances = 5,
        consensus_role = "leader"
    )
    private val network = NetworkSnapshot(connected_peers = listOf("peer-1"))
    private val scores = listOf(
        NodeScore(
            peer_id = "peer-1",
            uptime_score = 0.95,
            performance_score = 0.88,
            governance_score = 0.92,
            penalty = 0.0,
            final_score = 0.91,
            last_updated = "2024-01-01T00:00:00Z"
        )
    )
    private val instances = listOf(
        Instance(
            id = "inst-1",
            name = "test-instance",
            kind = "game",
            status = "running",
            currentHost = "host-1",
            createdAt = "2024-01-01T00:00:00Z"
        )
    )
    private val alerts = listOf(
        Alert(
            id = "alert-1",
            alertType = "node_down",
            severity = "critical",
            message = "Node is down",
            target = "peer-1",
            createdAt = "2024-01-01T00:00:00Z"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        client = mockk()
        repository = mockk()
        pushEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 8)
        pushService = mockk()
        every { pushService.events } returns pushEvents
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockSuccess() {
        coEvery { repository.getClusterHealth() } returns Result.success(health)
        coEvery { repository.getNetworkSnapshot() } returns Result.success(network)
        coEvery { repository.listNodeScores() } returns Result.success(scores)
        coEvery { client.getInstances() } returns Result.success(instances)
        coEvery { client.listAlerts(includeResolved = false) } returns Result.success(alerts)
    }

    private fun createViewModel(): DashboardViewModel = DashboardViewModel(client, repository, pushService)

    private fun pushEvent(type: String) = PushService.PushEvent(type, GenericPushEventData(raw = ""))

    @Test
    fun `initial state loads dashboard data`() = runTest {
        mockSuccess()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(health, state.health)
        assertEquals(network, state.network)
        assertEquals(scores, state.nodeScores)
        assertEquals(instances, state.instances)
        assertEquals(alerts, state.alerts)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `cluster push event triggers refresh`() = runTest {
        mockSuccess()
        createViewModel()
        advanceUntilIdle()

        pushEvents.tryEmit(pushEvent("cluster_health"))
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getClusterHealth() }
        coVerify(exactly = 2) { client.getInstances() }
        coVerify(exactly = 2) { client.listAlerts(includeResolved = false) }
    }

    @Test
    fun `unrelated push event does not refresh`() = runTest {
        mockSuccess()
        createViewModel()
        advanceUntilIdle()

        pushEvents.tryEmit(pushEvent("unrelated"))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getClusterHealth() }
        coVerify(exactly = 1) { client.getInstances() }
    }

    @Test
    fun `first failing source is exposed as error`() = runTest {
        coEvery { repository.getClusterHealth() } returns Result.failure(RuntimeException("health fetch failed"))
        coEvery { repository.getNetworkSnapshot() } returns Result.success(network)
        coEvery { repository.listNodeScores() } returns Result.success(scores)
        coEvery { client.getInstances() } returns Result.success(instances)
        coEvery { client.listAlerts(includeResolved = false) } returns Result.success(alerts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("health fetch failed", viewModel.uiState.value.error)
    }
}

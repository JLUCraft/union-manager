package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.remote.ClusterHealthResponse
import com.jlucraft.console.data.remote.NetworkSnapshot
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.di.ServiceLocator
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
class DashboardViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: NodeRepository
    private lateinit var pushEvents: MutableSharedFlow<PushService.WebSocketEvent>

    private val mockHealth = ClusterHealthResponse(
        status = "healthy",
        peer_id = "peer-1",
        connected_peers = 3,
        running_instances = 5,
        consensus_role = "leader"
    )

    private val mockNetwork = NetworkSnapshot(
        connected_peers = listOf("peer-1", "peer-2", "peer-3")
    )

    private val mockScores = listOf(
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

    private val mockInstances = listOf(
        Instance(
            id = "inst-1",
            name = "test-instance",
            kind = "game",
            status = "running",
            currentHost = "host-1",
            createdAt = "2024-01-01T00:00:00Z"
        )
    )

    private val mockAlerts = listOf(
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
        mockRepository = mockk()
        pushEvents = MutableSharedFlow()
        val mockPushService = mockk<PushService>()
        every { mockPushService.events } returns pushEvents
        ServiceLocator.pushService = mockPushService
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockAllRepositoryCalls() {
        coEvery { mockRepository.getClusterHealth() } returns Result.success(mockHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)
    }

    private fun createViewModel(): DashboardViewModel = DashboardViewModel(mockRepository)

    private fun makeEvent(type: String) = PushService.WebSocketEvent(
        type = type,
        data = kotlinx.serialization.json.buildJsonObject { put("dummy", kotlinx.serialization.json.JsonPrimitive("v")) }
    )

    // ------------------------------------------------------------------ //
    // 1. Initial state loads all data
    // ------------------------------------------------------------------ //

    @Test
    fun `initial state loads cluster health`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(mockHealth, viewModel.uiState.value.health)
    }

    @Test
    fun `initial state loads network snapshot`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(mockNetwork, viewModel.uiState.value.network)
    }

    @Test
    fun `initial state loads node scores`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(mockScores, viewModel.uiState.value.nodeScores)
    }

    @Test
    fun `initial state loads instances`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(mockInstances, viewModel.uiState.value.instances)
    }

    @Test
    fun `initial state loads alerts`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(mockAlerts, viewModel.uiState.value.alerts)
    }

    @Test
    fun `initial state loads all data in a single refresh`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(mockHealth, state.health)
        assertEquals(mockNetwork, state.network)
        assertEquals(mockScores, state.nodeScores)
        assertEquals(mockInstances, state.instances)
        assertEquals(mockAlerts, state.alerts)
        assertFalse(state.isLoading)
        assertNull(state.error)

        coVerify(exactly = 1) { mockRepository.getClusterHealth() }
        coVerify(exactly = 1) { mockRepository.getNetworkSnapshot() }
        coVerify(exactly = 1) { mockRepository.listNodeScores() }
        coVerify(exactly = 1) { mockRepository.getInstances() }
        coVerify(exactly = 1) { mockRepository.listAlerts(includeResolved = false) }
    }

    // ------------------------------------------------------------------ //
    // 2. Refresh updates all state fields
    // ------------------------------------------------------------------ //

    @Test
    fun `refresh updates all state fields correctly`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        val updatedHealth = mockHealth.copy(status = "degraded", running_instances = 3)
        val updatedNetwork = mockNetwork.copy(connected_peers = listOf("peer-1"))
        val updatedScores = listOf(
            NodeScore(
                peer_id = "peer-2",
                uptime_score = 0.80,
                performance_score = 0.75,
                governance_score = 0.85,
                penalty = 0.0,
                final_score = 0.80,
                last_updated = "2024-01-02T00:00:00Z"
            )
        )
        val updatedInstances = listOf(
            Instance(
                id = "inst-2",
                name = "another-instance",
                kind = "worker",
                status = "stopped",
                currentHost = "host-2",
                createdAt = "2024-01-02T00:00:00Z"
            )
        )
        val updatedAlerts = listOf(
            Alert(
                id = "alert-2",
                alertType = "cpu_spike",
                severity = "warning",
                message = "CPU spike detected",
                target = "peer-2",
                createdAt = "2024-01-02T00:00:00Z"
            )
        )

        coEvery { mockRepository.getClusterHealth() } returns Result.success(updatedHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(updatedNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(updatedScores)
        coEvery { mockRepository.getInstances() } returns Result.success(updatedInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(updatedAlerts)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(updatedHealth, state.health)
        assertEquals(updatedNetwork, state.network)
        assertEquals(updatedScores, state.nodeScores)
        assertEquals(updatedInstances, state.instances)
        assertEquals(updatedAlerts, state.alerts)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ------------------------------------------------------------------ //
    // 3. Push event "cluster_health" triggers refresh
    // ------------------------------------------------------------------ //

    @Test
    fun `push event cluster_health triggers refresh`() = runTest {
        mockAllRepositoryCalls()
        createViewModel()
        advanceUntilIdle()

        pushEvents.emit(makeEvent("cluster_health"))
        advanceUntilIdle()

        coVerify(exactly = 2) { mockRepository.getClusterHealth() }
        coVerify(exactly = 2) { mockRepository.getNetworkSnapshot() }
        coVerify(exactly = 2) { mockRepository.listNodeScores() }
        coVerify(exactly = 2) { mockRepository.getInstances() }
        coVerify(exactly = 2) { mockRepository.listAlerts(includeResolved = false) }
    }

    // ------------------------------------------------------------------ //
    // 4. Push event "alerts" triggers refresh
    // ------------------------------------------------------------------ //

    @Test
    fun `push event alerts triggers refresh`() = runTest {
        mockAllRepositoryCalls()
        createViewModel()
        advanceUntilIdle()

        pushEvents.emit(makeEvent("alerts"))
        advanceUntilIdle()

        coVerify(exactly = 2) { mockRepository.getClusterHealth() }
        coVerify(exactly = 2) { mockRepository.getNetworkSnapshot() }
        coVerify(exactly = 2) { mockRepository.listNodeScores() }
        coVerify(exactly = 2) { mockRepository.getInstances() }
        coVerify(exactly = 2) { mockRepository.listAlerts(includeResolved = false) }
    }

    // ------------------------------------------------------------------ //
    // 5. Push event "proposals" triggers refresh
    // ------------------------------------------------------------------ //

    @Test
    fun `push event proposals triggers refresh`() = runTest {
        mockAllRepositoryCalls()
        createViewModel()
        advanceUntilIdle()

        pushEvents.emit(makeEvent("proposals"))
        advanceUntilIdle()

        coVerify(exactly = 2) { mockRepository.getClusterHealth() }
        coVerify(exactly = 2) { mockRepository.getNetworkSnapshot() }
        coVerify(exactly = 2) { mockRepository.listNodeScores() }
        coVerify(exactly = 2) { mockRepository.getInstances() }
        coVerify(exactly = 2) { mockRepository.listAlerts(includeResolved = false) }
    }

    // ------------------------------------------------------------------ //
    // 6. Non-matching push event does NOT trigger refresh
    // ------------------------------------------------------------------ //

    @Test
    fun `push event instance_status does NOT trigger refresh`() = runTest {
        mockAllRepositoryCalls()
        createViewModel()
        advanceUntilIdle()

        pushEvents.emit(makeEvent("instance_status"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.getClusterHealth() }
        coVerify(exactly = 1) { mockRepository.getNetworkSnapshot() }
        coVerify(exactly = 1) { mockRepository.listNodeScores() }
        coVerify(exactly = 1) { mockRepository.getInstances() }
        coVerify(exactly = 1) { mockRepository.listAlerts(includeResolved = false) }
    }

    @Test
    fun `push event unknown type does NOT trigger refresh`() = runTest {
        mockAllRepositoryCalls()
        createViewModel()
        advanceUntilIdle()

        pushEvents.emit(makeEvent("unknown"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.getClusterHealth() }
        coVerify(exactly = 1) { mockRepository.getNetworkSnapshot() }
        coVerify(exactly = 1) { mockRepository.listNodeScores() }
        coVerify(exactly = 1) { mockRepository.getInstances() }
        coVerify(exactly = 1) { mockRepository.listAlerts(includeResolved = false) }
    }

    // ------------------------------------------------------------------ //
    // 7. Error from repository is captured in state
    // ------------------------------------------------------------------ //

    @Test
    fun `error from cluster health is captured in state`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.failure(RuntimeException("health fetch failed"))
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("health fetch failed", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.health)
        assertNotNull(viewModel.uiState.value.network)
    }

    @Test
    fun `error from alerts is captured in state when health succeeds`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.success(mockHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.failure(RuntimeException("alerts fetch failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("alerts fetch failed", viewModel.uiState.value.error)
        assertNotNull(viewModel.uiState.value.health)
        assertTrue(viewModel.uiState.value.alerts.isEmpty())
    }

    @Test
    fun `multiple errors capture the first one in chain`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.success(mockHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.failure(RuntimeException("network error"))
        coEvery { mockRepository.listNodeScores() } returns Result.failure(RuntimeException("scores error"))
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("network error", viewModel.uiState.value.error)
    }

    // ------------------------------------------------------------------ //
    // 8. Loading state is set correctly during refresh
    // ------------------------------------------------------------------ //

    @Test
    fun `isLoading is false after successful refresh`() = runTest {
        mockAllRepositoryCalls()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `isLoading is false after failed refresh`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.failure(RuntimeException("boom"))
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `isLoading is true while async tasks are in flight`() = runTest {
        var capturedViewModel: DashboardViewModel? = null
        var capturedIsLoading: Boolean? = null

        coEvery { mockRepository.getClusterHealth() } coAnswers {
            capturedIsLoading = capturedViewModel!!.uiState.value.isLoading
            Result.success(mockHealth)
        }
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)

        val viewModel = DashboardViewModel(mockRepository)
        capturedViewModel = viewModel

        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, capturedIsLoading)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ------------------------------------------------------------------ //
    // 9. Empty lists are handled correctly
    // ------------------------------------------------------------------ //

    @Test
    fun `empty node scores list is handled`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.success(mockHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(emptyList())
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.nodeScores.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `empty instances list is handled`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.success(mockHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(emptyList())
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.instances.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `empty alerts list is handled`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.success(mockHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.alerts.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `all empty lists are handled`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.success(mockHealth)
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(emptyList())
        coEvery { mockRepository.getInstances() } returns Result.success(emptyList())
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.nodeScores.isEmpty())
        assertTrue(viewModel.uiState.value.instances.isEmpty())
        assertTrue(viewModel.uiState.value.alerts.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `null health is propagated as null when repository fails`() = runTest {
        coEvery { mockRepository.getClusterHealth() } returns Result.failure(RuntimeException("down"))
        coEvery { mockRepository.getNetworkSnapshot() } returns Result.success(mockNetwork)
        coEvery { mockRepository.listNodeScores() } returns Result.success(mockScores)
        coEvery { mockRepository.getInstances() } returns Result.success(mockInstances)
        coEvery { mockRepository.listAlerts(includeResolved = false) } returns Result.success(mockAlerts)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.health)
    }
}

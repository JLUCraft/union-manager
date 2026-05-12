package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.TeeCapability
import com.jlucraft.console.data.model.ApplySchedulingConstraintsRequest
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.SchedulingConstraints
import com.jlucraft.console.data.model.SchedulingConstraintsResponse
import com.jlucraft.console.data.model.SchedulingPreset
import com.jlucraft.console.data.model.SchedulingSimulation
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulingViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockClient: Libp2pClient
    private lateinit var mockTeeAuth: TeeAuthManager
    private lateinit var mockAuthCoordinator: AuthCoordinator

    private val testInstanceId = "inst-test-1"

    private val mockConstraints = SchedulingConstraints(
        minNodeScore = 50.0,
        priorityClass = "normal",
        preemptible = false
    )

    private val mockSimulation = SchedulingSimulation(
        instanceId = testInstanceId,
        currentHost = "host-1",
        wouldMigrate = false,
        eligibleHosts = listOf("host-1", "host-2"),
        constraintViolations = emptyList()
    )

    private val mockResponse = SchedulingConstraintsResponse(
        instanceId = testInstanceId,
        constraints = mockConstraints,
        appliedAt = "2024-01-01T00:00:00Z",
        previousConstraints = null,
        warnings = emptyList()
    )

    private val mockInstances = listOf(
        Instance(
            id = "inst-1",
            name = "Game Server Alpha",
            kind = "gameserver",
            status = "running",
            currentHost = "host-1",
            playerCount = 12,
            createdAt = "2024-01-01T00:00:00Z"
        ),
        Instance(
            id = "inst-2",
            name = "Game Server Beta",
            kind = "gameserver",
            status = "stopped",
            currentHost = "host-2",
            playerCount = 0,
            createdAt = "2024-01-02T00:00:00Z"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockClient = mockk()
        mockTeeAuth = mockk {
            every { isTeeBacked } returns true
        }
        mockAuthCoordinator = mockk(relaxed = true)
        coEvery { mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): SchedulingViewModel = SchedulingViewModel(mockClient, mockTeeAuth, mockAuthCoordinator)



    @Test
    fun `loadInstances populates instance list`() = runTest {
        coEvery { mockClient.getInstances() } returns Result.success(mockInstances)
        val viewModel = createViewModel()

        viewModel.loadInstances()
        advanceUntilIdle()

        assertEquals(mockInstances, viewModel.uiState.value.allInstances)
        assertNull(viewModel.uiState.value.instancesError)
        coVerify(exactly = 1) { mockClient.getInstances() }
    }

    @Test
    fun `loadInstances handles error`() = runTest {
        coEvery { mockClient.getInstances() } returns Result.failure(RuntimeException("network error"))
        val viewModel = createViewModel()

        viewModel.loadInstances()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.allInstances)
        assertEquals("network error", viewModel.uiState.value.instancesError)
    }

    @Test
    fun `selectInstance sets instanceId and loads constraints`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(mockInstances[0].id) } returns Result.success(mockConstraints)
        val viewModel = createViewModel()

        viewModel.selectInstance(mockInstances[0])
        advanceUntilIdle()

        assertEquals(mockInstances[0].id, viewModel.uiState.value.instanceId)
        assertEquals(mockConstraints, viewModel.uiState.value.constraints)
        coVerify(exactly = 1) { mockClient.getSchedulingConstraints(mockInstances[0].id) }
    }

    @Test
    fun `initial state has no instanceId and no constrains`() {
        val viewModel = createViewModel()

        assertEquals("", viewModel.uiState.value.instanceId)
        assertNull(viewModel.uiState.value.constraints)
        assertNull(viewModel.uiState.value.allInstances)
    }



    @Test
    fun `set instance ID loads constraints`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.success(mockConstraints)
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()

        assertEquals(testInstanceId, viewModel.uiState.value.instanceId)
        assertEquals(mockConstraints, viewModel.uiState.value.constraints)
        coVerify(exactly = 1) { mockClient.getSchedulingConstraints(testInstanceId) }
    }

    @Test
    fun `set instance ID handles load error`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.failure(RuntimeException("not found"))
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()

        assertEquals("not found", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.constraints)
    }

    @Test
    fun `set empty instance ID does not trigger load`() = runTest {
        val viewModel = createViewModel()

        viewModel.setInstanceId("")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.instanceId)
        coVerify(exactly = 0) { mockClient.getSchedulingConstraints(any()) }
    }



    @Test
    fun `apply preset sets constraints`() {
        val viewModel = createViewModel()

        viewModel.applyPreset(SchedulingPreset.HIGH_AVAILABILITY)

        val constraints = viewModel.uiState.value.constraints
        assertNotNull(constraints)
        assertEquals(80.0, constraints!!.minNodeScore, 0.01)
        assertEquals(true, constraints.dedicatedHost)
        assertEquals("high", constraints.priorityClass)
        assertEquals(SchedulingPreset.HIGH_AVAILABILITY, viewModel.uiState.value.preset)
    }

    @Test
    fun `apply preset clears previous preset on manual update`() {
        val viewModel = createViewModel()
        viewModel.applyPreset(SchedulingPreset.BALANCED)
        assertNotNull(viewModel.uiState.value.preset)

        viewModel.updateConstraints(mockConstraints.copy(minNodeScore = 99.0))
        assertNull(viewModel.uiState.value.preset)
    }



    @Test
    fun `simulate scheduling succeeds`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.success(mockConstraints)
        coEvery { mockClient.simulateScheduling(testInstanceId, mockConstraints) } returns Result.success(mockSimulation)
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()

        viewModel.simulateScheduling()
        advanceUntilIdle()

        assertEquals(mockSimulation, viewModel.uiState.value.simResult)
        assertNull(viewModel.uiState.value.simError)
        coVerify(exactly = 1) { mockClient.simulateScheduling(testInstanceId, mockConstraints) }
    }

    @Test
    fun `simulate scheduling fails`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.success(mockConstraints)
        coEvery { mockClient.simulateScheduling(testInstanceId, mockConstraints) } returns Result.failure(RuntimeException("sim error"))
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()

        viewModel.simulateScheduling()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.simResult)
        assertEquals("sim error", viewModel.uiState.value.simError)
    }

    @Test
    fun `simulate scheduling does nothing without constraints`() = runTest {
        val viewModel = createViewModel()

        viewModel.simulateScheduling()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockClient.simulateScheduling(any(), any()) }
    }



    @Test
    fun `apply constraints succeeds`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.success(mockConstraints)
        coEvery { mockClient.applySchedulingConstraints(any()) } returns Result.success(mockResponse)
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()

        viewModel.applyConstraints("manual update")
        advanceUntilIdle()

        assertEquals(mockResponse, viewModel.uiState.value.applyResult)
        assertEquals("调度约束已成功应用", viewModel.uiState.value.applySuccess)
        coVerify(exactly = 1) { mockClient.applySchedulingConstraints(any()) }
    }

    @Test
    fun `apply constraints fails`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.success(mockConstraints)
        coEvery { mockClient.applySchedulingConstraints(any()) } returns Result.failure(RuntimeException("apply error"))
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()

        viewModel.applyConstraints("manual update")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.applyResult)
        assertEquals("apply error", viewModel.uiState.value.applyError)
    }

    @Test
    fun `apply constraints does nothing without constraints`() = runTest {
        val viewModel = createViewModel()

        viewModel.applyConstraints("test")
        advanceUntilIdle()

        coVerify(exactly = 0) { mockClient.applySchedulingConstraints(any()) }
    }

    @Test
    fun `apply constraints rejected on read-only device`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.success(mockConstraints)
        every { mockTeeAuth.isTeeBacked } returns false
        every { mockTeeAuth.capability } returns TeeCapability.NoHardwareBackedKey("emulator")
        coEvery { mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns
            Result.failure(com.jlucraft.console.data.auth.ReadOnlyDeviceException.fromCapability(mockTeeAuth.capability))
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()

        viewModel.applyConstraints("should be blocked")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.applyError)
        assertTrue(viewModel.uiState.value.applyError!!.contains("管理写操作已被禁止"))
        coVerify(exactly = 0) { mockClient.applySchedulingConstraints(any()) }
    }



    @Test
    fun `clear apply state resets apply fields`() = runTest {
        coEvery { mockClient.getSchedulingConstraints(testInstanceId) } returns Result.success(mockConstraints)
        coEvery { mockClient.applySchedulingConstraints(any()) } returns Result.success(mockResponse)
        val viewModel = createViewModel()

        viewModel.setInstanceId(testInstanceId)
        advanceUntilIdle()
        viewModel.applyConstraints("test")
        advanceUntilIdle()

        viewModel.clearApplyState()

        assertNull(viewModel.uiState.value.applySuccess)
        assertNull(viewModel.uiState.value.applyError)
    }
}

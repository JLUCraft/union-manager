package com.jlucraft.console.viewmodel

import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.repository.NodeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstancesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: NodeRepository
    private lateinit var authCoordinator: AuthCoordinator
    private lateinit var pushEvents: MutableSharedFlow<PushService.WebSocketEvent>
    private lateinit var pushService: PushService

    private lateinit var viewModel: InstancesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk()
        authCoordinator = mockk()

        pushEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        pushService = mockk()
        every { pushService.events } returns pushEvents

        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
        every { authCoordinator.clearAuth() } just runs

        coEvery { repository.getInstances() } returns Result.success(emptyList())
        coEvery { repository.startInstance(any()) } returns Result.success(Unit)
        coEvery { repository.stopInstance(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() {
        viewModel = InstancesViewModel(repository, authCoordinator, pushService)
    }

    private fun createInstance(
        id: String = "inst-1",
        name: String = "test-instance",
        kind: String = "room",
        status: String = "running",
        currentHost: String = "node-1"
    ) = Instance(
        id = id,
        name = name,
        kind = kind,
        status = status,
        currentHost = currentHost,
        playerCount = 0,
        createdAt = "2024-01-01T00:00:00Z"
    )

    private fun createInstances(count: Int): List<Instance> =
        (1..count).map { i ->
            createInstance(
                id = "inst-$i",
                name = "instance-$i",
                currentHost = "node-$i"
            )
        }

    // ---------------------------------------------------------------
    // Test 1: initial state loads instances
    // ---------------------------------------------------------------
    @Test
    fun initial_state_loads_instances() {
        val instances = createInstances(3)
        coEvery { repository.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInstances() }
        assertEquals(3, viewModel.uiState.value.instances.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------
    // Test 2: refresh updates instances list
    // ---------------------------------------------------------------
    @Test
    fun refresh_updates_instances_list() {
        coEvery { repository.getInstances() } returns Result.success(createInstances(2))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.instances.size)

        coEvery { repository.getInstances() } returns Result.success(createInstances(4))
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.getInstances() }
        assertEquals(4, viewModel.uiState.value.instances.size)
    }

    // ---------------------------------------------------------------
    // Test 3: push event instance_started triggers refresh
    // ---------------------------------------------------------------
    @Test
    fun push_event_instance_started_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.getInstances() }

        pushEvents.tryEmit(PushService.WebSocketEvent("instance_started", JsonObject(emptyMap())))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.getInstances() }
    }

    // ---------------------------------------------------------------
    // Test 4: push event instance_stopped triggers refresh
    // ---------------------------------------------------------------
    @Test
    fun push_event_instance_stopped_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.getInstances() }

        pushEvents.tryEmit(PushService.WebSocketEvent("instance_stopped", JsonObject(emptyMap())))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.getInstances() }
    }

    // ---------------------------------------------------------------
    // Test 5: push event instance_crash triggers refresh
    // ---------------------------------------------------------------
    @Test
    fun push_event_instance_crash_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.getInstances() }

        pushEvents.tryEmit(PushService.WebSocketEvent("instance_crash", JsonObject(emptyMap())))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.getInstances() }
    }

    // ---------------------------------------------------------------
    // Test 6: push event instance_created triggers refresh
    // ---------------------------------------------------------------
    @Test
    fun push_event_instance_created_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.getInstances() }

        pushEvents.tryEmit(PushService.WebSocketEvent("instance_created", JsonObject(emptyMap())))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.getInstances() }
    }

    // ---------------------------------------------------------------
    // Test 7: push event instance_deleted triggers refresh
    // ---------------------------------------------------------------
    @Test
    fun push_event_instance_deleted_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.getInstances() }

        pushEvents.tryEmit(PushService.WebSocketEvent("instance_deleted", JsonObject(emptyMap())))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.getInstances() }
    }

    // ---------------------------------------------------------------
    // Test 8: non-matching push event does NOT refresh
    // ---------------------------------------------------------------
    @Test
    fun non_matching_push_event_does_not_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.getInstances() }

        pushEvents.tryEmit(PushService.WebSocketEvent("unrelated_event", JsonObject(emptyMap())))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInstances() }
    }

    // ---------------------------------------------------------------
    // Test 9: start instance calls repository correctly
    // ---------------------------------------------------------------
    @Test
    fun start_instance_calls_repository_correctly() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            authCoordinator.authenticateForOperation("start-instance", any(), "启动实例", "请验证身份以启动实例")
        }
        coVerify(exactly = 1) { repository.startInstance("inst-1") }
    }

    // ---------------------------------------------------------------
    // Test 10: stop instance calls repository correctly
    // ---------------------------------------------------------------
    @Test
    fun stop_instance_calls_repository_correctly() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.stopInstance("inst-2")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            authCoordinator.authenticateForOperation("stop-instance", any(), "停止实例", "请验证身份以停止实例")
        }
        coVerify(exactly = 1) { repository.stopInstance("inst-2") }
    }

    // ---------------------------------------------------------------
    // Test 11: batch mode toggle works
    // ---------------------------------------------------------------
    @Test
    fun batch_mode_toggle_works() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBatchMode)
        assertTrue(viewModel.uiState.value.selectedInstanceIds.isEmpty())

        viewModel.toggleBatchMode()
        assertTrue(viewModel.uiState.value.isBatchMode)
        assertTrue(viewModel.uiState.value.selectedInstanceIds.isEmpty())

        viewModel.toggleBatchMode()
        assertFalse(viewModel.uiState.value.isBatchMode)
        assertTrue(viewModel.uiState.value.selectedInstanceIds.isEmpty())
    }

    // ---------------------------------------------------------------
    // Test 12: selection toggle works (add, remove, limit 20)
    // ---------------------------------------------------------------
    @Test
    fun selection_toggle_adds_and_removes_instances() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleInstanceSelection("inst-1")
        assertEquals(setOf("inst-1"), viewModel.uiState.value.selectedInstanceIds)

        viewModel.toggleInstanceSelection("inst-2")
        assertEquals(setOf("inst-1", "inst-2"), viewModel.uiState.value.selectedInstanceIds)

        viewModel.toggleInstanceSelection("inst-1")
        assertEquals(setOf("inst-2"), viewModel.uiState.value.selectedInstanceIds)
    }

    @Test
    fun selection_toggle_blocks_adding_beyond_limit_of_20() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        for (i in 1..20) {
            viewModel.toggleInstanceSelection("inst-$i")
        }
        assertNull(viewModel.uiState.value.operationError)
        assertEquals(20, viewModel.uiState.value.selectedInstanceIds.size)

        viewModel.toggleInstanceSelection("inst-21")
        assertEquals(20, viewModel.uiState.value.selectedInstanceIds.size)
        assertNotNull(viewModel.uiState.value.operationError)
    }

    @Test
    fun selection_toggle_removes_instance_from_set() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleInstanceSelection("inst-1")
        viewModel.toggleInstanceSelection("inst-2")
        viewModel.toggleInstanceSelection("inst-1")

        assertFalse(viewModel.uiState.value.selectedInstanceIds.contains("inst-1"))
        assertTrue(viewModel.uiState.value.selectedInstanceIds.contains("inst-2"))
    }

    // ---------------------------------------------------------------
    // Test 13: selectAllInstances caps at 20
    // ---------------------------------------------------------------
    @Test
    fun selectAllInstances_caps_at_20() {
        val instances = createInstances(25)
        coEvery { repository.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAllInstances()
        assertEquals(20, viewModel.uiState.value.selectedInstanceIds.size)
        assertEquals(instances.take(20).map { it.id }.toSet(), viewModel.uiState.value.selectedInstanceIds)
    }

    @Test
    fun selectAllInstances_selects_all_when_less_than_20() {
        val instances = createInstances(5)
        coEvery { repository.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAllInstances()
        assertEquals(5, viewModel.uiState.value.selectedInstanceIds.size)
        assertEquals(instances.map { it.id }.toSet(), viewModel.uiState.value.selectedInstanceIds)
    }

    // ---------------------------------------------------------------
    // Test 14: error state is handled
    // ---------------------------------------------------------------
    @Test
    fun error_state_is_handled() {
        coEvery { repository.getInstances() } returns Result.failure(Exception("Network error"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("Network error"))
        assertTrue(viewModel.uiState.value.instances.isEmpty())
    }

    @Test
    fun auth_error_is_set_on_operation_failure() {
        coEvery { repository.getInstances() } returns Result.success(emptyList())
        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns
            Result.failure(Exception("认证失败"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.authError)
        assertTrue(viewModel.uiState.value.authError!!.contains("认证"))
        assertNull(viewModel.uiState.value.operationError)
    }

    @Test
    fun operation_error_is_set_on_repository_failure() {
        coEvery { repository.getInstances() } returns Result.success(emptyList())
        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.startInstance("inst-bad") } returns Result.failure(Exception("启动失败"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-bad")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.operationError)
        assertTrue(viewModel.uiState.value.operationError!!.contains("启动失败"))
        assertNull(viewModel.uiState.value.authError)
    }

    // ---------------------------------------------------------------
    // Test 15: loading state is handled
    // ---------------------------------------------------------------
    @Test
    fun loading_state_is_set_to_true_during_refresh() {
        coEvery { repository.getInstances() } coAnswers {
            delay(Long.MAX_VALUE)
            Result.success(emptyList())
        }
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loading_state_clears_on_error() {
        coEvery { repository.getInstances() } returns Result.failure(Exception("fail"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------
    // Edge case: clear selection
    // ---------------------------------------------------------------
    @Test
    fun clearSelection_resets_selection() {
        val instances = createInstances(1)
        coEvery { repository.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectInstance(instances.first())

        assertNotNull(viewModel.uiState.value.selectedInstance)

        viewModel.clearSelection()
        assertNull(viewModel.uiState.value.selectedInstance)
    }

    // ---------------------------------------------------------------
    // Edge case: clearAuthError and clearOperationError
    // ---------------------------------------------------------------
    @Test
    fun clearAuthError_resets_auth_error() {
        coEvery { repository.getInstances() } returns Result.success(emptyList())
        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns
            Result.failure(Exception("认证失败"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.authError)

        viewModel.clearAuthError()
        assertNull(viewModel.uiState.value.authError)
    }

    @Test
    fun clearOperationError_resets_operation_error() {
        coEvery { repository.getInstances() } returns Result.success(emptyList())
        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.startInstance(any()) } returns Result.failure(Exception("op fail"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.operationError)

        viewModel.clearOperationError()
        assertNull(viewModel.uiState.value.operationError)
    }
}

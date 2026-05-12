package com.jlucraft.console.viewmodel

import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.model.GenericPushEventData
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
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

    private lateinit var client: Libp2pClient
    private lateinit var authCoordinator: AuthCoordinator
    private lateinit var pushEvents: MutableSharedFlow<PushService.PushEvent>
    private lateinit var pushService: PushService

    private lateinit var viewModel: InstancesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        client = mockk()
        authCoordinator = mockk()

        pushEvents = MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        pushService = mockk()
        every { pushService.events } returns pushEvents

        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
        every { authCoordinator.clearAuth() } just runs

        coEvery { client.getInstances() } returns Result.success(emptyList())
        coEvery { client.startInstance(any()) } returns Result.success(Unit)
        coEvery { client.stopInstance(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() {
        viewModel = InstancesViewModel(client, authCoordinator, pushService)
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

    private fun makePushEvent(type: String) = PushService.PushEvent(
        type = type,
        data = GenericPushEventData(raw = "")
    )




    @Test
    fun initial_state_loads_instances() {
        val instances = createInstances(3)
        coEvery { client.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { client.getInstances() }
        assertEquals(3, viewModel.uiState.value.instances.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }




    @Test
    fun refresh_updates_instances_list() {
        coEvery { client.getInstances() } returns Result.success(createInstances(2))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.instances.size)

        coEvery { client.getInstances() } returns Result.success(createInstances(4))
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.getInstances() }
        assertEquals(4, viewModel.uiState.value.instances.size)
    }




    @Test
    fun push_event_instance_started_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { client.getInstances() }

        pushEvents.tryEmit(makePushEvent("instance_started"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.getInstances() }
    }




    @Test
    fun push_event_instance_stopped_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { client.getInstances() }

        pushEvents.tryEmit(makePushEvent("instance_stopped"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.getInstances() }
    }




    @Test
    fun push_event_instance_crash_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { client.getInstances() }

        pushEvents.tryEmit(makePushEvent("instance_crash"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.getInstances() }
    }




    @Test
    fun push_event_instance_created_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { client.getInstances() }

        pushEvents.tryEmit(makePushEvent("instance_created"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.getInstances() }
    }




    @Test
    fun push_event_instance_deleted_triggers_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { client.getInstances() }

        pushEvents.tryEmit(makePushEvent("instance_deleted"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { client.getInstances() }
    }




    @Test
    fun non_matching_push_event_does_not_refresh() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { client.getInstances() }

        pushEvents.tryEmit(makePushEvent("unrelated_event"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { client.getInstances() }
    }




    @Test
    fun start_instance_calls_client_correctly() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            authCoordinator.authenticateForOperation("start-instance", any(), "启动实例", "请验证身份以启动实例")
        }
        coVerify(exactly = 1) { client.startInstance("inst-1") }
    }




    @Test
    fun stop_instance_calls_client_correctly() {
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.stopInstance("inst-2")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            authCoordinator.authenticateForOperation("stop-instance", any(), "停止实例", "请验证身份以停止实例")
        }
        coVerify(exactly = 1) { client.stopInstance("inst-2") }
    }




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




    @Test
    fun selectAllInstances_caps_at_20() {
        val instances = createInstances(25)
        coEvery { client.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAllInstances()
        assertEquals(20, viewModel.uiState.value.selectedInstanceIds.size)
        assertEquals(instances.take(20).map { it.id }.toSet(), viewModel.uiState.value.selectedInstanceIds)
    }

    @Test
    fun selectAllInstances_selects_all_when_less_than_20() {
        val instances = createInstances(5)
        coEvery { client.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAllInstances()
        assertEquals(5, viewModel.uiState.value.selectedInstanceIds.size)
        assertEquals(instances.map { it.id }.toSet(), viewModel.uiState.value.selectedInstanceIds)
    }




    @Test
    fun error_state_is_handled() {
        coEvery { client.getInstances() } returns Result.failure(Exception("Network error"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("Network error"))
        assertTrue(viewModel.uiState.value.instances.isEmpty())
    }

    @Test
    fun auth_error_is_set_on_operation_failure() {
        coEvery { client.getInstances() } returns Result.success(emptyList())
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
    fun operation_error_is_set_on_client_failure() {
        coEvery { client.getInstances() } returns Result.success(emptyList())
        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { client.startInstance("inst-bad") } returns Result.failure(Exception("启动失败"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-bad")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.operationError)
        assertTrue(viewModel.uiState.value.operationError!!.contains("启动失败"))
        assertNull(viewModel.uiState.value.authError)
    }




    @Test
    fun loading_state_is_set_to_true_during_refresh() {
        coEvery { client.getInstances() } coAnswers {
            delay(Long.MAX_VALUE)
            Result.success(emptyList())
        }
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loading_state_clears_on_error() {
        coEvery { client.getInstances() } returns Result.failure(Exception("fail"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
    }




    @Test
    fun clearSelection_resets_selection() {
        val instances = createInstances(1)
        coEvery { client.getInstances() } returns Result.success(instances)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectInstance(instances.first())

        assertNotNull(viewModel.uiState.value.selectedInstance)

        viewModel.clearSelection()
        assertNull(viewModel.uiState.value.selectedInstance)
    }




    @Test
    fun clearAuthError_resets_auth_error() {
        coEvery { client.getInstances() } returns Result.success(emptyList())
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
        coEvery { client.getInstances() } returns Result.success(emptyList())
        coEvery { authCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { client.startInstance(any()) } returns Result.failure(Exception("op fail"))

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startInstance("inst-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.operationError)

        viewModel.clearOperationError()
        assertNull(viewModel.uiState.value.operationError)
    }
}

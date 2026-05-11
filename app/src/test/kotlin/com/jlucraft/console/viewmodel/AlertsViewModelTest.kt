package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.repository.NodeRepository
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
class AlertsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: NodeRepository
    private lateinit var mockAuthCoordinator: AuthCoordinator

    private val mockAlerts = listOf(
        Alert(
            id = "alert-1",
            alertType = "node_down",
            severity = "critical",
            message = "Node is down",
            target = "peer-1",
            createdAt = "2024-01-01T00:00:00Z"
        ),
        Alert(
            id = "alert-2",
            alertType = "cpu_spike",
            severity = "warning",
            message = "CPU spike detected",
            target = "peer-2",
            createdAt = "2024-01-01T00:01:00Z",
            acknowledgedAt = "2024-01-01T00:02:00Z"
        ),
        Alert(
            id = "alert-3",
            alertType = "disk_full",
            severity = "critical",
            message = "Disk is full",
            target = "peer-3",
            createdAt = "2024-01-01T00:02:00Z",
            resolvedAt = "2024-01-01T00:03:00Z"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk()
        mockAuthCoordinator = mockk(relaxed = true)
        coEvery { mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): AlertsViewModel = AlertsViewModel(mockRepository, mockAuthCoordinator)

    // ── List alerts ──

    @Test
    fun `loads alerts on init`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.success(mockAlerts)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.alerts.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `handles list alerts error`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.failure(RuntimeException("fetch failed"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("fetch failed", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.alerts.isEmpty())
    }

    // ── Acknowledge alert ──

    @Test
    fun `acknowledge alert succeeds and refreshes list`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.success(mockAlerts)
        val acknowledgedAlert = mockAlerts[0].copy(acknowledgedAt = "2024-01-01T00:05:00Z")
        coEvery { mockRepository.acknowledgeAlert("alert-1") } returns Result.success(acknowledgedAlert)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.acknowledgeAlert("alert-1")
        advanceUntilIdle()

        assertEquals("alert_acknowledged", viewModel.uiState.value.actionSuccess)
        assertNull(viewModel.uiState.value.actionError)
        coVerify(exactly = 1) { mockRepository.acknowledgeAlert("alert-1") }
        coVerify(exactly = 2) { mockRepository.listAlerts(severity = null, includeResolved = false) }
    }

    @Test
    fun `acknowledge alert failure sets action error`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.success(mockAlerts)
        coEvery { mockRepository.acknowledgeAlert("alert-1") } returns Result.failure(RuntimeException("ack failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.acknowledgeAlert("alert-1")
        advanceUntilIdle()

        assertEquals("ack failed", viewModel.uiState.value.actionError)
        assertNull(viewModel.uiState.value.actionSuccess)
    }

    // ── Resolve alert ──

    @Test
    fun `resolve alert succeeds and refreshes list`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.success(mockAlerts)
        val resolvedAlert = mockAlerts[0].copy(resolvedAt = "2024-01-01T00:05:00Z")
        coEvery { mockRepository.resolveAlert("alert-1") } returns Result.success(resolvedAlert)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.resolveAlert("alert-1")
        advanceUntilIdle()

        assertEquals("alert_resolved", viewModel.uiState.value.actionSuccess)
        assertNull(viewModel.uiState.value.actionError)
        coVerify(exactly = 1) { mockRepository.resolveAlert("alert-1") }
        coVerify(exactly = 2) { mockRepository.listAlerts(severity = null, includeResolved = false) }
    }

    @Test
    fun `resolve alert failure sets action error`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.success(mockAlerts)
        coEvery { mockRepository.resolveAlert("alert-1") } returns Result.failure(RuntimeException("resolve failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.resolveAlert("alert-1")
        advanceUntilIdle()

        assertEquals("resolve failed", viewModel.uiState.value.actionError)
        assertNull(viewModel.uiState.value.actionSuccess)
    }

    // ── Filtering ──

    @Test
    fun `severity filter triggers refresh`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.success(mockAlerts)
        coEvery { mockRepository.listAlerts(severity = "critical", includeResolved = false) } returns Result.success(listOf(mockAlerts[0]))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSeverityFilter("critical")
        advanceUntilIdle()

        assertEquals("critical", viewModel.uiState.value.severityFilter)
        coVerify(exactly = 1) { mockRepository.listAlerts(severity = "critical", includeResolved = false) }
    }

    // ── Clear action state ──

    @Test
    fun `clear action state resets action fields`() = runTest {
        coEvery { mockRepository.listAlerts(severity = null, includeResolved = false) } returns Result.success(mockAlerts)
        coEvery { mockRepository.resolveAlert("alert-1") } returns Result.success(mockAlerts[0])

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.resolveAlert("alert-1")
        advanceUntilIdle()
        viewModel.clearActionState()

        assertNull(viewModel.uiState.value.actionSuccess)
        assertNull(viewModel.uiState.value.actionError)
    }
}

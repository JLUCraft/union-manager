package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.repository.NodeRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: NodeRepository
    private lateinit var mockTeeAuth: TeeAuthManager
    private lateinit var mockAuthCoordinator: AuthCoordinator

    private val mockDevices = listOf(
        Device(
            pubkey = "pk-device-1",
            status = "active",
            platform = "Android 14",
            createdAt = "2024-01-01T00:00:00Z",
            lastSeenAt = "2024-01-02T00:00:00Z"
        ),
        Device(
            pubkey = "pk-device-2",
            status = "revoked",
            platform = "iOS 17",
            createdAt = "2024-01-01T00:00:00Z",
            revokedAt = "2024-01-03T00:00:00Z",
            revokedReason = "lost device",
            revokedBy = "pk-admin"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk()
        mockTeeAuth = mockk()
        mockAuthCoordinator = mockk()
        every { mockTeeAuth.getPublicKey() } returns "pk-device-1"
        every { mockAuthCoordinator.clearAuth() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): DeviceViewModel = DeviceViewModel(mockRepository, mockTeeAuth, mockAuthCoordinator)

    // ── Load devices ──

    @Test
    fun `loads devices on init`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.devices.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `handles load devices error`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.failure(RuntimeException("fetch failed"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("fetch failed", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.devices.isEmpty())
    }

    // ── Revoke device ──

    @Test
    fun `revoke device succeeds and refreshes list`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked", revokedAt = "2024-01-04T00:00:00Z", revokedReason = "test revoke")
        coEvery { mockRepository.revokeDevice("pk-device-1", "test reason", "pk-device-1") } returns Result.success(revokedDevice)
        coEvery { mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()

        assertEquals("设备已吊销", viewModel.uiState.value.revokeSuccess)
        coVerify(exactly = 1) { mockRepository.revokeDevice("pk-device-1", "test reason", "pk-device-1") }
        coVerify(exactly = 2) { mockRepository.listDevices() }
    }

    @Test
    fun `revoke device fails on auth failure`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        coEvery { mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.failure(RuntimeException("auth failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()

        assertEquals("auth failed", viewModel.uiState.value.revokeError)
        coVerify(exactly = 0) { mockRepository.revokeDevice(any(), any(), any()) }
    }

    @Test
    fun `revoke device fails on server error`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        coEvery { mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { mockRepository.revokeDevice("pk-device-1", "test reason", "pk-device-1") } returns Result.failure(RuntimeException("server error"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()

        assertEquals("server error", viewModel.uiState.value.revokeError)
        coVerify(exactly = 1) { mockAuthCoordinator.clearAuth() }
    }

    @Test
    fun `revoke device auth payload binds revoked_by matching repository call`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked")
        coEvery { mockRepository.revokeDevice("pk-device-1", "test reason", "pk-device-1") } returns Result.success(revokedDevice)

        // Capture the auth payload sent to AuthCoordinator
        val capturedPayloads = mutableListOf<JsonObject>()
        coEvery {
            mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any())
        } answers {
            capturedPayloads.add(arg<JsonObject>(1))
            Result.success(Unit)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()

        // Verify auth payload contains all three bound fields
        val payload = capturedPayloads.single()
        assertEquals("pk-device-1", payload["target_pubkey"]?.jsonPrimitive?.content)
        assertEquals("test reason", payload["reason"]?.jsonPrimitive?.content)
        assertEquals("pk-device-1", payload["revoked_by"]?.jsonPrimitive?.content)

        // Verify repository was called with the identical revokedBy
        coVerify(exactly = 1) { mockRepository.revokeDevice("pk-device-1", "test reason", "pk-device-1") }
    }

    // ── Emergency revoke ──

    @Test
    fun `emergency revoke device succeeds and refreshes list`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked")
        coEvery { mockRepository.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") } returns Result.success(revokedDevice)
        coEvery { mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any()) } returns Result.success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.emergencyRevokeDevice("pk-device-1", "emergency")
        advanceUntilIdle()

        assertEquals("设备已紧急吊销", viewModel.uiState.value.revokeSuccess)
        coVerify(exactly = 1) { mockRepository.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") }
    }

    @Test
    fun `emergency revoke device auth payload binds revoked_by matching repository call`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked")
        coEvery { mockRepository.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") } returns Result.success(revokedDevice)

        val capturedPayloads = mutableListOf<JsonObject>()
        coEvery {
            mockAuthCoordinator.authenticateForOperation(any(), any(), any(), any())
        } answers {
            capturedPayloads.add(arg<JsonObject>(1))
            Result.success(Unit)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.emergencyRevokeDevice("pk-device-1", "emergency")
        advanceUntilIdle()

        val payload = capturedPayloads.single()
        assertEquals("pk-device-1", payload["target_pubkey"]?.jsonPrimitive?.content)
        assertEquals("emergency", payload["reason"]?.jsonPrimitive?.content)
        assertEquals("pk-device-1", payload["revoked_by"]?.jsonPrimitive?.content)

        coVerify(exactly = 1) { mockRepository.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") }
    }

    // ── Get current pubkey ──

    @Test
    fun `get current pubkey returns tee auth public key`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("pk-device-1", viewModel.getCurrentPubkey())
        verify(exactly = 1) { mockTeeAuth.getPublicKey() }
    }

    // ── Clear revoke status ──

    @Test
    fun `clear revoke status resets status fields`() = runTest {
        coEvery { mockRepository.listDevices() } returns Result.success(mockDevices)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearRevokeStatus()

        assertNull(viewModel.uiState.value.revokeSuccess)
        assertNull(viewModel.uiState.value.revokeError)
    }
}

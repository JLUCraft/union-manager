package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.model.RevokeDevicePayload
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
class DeviceViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockClient: Libp2pClient
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
        mockClient = mockk()
        mockTeeAuth = mockk()
        mockAuthCoordinator = mockk()
        every { mockTeeAuth.getPublicKey() } returns "pk-device-1"
        every { mockTeeAuth.tryGetPublicKey() } returns Result.success("pk-device-1")
        every { mockAuthCoordinator.clearAuth() } just Runs
    }

    private fun mockDeviceKeyAuthSuccess() {
        coEvery {
            mockAuthCoordinator.authenticateForOperationWithDeviceKey(any(), any(), any(), any())
        } answers {
            val payloadFactory = arg<(String) -> com.jlucraft.console.data.model.AuthPayload>(1)
            payloadFactory("pk-device-1")
            Result.success("pk-device-1")
        }
    }

    private fun mockDeviceKeyAuthFailure(message: String) {
        coEvery {
            mockAuthCoordinator.authenticateForOperationWithDeviceKey(any(), any(), any(), any())
        } returns Result.failure(RuntimeException(message))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): DeviceViewModel = DeviceViewModel(mockClient, mockTeeAuth, mockAuthCoordinator)



    @Test
    fun `loads devices on init`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.devices.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `handles load devices error`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.failure(RuntimeException("fetch failed"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("fetch failed", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.devices.isEmpty())
    }



    @Test
    fun `revoke device succeeds and refreshes list`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked", revokedAt = "2024-01-04T00:00:00Z", revokedReason = "test revoke")
        coEvery { mockClient.revokeDevice("pk-device-1", "test reason", "pk-device-1") } returns Result.success(revokedDevice)
        mockDeviceKeyAuthSuccess()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()

        assertEquals("设备已吊销", viewModel.uiState.value.revokeSuccess)
        coVerify(exactly = 1) { mockClient.revokeDevice("pk-device-1", "test reason", "pk-device-1") }
        coVerify(exactly = 2) { mockClient.listDevices() }
    }

    @Test
    fun `revoke device fails on auth failure`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        mockDeviceKeyAuthFailure("auth failed")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()

        assertEquals("auth failed", viewModel.uiState.value.revokeError)
        coVerify(exactly = 0) { mockClient.revokeDevice(any(), any(), any()) }
    }

    @Test
    fun `revoke device fails on server error`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        mockDeviceKeyAuthSuccess()
        coEvery { mockClient.revokeDevice("pk-device-1", "test reason", "pk-device-1") } returns Result.failure(RuntimeException("server error"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()

        assertEquals("server error", viewModel.uiState.value.revokeError)
        coVerify(exactly = 1) { mockAuthCoordinator.clearAuth() }
    }

    @Test
    fun `revoke device auth payload binds revoked_by matching client call`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked")
        coEvery { mockClient.revokeDevice("pk-device-1", "test reason", "pk-device-1") } returns Result.success(revokedDevice)


        val capturedPayloads = mutableListOf<com.jlucraft.console.data.model.AuthPayload>()
        coEvery {
            mockAuthCoordinator.authenticateForOperationWithDeviceKey(any(), any(), any(), any())
        } answers {
            val payloadFactory = arg<(String) -> com.jlucraft.console.data.model.AuthPayload>(1)
            capturedPayloads.add(payloadFactory("pk-device-1"))
            Result.success("pk-device-1")
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeDevice("pk-device-1", "test reason")
        advanceUntilIdle()


        val payload = capturedPayloads.single() as RevokeDevicePayload
        assertEquals("pk-device-1", payload.targetPubkey)
        assertEquals("test reason", payload.reason)
        assertEquals("pk-device-1", payload.revokedBy)


        coVerify(exactly = 1) { mockClient.revokeDevice("pk-device-1", "test reason", "pk-device-1") }
    }



    @Test
    fun `emergency revoke device succeeds and refreshes list`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked")
        coEvery { mockClient.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") } returns Result.success(revokedDevice)
        mockDeviceKeyAuthSuccess()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.emergencyRevokeDevice("pk-device-1", "emergency")
        advanceUntilIdle()

        assertEquals("设备已紧急吊销", viewModel.uiState.value.revokeSuccess)
        coVerify(exactly = 1) { mockClient.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") }
    }

    @Test
    fun `emergency revoke device auth payload binds revoked_by matching client call`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        val revokedDevice = mockDevices[0].copy(status = "revoked")
        coEvery { mockClient.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") } returns Result.success(revokedDevice)

        val capturedPayloads = mutableListOf<com.jlucraft.console.data.model.AuthPayload>()
        coEvery {
            mockAuthCoordinator.authenticateForOperationWithDeviceKey(any(), any(), any(), any())
        } answers {
            val payloadFactory = arg<(String) -> com.jlucraft.console.data.model.AuthPayload>(1)
            capturedPayloads.add(payloadFactory("pk-device-1"))
            Result.success("pk-device-1")
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.emergencyRevokeDevice("pk-device-1", "emergency")
        advanceUntilIdle()

        val payload = capturedPayloads.single() as com.jlucraft.console.data.model.EmergencyRevokeDevicePayload
        assertEquals("pk-device-1", payload.targetPubkey)
        assertEquals("emergency", payload.reason)
        assertEquals("pk-device-1", payload.revokedBy)

        coVerify(exactly = 1) { mockClient.emergencyRevokeDevice("pk-device-1", "emergency", "pk-device-1") }
    }



    @Test
    fun `get current pubkey returns tee auth public key`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("pk-device-1", viewModel.getCurrentPubkey())
        verify(exactly = 1) { mockTeeAuth.tryGetPublicKey() }
    }



    @Test
    fun `clear revoke status resets status fields`() = runTest {
        coEvery { mockClient.listDevices() } returns Result.success(mockDevices)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearRevokeStatus()

        assertNull(viewModel.uiState.value.revokeSuccess)
        assertNull(viewModel.uiState.value.revokeError)
    }
}

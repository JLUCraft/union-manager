package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.auth.BiometricResult
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.remote.UnionPushReceiver
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.di.ServiceLocator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsStore: SettingsStore
    private lateinit var teeAuth: TeeAuthManager
    private lateinit var biometric: BiometricAuthManager
    private lateinit var nodeRepository: NodeRepository
    private lateinit var authCoordinator: AuthCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        settingsStore = mockk(relaxed = true)
        teeAuth = mockk(relaxed = true)
        biometric = mockk(relaxed = true)
        nodeRepository = mockk(relaxed = true)
        authCoordinator = mockk(relaxed = true)

        mockkObject(ServiceLocator)
        every { ServiceLocator.nodeRepository } returns nodeRepository
        every { ServiceLocator.authCoordinator } returns authCoordinator
        coEvery { ServiceLocator.setServerUrl(any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(settingsStore, teeAuth, biometric)

    private fun createDevice(
        pubkey: String = "pk-test",
        status: String = "active",
        platform: String = "android",
        createdAt: String = "2025-01-01T00:00:00Z",
        lastSeenAt: String? = "2025-01-15T12:00:00Z",
        revokedAt: String? = null,
        revokedReason: String? = null,
        revokedBy: String? = null
    ): Device = Device(
        pubkey = pubkey,
        status = status,
        platform = platform,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        revokedAt = revokedAt,
        revokedReason = revokedReason,
        revokedBy = revokedBy
    )

    // ─── 1. init reads server URL from settings store ──────────────

    @Test
    fun `init reads server URL from settings store`() {
        every { settingsStore.currentServerUrl } returns "http://custom:3000"

        val vm = createViewModel()

        assertEquals("http://custom:3000", vm.uiState.value.serverUrl)
    }

    @Test
    fun `init uses default server URL when settings store returns default`() {
        every { settingsStore.currentServerUrl } returns SettingsStore.DEFAULT_SERVER_URL

        val vm = createViewModel()

        assertEquals(SettingsStore.DEFAULT_SERVER_URL, vm.uiState.value.serverUrl)
    }

    // ─── 2. init reads TEE key status ──────────────────────────────

    @Test
    fun `init reads TEE key status when key exists`() {
        every { teeAuth.hasKey() } returns true

        val vm = createViewModel()

        assertTrue(vm.uiState.value.hasTeeKey)
    }

    @Test
    fun `init reads TEE key status when key does not exist`() {
        every { teeAuth.hasKey() } returns false

        val vm = createViewModel()

        assertFalse(vm.uiState.value.hasTeeKey)
    }

    // ─── 3. setServerUrl updates settings store and service locator ─

    @Test
    fun `setServerUrl updates settings store and service locator`() {
        val oldUrl = "http://old:8080"
        val newUrl = "http://new:9090"

        every { settingsStore.currentServerUrl } returns oldUrl andThen newUrl
        coEvery { ServiceLocator.setServerUrl(any()) } returns Unit

        val vm = createViewModel()
        assertEquals(oldUrl, vm.uiState.value.serverUrl)

        vm.setServerUrl(newUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { ServiceLocator.setServerUrl(newUrl) }
        assertEquals(newUrl, vm.uiState.value.serverUrl)
    }

    // ─── 4. testBiometric on success shows "认证成功" ──────────────

    @Test
    fun `testBiometric on success shows success message`() {
        coEvery { biometric.authenticate(any(), any()) } returns BiometricResult.Success

        val vm = createViewModel()
        vm.testBiometric()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("认证成功", vm.uiState.value.biometricResult)
    }

    // ─── 5. testBiometric on failure shows "认证失败" ──────────────

    @Test
    fun `testBiometric on failure shows failure message`() {
        coEvery { biometric.authenticate(any(), any()) } returns BiometricResult.Failed

        val vm = createViewModel()
        vm.testBiometric()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("认证失败", vm.uiState.value.biometricResult)
    }

    // ─── 6. testBiometric on error shows error message ─────────────

    @Test
    fun `testBiometric on error shows error message`() {
        coEvery { biometric.authenticate(any(), any()) } returns BiometricResult.Error("指纹硬件不可用")

        val vm = createViewModel()
        vm.testBiometric()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("错误: 指纹硬件不可用", vm.uiState.value.biometricResult)
    }

    // ─── 7. loadDevices populates device list ──────────────────────

    @Test
    fun `loadDevices populates device list`() {
        val device1 = createDevice("pk-abc", "active")
        val device2 = createDevice("pk-def", "revoked")
        coEvery { nodeRepository.listDevices() } returns Result.success(listOf(device1, device2))

        val vm = createViewModel()
        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertNull(vm.uiState.value.devicesError)
        assertEquals(2, vm.uiState.value.devices.size)
        assertEquals("pk-abc", vm.uiState.value.devices[0].pubkey)
        assertEquals("active", vm.uiState.value.devices[0].status)
        assertEquals("pk-def", vm.uiState.value.devices[1].pubkey)
        assertEquals("revoked", vm.uiState.value.devices[1].status)
    }

    @Test
    fun `loadDevices populates empty device list`() {
        coEvery { nodeRepository.listDevices() } returns Result.success(emptyList())

        val vm = createViewModel()
        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertTrue(vm.uiState.value.devices.isEmpty())
        assertNull(vm.uiState.value.devicesError)
    }

    // ─── 8. loadDevices handles error ──────────────────────────────

    @Test
    fun `loadDevices handles error`() {
        coEvery { nodeRepository.listDevices() } returns Result.failure(RuntimeException("网络连接失败"))

        val vm = createViewModel()
        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertEquals("网络连接失败", vm.uiState.value.devicesError)
        assertTrue(vm.uiState.value.devices.isEmpty())
    }

    @Test
    fun `loadDevices clears previous error on success`() {
        val device1 = createDevice("pk-1")
        coEvery { nodeRepository.listDevices() } returns Result.success(listOf(device1))

        val vm = createViewModel()
        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertNull(vm.uiState.value.devicesError)
        assertEquals(1, vm.uiState.value.devices.size)
    }

    @Test
    fun `loadDevices clears device list on error`() {
        coEvery { nodeRepository.listDevices() } returns Result.success(listOf(createDevice("pk-1"))) andThen Result.failure(RuntimeException("连接中断"))

        val vm = createViewModel()
        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.devices.size)

        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.devices.size)
        assertEquals("连接中断", vm.uiState.value.devicesError)
    }

    // ─── 9. clearBiometricResult clears the result ─────────────────

    @Test
    fun `clearBiometricResult clears the result`() {
        coEvery { biometric.authenticate(any(), any()) } returns BiometricResult.Success

        val vm = createViewModel()
        vm.testBiometric()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("认证成功", vm.uiState.value.biometricResult)

        vm.clearBiometricResult()

        assertNull(vm.uiState.value.biometricResult)
    }

    // ─── 10. distributor info flow updates state ───────────────────

    @Test
    fun `distributor info flow updates state for embedded distributor`() {
        UnionPushReceiver.setDistributorInfo("Embedded FCM (内置)")

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Embedded FCM (内置)", vm.uiState.value.distributorInfo)
        assertEquals("FCM", vm.uiState.value.distributorBadge)
    }

    @Test
    fun `distributor info flow updates state for external distributor`() {
        UnionPushReceiver.setDistributorInfo("External Distributor")

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("External Distributor", vm.uiState.value.distributorInfo)
        assertEquals("外部", vm.uiState.value.distributorBadge)
    }

    @Test
    fun `distributor info flow updates badge after distributor changes`() {
        UnionPushReceiver.setDistributorInfo("Embedded FCM (内置)")

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("FCM", vm.uiState.value.distributorBadge)

        UnionPushReceiver.setDistributorInfo("ntfy")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("ntfy", vm.uiState.value.distributorInfo)
        assertEquals("外部", vm.uiState.value.distributorBadge)
    }

    // ─── revokeDevice ──────────────────────────────────────────────

    @Test
    fun `revokeDevice successfully revokes a device`() {
        val revokedDevice = createDevice("pk-revoke", "revoked")

        coEvery {
            authCoordinator.authenticateForOperation(any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery { nodeRepository.revokeDevice(any(), any(), any()) } returns Result.success(revokedDevice)
        coEvery { nodeRepository.listDevices() } returns Result.success(listOf(revokedDevice))
        every { authCoordinator.clearAuth() } just runs

        val vm = createViewModel()
        vm.revokeDevice("pk-revoke", "测试吊销原因")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("设备已吊销", vm.uiState.value.revokeSuccess)
        assertEquals("revoked", vm.uiState.value.devices.single().status)
        coVerify { nodeRepository.revokeDevice("pk-revoke", "测试吊销原因", any()) }
        coVerify { authCoordinator.clearAuth() }
    }

    @Test
    fun `revokeDevice fails when authentication fails`() {
        coEvery {
            authCoordinator.authenticateForOperation(any(), any(), any(), any())
        } returns Result.failure(Exception("认证被取消"))

        val vm = createViewModel()
        vm.revokeDevice("pk-revoke", "reason")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertEquals("认证被取消", vm.uiState.value.devicesError)
        assertNull(vm.uiState.value.revokeSuccess)
    }

    @Test
    fun `revokeDevice fails when revoke API fails`() {
        coEvery {
            authCoordinator.authenticateForOperation(any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery {
            nodeRepository.revokeDevice(any(), any(), any())
        } returns Result.failure(Exception("服务器内部错误"))
        every { authCoordinator.clearAuth() } just runs

        val vm = createViewModel()
        vm.revokeDevice("pk-revoke", "reason")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertEquals("服务器内部错误", vm.uiState.value.devicesError)
        coVerify { authCoordinator.clearAuth() }
    }

    // ─── emergencyRevokeDevice ─────────────────────────────────────

    @Test
    fun `emergencyRevokeDevice successfully revokes a device`() {
        val device = createDevice("pk-emergency", "revoked")

        coEvery {
            authCoordinator.authenticateForOperation(any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery {
            nodeRepository.emergencyRevokeDevice(any(), any(), any())
        } returns Result.success(device)
        coEvery { nodeRepository.listDevices() } returns Result.success(listOf(device))
        every { authCoordinator.clearAuth() } just runs

        val vm = createViewModel()
        vm.emergencyRevokeDevice("pk-emergency", "紧急吊销")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("设备已紧急吊销", vm.uiState.value.revokeSuccess)
        coVerify { nodeRepository.emergencyRevokeDevice("pk-emergency", "紧急吊销", any()) }
        coVerify { authCoordinator.clearAuth() }
    }

    @Test
    fun `emergencyRevokeDevice fails when auth fails`() {
        coEvery {
            authCoordinator.authenticateForOperation(any(), any(), any(), any())
        } returns Result.failure(Exception("认证超时"))

        val vm = createViewModel()
        vm.emergencyRevokeDevice("pk-any", "reason")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertEquals("认证超时", vm.uiState.value.devicesError)
        assertNull(vm.uiState.value.revokeSuccess)
    }

    @Test
    fun `emergencyRevokeDevice fails when revoke API fails`() {
        coEvery {
            authCoordinator.authenticateForOperation(any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery {
            nodeRepository.emergencyRevokeDevice(any(), any(), any())
        } returns Result.failure(Exception("服务不可用"))
        every { authCoordinator.clearAuth() } just runs

        val vm = createViewModel()
        vm.emergencyRevokeDevice("pk-any", "reason")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertEquals("服务不可用", vm.uiState.value.devicesError)
        coVerify { authCoordinator.clearAuth() }
    }

    // ─── clearRevokeSuccess ────────────────────────────────────────

    @Test
    fun `clearRevokeSuccess clears revoke status`() {
        val device = createDevice("pk-clear", "revoked")

        coEvery {
            authCoordinator.authenticateForOperation(any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery { nodeRepository.revokeDevice(any(), any(), any()) } returns Result.success(device)
        coEvery { nodeRepository.listDevices() } returns Result.success(listOf(device))
        every { authCoordinator.clearAuth() } just runs

        val vm = createViewModel()
        vm.revokeDevice("pk-clear", "reason")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("设备已吊销", vm.uiState.value.revokeSuccess)

        vm.clearRevokeSuccess()
        assertNull(vm.uiState.value.revokeSuccess)
    }

    @Test
    fun `clearRevokeSuccess is no-op when revokeSuccess is null`() {
        val vm = createViewModel()

        assertNull(vm.uiState.value.revokeSuccess)
        vm.clearRevokeSuccess()
        assertNull(vm.uiState.value.revokeSuccess)
    }

    // ─── state initial defaults ────────────────────────────────────

    @Test
    fun `initial state has correct default values`() {
        every { settingsStore.currentServerUrl } returns SettingsStore.DEFAULT_SERVER_URL
        every { teeAuth.hasKey() } returns false

        val vm = createViewModel()

        assertEquals("检测中...", vm.uiState.value.distributorInfo)
        assertEquals("", vm.uiState.value.distributorBadge)
        assertNull(vm.uiState.value.biometricResult)
        assertTrue(vm.uiState.value.devices.isEmpty())
        assertFalse(vm.uiState.value.devicesLoading)
        assertNull(vm.uiState.value.devicesError)
        assertNull(vm.uiState.value.revokeSuccess)
    }
}

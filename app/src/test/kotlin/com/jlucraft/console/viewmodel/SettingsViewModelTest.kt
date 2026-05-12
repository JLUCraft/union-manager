package com.jlucraft.console.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.auth.BiometricResult
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.TeeCapability
import com.jlucraft.console.data.auth.AuthStateHolder
import com.jlucraft.console.data.auth.ReadOnlyMode
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import com.jlucraft.console.data.remote.PushPreferencesResponse
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
    private lateinit var client: Libp2pClient
    private lateinit var nodeRepository: NodeRepository
    private lateinit var authCoordinator: AuthCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        settingsStore = mockk(relaxed = true)
        teeAuth = mockk(relaxed = true)
        biometric = mockk(relaxed = true)
        client = mockk(relaxed = true)
        nodeRepository = mockk(relaxed = true)
        authCoordinator = mockk(relaxed = true)
        every { teeAuth.capability } returns TeeCapability.TeeOnlyAvailable
        every { teeAuth.hasKey() } returns false
        every { teeAuth.tryGetPublicKey() } returns Result.success("pk-actor")
        coEvery { nodeRepository.getPushPreferences() } returns Result.success(PushPreferencesResponse())
        coEvery { nodeRepository.updatePushPreferences(any(), any(), any(), any()) } returns Result.success(PushPreferencesResponse())

        AuthStateHolder.resetReadOnlyMode()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        AuthStateHolder.resetReadOnlyMode()
    }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            settingsStore,
            teeAuth,
            biometric,
            client,
            nodeRepository,
            authCoordinator,
        )

    private fun mockDeviceKeyAuthSuccess() {
        coEvery {
            authCoordinator.authenticateForOperationWithDeviceKey(any(), any(), any(), any())
        } answers {
            val payloadFactory = arg<(String) -> com.jlucraft.console.data.model.AuthPayload>(1)
            payloadFactory("pk-actor")
            Result.success("pk-actor")
        }
    }

    private fun mockDeviceKeyAuthFailure(message: String) {
        coEvery {
            authCoordinator.authenticateForOperationWithDeviceKey(any(), any(), any(), any())
        } returns Result.failure(Exception(message))
    }

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



    @Test
    fun `testBiometric on success shows success message`() {
        coEvery { biometric.authenticate(any(), any()) } returns BiometricResult.Success

        val vm = createViewModel()
        vm.testBiometric()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("认证成功", vm.uiState.value.biometricResult)
    }



    @Test
    fun `testBiometric on failure shows failure message`() {
        coEvery { biometric.authenticate(any(), any()) } returns BiometricResult.Failed

        val vm = createViewModel()
        vm.testBiometric()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("认证失败", vm.uiState.value.biometricResult)
    }



    @Test
    fun `testBiometric on error shows error message`() {
        coEvery { biometric.authenticate(any(), any()) } returns BiometricResult.Error("指纹硬件不可用")

        val vm = createViewModel()
        vm.testBiometric()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("错误: 指纹硬件不可用", vm.uiState.value.biometricResult)
    }



    @Test
    fun `loadDevices populates device list`() {
        val device1 = createDevice("pk-abc", "active")
        val device2 = createDevice("pk-def", "revoked")
        coEvery { client.listDevices() } returns Result.success(listOf(device1, device2))

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
        coEvery { client.listDevices() } returns Result.success(emptyList())

        val vm = createViewModel()
        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertTrue(vm.uiState.value.devices.isEmpty())
        assertNull(vm.uiState.value.devicesError)
    }



    @Test
    fun `loadDevices handles error`() {
        coEvery { client.listDevices() } returns Result.failure(RuntimeException("网络连接失败"))

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
        coEvery { client.listDevices() } returns Result.success(listOf(device1))

        val vm = createViewModel()
        vm.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertNull(vm.uiState.value.devicesError)
        assertEquals(1, vm.uiState.value.devices.size)
    }



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



    @Test
    fun `revokeDevice successfully revokes a device`() {
        val revokedDevice = createDevice("pk-revoke", "revoked")
        mockDeviceKeyAuthSuccess()
        every { authCoordinator.clearAuth() } just runs
        coEvery { client.revokeDevice("pk-revoke", any(), "pk-actor") } returns Result.success(revokedDevice)
        coEvery { client.listDevices() } returns Result.success(listOf(revokedDevice))

        val vm = createViewModel()
        vm.revokeDevice("pk-revoke", "测试吊销原因")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("设备已吊销", vm.uiState.value.revokeSuccess)
        assertEquals("revoked", vm.uiState.value.devices.single().status)
    }

    @Test
    fun `revokeDevice fails when authentication fails`() {
        mockDeviceKeyAuthFailure("认证被取消")

        val vm = createViewModel()
        vm.revokeDevice("pk-revoke", "reason")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertEquals("认证被取消", vm.uiState.value.devicesError)
        assertNull(vm.uiState.value.revokeSuccess)
    }



    @Test
    fun `emergencyRevokeDevice successfully revokes a device`() {
        val device = createDevice("pk-emergency", "revoked")
        mockDeviceKeyAuthSuccess()
        every { authCoordinator.clearAuth() } just runs
        coEvery { client.emergencyRevokeDevice("pk-emergency", any(), "pk-actor") } returns Result.success(device)
        coEvery { client.listDevices() } returns Result.success(listOf(device))

        val vm = createViewModel()
        vm.emergencyRevokeDevice("pk-emergency", "紧急吊销")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("设备已紧急吊销", vm.uiState.value.revokeSuccess)
    }

    @Test
    fun `emergencyRevokeDevice fails when auth fails`() {
        mockDeviceKeyAuthFailure("认证超时")

        val vm = createViewModel()
        vm.emergencyRevokeDevice("pk-any", "reason")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.devicesLoading)
        assertEquals("认证超时", vm.uiState.value.devicesError)
        assertNull(vm.uiState.value.revokeSuccess)
    }



    @Test
    fun `clearRevokeSuccess clears revoke status`() {
        val device = createDevice("pk-clear", "revoked")
        mockDeviceKeyAuthSuccess()
        every { authCoordinator.clearAuth() } just runs
        coEvery { client.revokeDevice("pk-clear", any(), "pk-actor") } returns Result.success(device)
        coEvery { client.listDevices() } returns Result.success(listOf(device))

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



    @Test
    fun `initial state has correct default values`() {
        every { teeAuth.hasKey() } returns false

        val vm = createViewModel()

        assertNull(vm.uiState.value.biometricResult)
        assertTrue(vm.uiState.value.devices.isEmpty())
        assertFalse(vm.uiState.value.devicesLoading)
        assertNull(vm.uiState.value.devicesError)
        assertNull(vm.uiState.value.revokeSuccess)
    }
}

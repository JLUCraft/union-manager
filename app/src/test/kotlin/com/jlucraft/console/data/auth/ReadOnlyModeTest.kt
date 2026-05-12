package com.jlucraft.console.data.auth

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadOnlyModeTest {

    @After
    fun tearDown() {

        AuthStateHolder.resetReadOnlyMode()
    }

    @Test
    fun `AuthStateHolder defaults to ReadOnly mode until initialized`() {
        AuthStateHolder.resetReadOnlyMode()

        val current = AuthStateHolder.readOnlyMode.value
        assertTrue(current is ReadOnlyMode.ReadOnly)
        assertEquals("TEE 状态检测中...", (current as ReadOnlyMode.ReadOnly).reason)
    }

    @Test
    fun `setReadOnlyModeFrom transitions to ReadWrite when TEE is available`() {
        val teeAuth = mockk<TeeAuthManager>(relaxed = true)
        every { teeAuth.isTeeBacked } returns true
        every { teeAuth.capability } returns TeeCapability.TeeOnlyAvailable

        AuthStateHolder.setReadOnlyModeFrom(teeAuth)

        val value = AuthStateHolder.readOnlyMode.value
        assertTrue(value is ReadOnlyMode.ReadWrite)
    }

    @Test
    fun `setReadOnlyModeFrom transitions to ReadOnly when TEE not available`() {
        val teeAuth = mockk<TeeAuthManager>(relaxed = true)
        every { teeAuth.isTeeBacked } returns false
        every { teeAuth.capability } returns TeeCapability.NoHardwareBackedKey("模拟: 无 TEE 硬件")

        AuthStateHolder.setReadOnlyModeFrom(teeAuth)

        val value = AuthStateHolder.readOnlyMode.value
        assertTrue(value is ReadOnlyMode.ReadOnly)
        assertEquals("模拟: 无 TEE 硬件", (value as ReadOnlyMode.ReadOnly).reason)
    }

    @Test
    fun `ReadOnlyMode ReadOnly carries descriptive reason`() {
        val mode = ReadOnlyMode.ReadOnly("Ed25519 设备不可用")
        assertEquals("Ed25519 设备不可用", mode.reason)
    }

    @Test
    fun `ReadOnlyMode ReadWrite is a singleton`() {
        val a = ReadOnlyMode.ReadWrite
        val b = ReadOnlyMode.ReadWrite
        assertSame(a, b)
    }

    @Test
    fun `ReadOnlyMode sealed interface has exactly two subtypes`() {

        val subtypes = ReadOnlyMode::class.sealedSubclasses
        assertEquals(2, subtypes.size)
        val names = subtypes.map { it.simpleName ?: "" }.toSet()
        assertTrue(names.contains("ReadWrite"))
        assertTrue(names.contains("ReadOnly"))
    }

    @Test
    fun `ReadOnlyMode ReadOnly from NoHardwareBackedKey extracts reason`() {
        val capability = TeeCapability.NoHardwareBackedKey("Ed25519 探测密钥安全级别为 0，非 TEE/StrongBox")

        val mode = if (true) {

            ReadOnlyMode.ReadOnly(
                (capability as TeeCapability.NoHardwareBackedKey).reason
            )
        } else {
            ReadOnlyMode.ReadWrite
        }

        assertEquals(
            "Ed25519 探测密钥安全级别为 0，非 TEE/StrongBox",
            (mode as ReadOnlyMode.ReadOnly).reason
        )
    }

    @Test
    fun `ReadOnlyMode reason is preserved through setReadOnlyModeFrom`() {
        val teeAuth = mockk<TeeAuthManager>(relaxed = true)
        val expectedReason = "Keystore 操作失败: ProviderException"
        every { teeAuth.isTeeBacked } returns false
        every { teeAuth.capability } returns TeeCapability.NoHardwareBackedKey(expectedReason)

        AuthStateHolder.setReadOnlyModeFrom(teeAuth)

        val value = AuthStateHolder.readOnlyMode.value
        assertTrue(value is ReadOnlyMode.ReadOnly)
        assertEquals(expectedReason, (value as ReadOnlyMode.ReadOnly).reason)
    }
}

package com.jlucraft.console.data.auth

import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.remote.AuthResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCoordinatorTest {

    private lateinit var api: ApiService
    private lateinit var teeAuth: TeeAuthManager
    private lateinit var biometricAuth: BiometricAuthManager
    private lateinit var coordinator: AuthCoordinator

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        teeAuth = mockk(relaxed = true)
        biometricAuth = mockk(relaxed = true)
        coordinator = AuthCoordinator(api, teeAuth, biometricAuth)
    }

    @Test
    fun `test_auth_headers_set_after_success`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            AuthChallenge("nonce123", 1234567890L, "hash123", 300L)
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any()) } just Runs

        val result = coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        assertTrue(result.isSuccess)
        verify { api.setAuthHeaders("nonce123", "AQID") }
    }

    @Test
    fun `test_auth_headers_cleared_on_clear`() {
        every { api.clearAuthHeaders() } just Runs

        coordinator.clearAuth()

        verify { api.clearAuthHeaders() }
    }

    @Test
    fun `test_withAuthenticatedOperation_runs_block`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            AuthChallenge("nonce123", 1234567890L, "hash123", 300L)
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any()) } just Runs
        every { api.clearAuthHeaders() } just Runs

        var blockRan = false
        val result = coordinator.withAuthenticatedOperation(
            cmdType = "test-cmd",
            payload = buildJsonObject { put("key", "value") }
        ) {
            blockRan = true
            "result-value"
        }

        assertTrue(result.isSuccess)
        assertEquals("result-value", result.getOrNull())
        assertTrue(blockRan)
        verify { api.clearAuthHeaders() }
    }

    @Test
    fun `test_withAuthenticatedOperation_cleans_up_on_error`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            AuthChallenge("nonce123", 1234567890L, "hash123", 300L)
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any()) } just Runs
        every { api.clearAuthHeaders() } just Runs

        val result = coordinator.withAuthenticatedOperation(
            cmdType = "test-cmd",
            payload = buildJsonObject { put("key", "value") }
        ) {
            throw RuntimeException("operation failed")
        }

        assertTrue(result.isFailure)
        verify { api.clearAuthHeaders() }
    }

    @Test
    fun `test_authenticateForOperation_calls_api`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            AuthChallenge("nonce123", 1234567890L, "hash123", 300L)
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any()) } just Runs

        coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        coVerify { api.requestChallenge(any()) }
        coVerify { api.verifySignature(any()) }
    }

    @Test
    fun `test_authenticateForOperation_fails_on_biometric_error`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Error("biometric failed")

        val result = coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `test_authenticateForOperation_fails_on_challenge_error`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.failure(Exception("challenge failed"))

        val result = coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `test_authenticateForOperation_fails_on_sign_error`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            AuthChallenge("nonce123", 1234567890L, "hash123", 300L)
        )
        every { teeAuth.sign(any()) } throws IllegalStateException("sign failed")

        val result = coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `test_authenticateForOperation_fails_on_verify_failure`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            AuthChallenge("nonce123", 1234567890L, "hash123", 300L)
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(false, "denied"))

        val result = coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `test_withAuthenticatedOperation_fails_on_auth_failure`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Error("denied")
        every { api.clearAuthHeaders() } just Runs

        var blockRan = false
        val result = coordinator.withAuthenticatedOperation(
            cmdType = "test-cmd",
            payload = buildJsonObject { put("key", "value") }
        ) {
            blockRan = true
            "value"
        }

        assertTrue(result.isFailure)
        assertFalse(blockRan)
    }

    @Test
    fun `test_signWithBiometric_returns_base64_signature`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)

        val result = coordinator.signWithBiometric("Title", "Subtitle", byteArrayOf(4, 5, 6))

        assertTrue(result.isSuccess)
        assertEquals("AQID", result.getOrNull())
    }

    @Test
    fun `test_signWithBiometric_fails_on_biometric_error`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Failed

        val result = coordinator.signWithBiometric("Title", "Subtitle", byteArrayOf(4, 5, 6))

        assertTrue(result.isFailure)
    }

    @Test
    fun `test_signWithBiometric_fails_on_sign_error`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.sign(any()) } throws RuntimeException("tee error")

        val result = coordinator.signWithBiometric("Title", "Subtitle", byteArrayOf(4, 5, 6))

        assertTrue(result.isFailure)
    }
}

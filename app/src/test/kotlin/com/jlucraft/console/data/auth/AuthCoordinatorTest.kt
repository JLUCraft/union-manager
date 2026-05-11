package com.jlucraft.console.data.auth

import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.remote.AuthResult
import com.jlucraft.console.data.remote.SignResponse
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
        every { teeAuth.isTeeBacked } returns true
        coordinator = AuthCoordinator(api, teeAuth, biometricAuth)
    }

    private fun challenge(
        nonce: String = "nonce123",
        challengeId: String = "challenge-123",
        payloadHash: String = "hash123",
        cmdType: String = "test-cmd",
        expiresAt: String = "2099-01-01T00:05:00Z"
    ) = AuthChallenge(
        challenge_id = challengeId,
        nonce = nonce,
        issued_at = "2099-01-01T00:00:00Z",
        expires_at = expiresAt,
        ttl_seconds = 300L,
        cmd_type = cmdType,
        payload_hash = payloadHash,
        human_summary = "$cmdType operation",
        risk_level = "low",
        required_role = "admin"
    )

    @Test
    fun `test_auth_headers_set_after_success`() = runTest {
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "test-pubkey"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            challenge()
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any(), any()) } just Runs

        val result = coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        assertTrue(result.isSuccess)
        verify { api.setAuthHeaders("nonce123", "AQID", "challenge-123") }
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
            challenge()
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any(), any()) } just Runs
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
            challenge()
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any(), any()) } just Runs
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
            challenge()
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any(), any()) } just Runs

        coordinator.authenticateForOperation(
            "test-cmd",
            buildJsonObject { put("key", "value") }
        )

        coVerify { api.requestChallenge(any()) }
        coVerify { api.verifySignature(any()) }
    }

    @Test
    fun `test_authenticateForOperation_sends_subject_did_and_canonical_challenge_fields`() = runTest {
        every { teeAuth.tryGetPublicKey() } returns Result.success("device-public-key")
        coordinator.registerLocalIdentity("did:key:zAlice", "admin", "Alice")
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "device-public-key"
        coEvery {
            api.requestChallenge(any())
        } returns Result.success(
            challenge(
                nonce = "nonce-42",
                challengeId = "challenge-42",
                payloadHash = "payload-42",
                cmdType = "stop-instance"
            ).copy(risk_level = "medium", required_role = "admin")
        )
        every { teeAuth.sign(any()) } returns byteArrayOf(1, 2, 3)
        coEvery { api.verifySignature(any()) } returns Result.success(AuthResult(true, "ok"))
        every { api.setAuthHeaders(any(), any(), any()) } just Runs

        val result = coordinator.authenticateForOperation(
            "stop-instance",
            buildJsonObject { put("instance_id", "inst-1") }
        )

        assertTrue(result.isSuccess)
        coVerify {
            api.verifySignature(withArg<SignResponse> { response ->
                assertEquals("challenge-42", response.challenge_id)
                assertEquals("nonce-42", response.nonce)
                assertEquals("did:key:zAlice", response.subject_did)
                assertEquals("Ed25519", response.signature_alg)
            })
        }
    }

    @Test
    fun `test_authenticateForOperation_rejects_when_server_required_role_exceeds_local_role`() = runTest {
        every { teeAuth.tryGetPublicKey() } returns Result.success("device-public-key")
        coordinator.registerLocalIdentity("did:key:zBob", "member", "Bob")
        coEvery { biometricAuth.authenticate(any(), any()) } returns BiometricResult.Success
        every { teeAuth.getPublicKey() } returns "device-public-key"
        coEvery { api.requestChallenge(any()) } returns Result.success(
            challenge(cmdType = "stop-instance").copy(required_role = "admin", risk_level = "medium")
        )

        val result = coordinator.authenticateForOperation(
            "stop-instance",
            buildJsonObject { put("instance_id", "inst-1") }
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.verifySignature(any()) }
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
            challenge()
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
            challenge()
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

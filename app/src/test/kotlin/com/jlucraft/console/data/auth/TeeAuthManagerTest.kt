package com.jlucraft.console.data.auth

import com.jlucraft.console.data.remote.AuthChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN.md §7.6: Unit tests for the canonical challenge message builder.
 *
 * Tests the pure message construction function without requiring real
 * TEE hardware, isolating the format contract from Android Keystore signing.
 */
class TeeAuthManagerCanonicalMessageTest {

    @Test
    fun `canonical message matches the exact JLUCraftAuthV1 format`() {
        val challenge = AuthChallenge(
            challenge_id = "uuid-1234-abcd",
            nonce = "nonce-5678-efgh",
            issued_at = "2026-05-04T12:00:00Z",
            expires_at = "2026-05-04T12:34:56Z",
            ttl_seconds = 60,
            cmd_type = "stop-instance",
            payload_hash = "abc123hexhash",
            human_summary = "Stop instance Survival-1",
            risk_level = "medium",
            required_role = "admin"
        )

        val message = TeeAuthManager.buildCanonicalChallengeMessage(challenge)
        val messageStr = String(message, Charsets.UTF_8)

        val expected = "JLUCraftAuthV1||uuid-1234-abcd||nonce-5678-efgh||stop-instance||abc123hexhash||2026-05-04T12:34:56Z"
        assertEquals(expected, messageStr)
    }

    @Test
    fun `canonical message handles special characters in payload hash`() {
        val challenge = AuthChallenge(
            challenge_id = "ch-1",
            nonce = "n-2",
            issued_at = "2026-01-01T00:00:00Z",
            expires_at = "2026-01-01T00:01:00Z",
            ttl_seconds = 60,
            cmd_type = "grant-role",
            payload_hash = "deadbeef/cafe+babe=01234",
            human_summary = "",
            risk_level = "high",
            required_role = "president"
        )

        val message = TeeAuthManager.buildCanonicalChallengeMessage(challenge)
        val messageStr = String(message, Charsets.UTF_8)

        // The payload_hash must appear verbatim in the message
        assertTrue(messageStr.contains("deadbeef/cafe+babe=01234"))
        // The separators must be literal "||"
        assertTrue(messageStr.contains("||n-2||grant-role||"))
    }

    @Test
    fun `canonical message uses UTF-8 encoding`() {
        val challenge = AuthChallenge(
            challenge_id = "ch-unicode-中文",
            nonce = "nonce-unicode",
            issued_at = "2026-01-01T00:00:00Z",
            expires_at = "2026-01-01T00:01:00Z",
            ttl_seconds = 60,
            cmd_type = "create-proposal",
            payload_hash = "hash-123",
            human_summary = "",
            risk_level = "low",
            required_role = "admin"
        )

        val message = TeeAuthManager.buildCanonicalChallengeMessage(challenge)

        // Verify it's valid UTF-8
        val messageStr = String(message, Charsets.UTF_8)
        assertEquals(message.size, messageStr.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `canonical message is deterministic for equal challenges`() {
        val challenge1 = AuthChallenge(
            challenge_id = "same-id",
            nonce = "same-nonce",
            issued_at = "2026-01-01T00:00:00Z",
            expires_at = "2026-01-01T00:01:00Z",
            ttl_seconds = 60,
            cmd_type = "migrate-instance",
            payload_hash = "hash-abc",
            human_summary = "",
            risk_level = "high",
            required_role = "admin"
        )

        val challenge2 = AuthChallenge(
            challenge_id = "same-id",
            nonce = "same-nonce",
            issued_at = "2026-01-01T00:00:00Z",
            expires_at = "2026-01-01T00:01:00Z",
            ttl_seconds = 60,
            cmd_type = "migrate-instance",
            payload_hash = "hash-abc",
            human_summary = "",
            risk_level = "high",
            required_role = "admin"
        )

        val msg1 = TeeAuthManager.buildCanonicalChallengeMessage(challenge1)
        val msg2 = TeeAuthManager.buildCanonicalChallengeMessage(challenge2)

        assertEquals(String(msg1, Charsets.UTF_8), String(msg2, Charsets.UTF_8))
    }

    @Test
    fun `canonical message changes with different challenge id`() {
        val challenge1 = AuthChallenge(
            challenge_id = "id-1", nonce = "n-1", issued_at = "2026-01-01T00:00:00Z",
            expires_at = "2026-01-01T00:01:00Z", ttl_seconds = 60,
            cmd_type = "test", payload_hash = "h-1",
            human_summary = "", risk_level = "low", required_role = "admin"
        )
        val challenge2 = AuthChallenge(
            challenge_id = "id-2", nonce = "n-1", issued_at = "2026-01-01T00:00:00Z",
            expires_at = "2026-01-01T00:01:00Z", ttl_seconds = 60,
            cmd_type = "test", payload_hash = "h-1",
            human_summary = "", risk_level = "low", required_role = "admin"
        )

        val msg1 = String(TeeAuthManager.buildCanonicalChallengeMessage(challenge1), Charsets.UTF_8)
        val msg2 = String(TeeAuthManager.buildCanonicalChallengeMessage(challenge2), Charsets.UTF_8)

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `canonical message starts with protocol version prefix`() {
        val challenge = AuthChallenge(
            challenge_id = "test-id", nonce = "test-nonce",
            issued_at = "2026-01-01T00:00:00Z", expires_at = "2026-01-01T00:01:00Z",
            ttl_seconds = 60, cmd_type = "test", payload_hash = "hash",
            human_summary = "", risk_level = "low", required_role = "admin"
        )

        val message = String(TeeAuthManager.buildCanonicalChallengeMessage(challenge), Charsets.UTF_8)
        assertTrue(message.startsWith("JLUCraftAuthV1||"))
    }

    @Test
    fun `canonical message uses literal pipe pairs as separators`() {
        val challenge = AuthChallenge(
            challenge_id = "cid", nonce = "nonce",
            issued_at = "2026-01-01T00:00:00Z", expires_at = "2026-01-01T00:01:00Z",
            ttl_seconds = 60, cmd_type = "cmd", payload_hash = "ph",
            human_summary = "", risk_level = "low", required_role = "admin"
        )

        val message = String(TeeAuthManager.buildCanonicalChallengeMessage(challenge), Charsets.UTF_8)
        // There should be exactly 6 fields separated by 5 "||" pairs
        val parts = message.split("||")
        assertEquals(6, parts.size)
        assertEquals("JLUCraftAuthV1", parts[0])
    }
}

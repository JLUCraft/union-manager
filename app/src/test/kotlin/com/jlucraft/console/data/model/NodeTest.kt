package com.jlucraft.console.data.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTest {

    private fun createInstance(status: String) = Instance(
        id = "inst-1", name = "test", kind = "room", status = status,
        currentHost = "node-1", createdAt = "2025-01-01T10:00:00Z"
    )

    @Test
    fun `test_instance_status_text`() {
        assertTrue(createInstance("stopped").isStopped)
        assertTrue(createInstance("Stopped").isStopped)
        assertTrue(createInstance("STOPPED").isStopped)
        assertFalse(createInstance("running").isStopped)
        assertFalse(createInstance("migrating").isStopped)
        assertFalse(createInstance("degraded").isStopped)
        assertFalse(createInstance("provisioning").isStopped)
        assertFalse(createInstance("destroyed").isStopped)
        assertFalse(createInstance("failed").isStopped)
    }

    @Test
    fun `test_instance_isStopped`() {
        assertTrue(createInstance("stopped").isStopped)
        assertFalse(createInstance("running").isStopped)
    }

    @Test
    fun `test_kindText_room_vs_service`() {
        assertEquals("房间", Instance(
            id = "inst-1", name = "test", kind = "room", status = "running",
            currentHost = "node-1", createdAt = "2025-01-01"
        ).kindText())
        assertEquals("服务", Instance(
            id = "inst-2", name = "test", kind = "service", status = "running",
            currentHost = "node-1", createdAt = "2025-01-01"
        ).kindText())
    }

    @Test
    fun `test_tournament_status_styles`() {
        assertEquals("进行中", createTournament("ongoing").statusText())
        assertEquals("报名中", createTournament("registration").statusText())
        assertEquals("已结束", createTournament("completed").statusText())
        assertEquals("草稿", createTournament("draft").statusText())
        assertEquals("unknown", createTournament("unknown").statusText())
    }

    private fun createTournament(status: String) = Tournament(
        id = "t1", name = "test", game_type = "battle", mode = "solo",
        status = status, max_participants = 16, min_member_score = 0,
        created_at = "2025-01-01", created_by = "user1"
    )

    @Test
    fun `test_proposal_status_styles`() {
        assertEquals("待签名", createProposal("pending").statusText())
        assertEquals("已通过", createProposal("approved").statusText())
        assertEquals("已执行", createProposal("executed").statusText())
        assertEquals("已拒绝", createProposal("rejected").statusText())
        assertEquals("已过期", createProposal("expired").statusText())
        assertEquals("unknown", createProposal("unknown").statusText())
    }

    private fun createProposal(status: String) = Proposal(
        id = "p1", proposalType = "test-proposal", proposer = "user1",
        expiresAt = "2025-01-02", signatures = emptyList(), status = status,
        createdAt = "2025-01-01", payload = buildJsonObject { put("key", "value") }
    )

    @Test
    fun `test_proposal_displayType`() {
        assertEquals("TEST PROPOSAL", createProposal("pending").displayType())
        assertEquals("INSTANCE CREATE", Proposal(
            id = "p1", proposalType = "instance-create", proposer = "user1",
            expiresAt = "2025-01-02", signatures = emptyList(), status = "pending",
            createdAt = "2025-01-01", payload = buildJsonObject { put("key", "value") }
        ).displayType())
    }

    @Test
    fun `test_match_status_styles`() {
        assertEquals("已安排", createMatch("scheduled").statusText())
        assertEquals("进行中", createMatch("live").statusText())
        assertEquals("已结束", createMatch("finished").statusText())
        assertEquals("争议中", createMatch("disputed").statusText())
        assertEquals("unknown", createMatch("unknown").statusText())
    }

    private fun createMatch(status: String) = Match(
        id = "m1", tournament_id = "t1", round = 1,
        participants = listOf("p1", "p2"), status = status,
        scheduled_at = "2025-01-01T10:00:00Z"
    )

    @Test
    fun `test_audit_entry_truncate`() {
        val longPubkey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        assertEquals("0123456789ab...", longPubkey.truncate(12))
        assertEquals("0123456789ab...", longPubkey.truncate(12, "..."))
        assertEquals("short", "short".truncate(12))
        assertEquals("exactly12chr", "exactly12chr".truncate(12))
    }

    @Test
    fun `test_toShortDate_formatting`() {
        assertEquals("2025-01-15", "2025-01-15T10:30:00Z".toShortDate())
        assertEquals("2025-01-15", "2025-01-15".toShortDate())
        assertEquals("short", "short".toShortDate())
        assertEquals("", "".toShortDate())
    }
}

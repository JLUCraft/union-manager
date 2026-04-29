package com.jlucraft.console.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiServiceTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `test_createInstanceRequest_serialization`() {
        val request = CreateInstanceRequest(
            name = "test-instance",
            kind = "room",
            owner = "player1",
            club = "club1",
            runtime = InstanceRuntimeSpec(
                image = "ghcr.io/jlucraft/game:v1",
                command = listOf("./start.sh"),
                env = mapOf("WORLD" to "survival"),
                labels = mapOf("game" to "battle"),
                workingDir = "/data",
                dataMountPath = "/mnt/data",
                logPath = "/var/log"
            ),
            resources = ResourceRequest(
                cpuCores = 2,
                memoryGb = 4,
                diskGb = 10
            ),
            autoRestart = true,
            admission = AdmissionPolicy(mode = AdmissionPolicy.MODE_PUBLIC)
        )

        val encoded = json.encodeToString(CreateInstanceRequest.serializer(), request)

        assertTrue(encoded.contains("\"name\":\"test-instance\""))
        assertTrue(encoded.contains("\"kind\":\"room\""))
        assertTrue(encoded.contains("\"auto_restart\":true"))
        assertTrue(encoded.contains("\"cpu_cores\":2"))
        assertTrue(encoded.contains("\"image\":\"ghcr.io/jlucraft/game:v1\""))
        assertTrue(encoded.contains("\"mode\":\"public\""))
    }

    @Test
    fun `test_authChallenge_deserialization`() {
        val jsonString = """{"nonce":"abc123","ts":1700000000000,"payload_hash":"hash123","ttl":300}"""
        val challenge = json.decodeFromString(AuthChallenge.serializer(), jsonString)

        assertEquals("abc123", challenge.nonce)
        assertEquals(1700000000000L, challenge.ts)
        assertEquals("hash123", challenge.payload_hash)
        assertEquals(300L, challenge.ttl)
    }

    @Test
    fun `test_clusterHealthResponse_deserialization`() {
        val jsonString = """{"status":"healthy","peer_id":"node-1","connected_peers":3,"running_instances":5,"consensus_role":"leader"}"""
        val health = json.decodeFromString(ClusterHealthResponse.serializer(), jsonString)

        assertEquals("healthy", health.status)
        assertEquals("node-1", health.peer_id)
        assertEquals(3, health.connected_peers)
        assertEquals(5, health.running_instances)
        assertEquals("leader", health.consensus_role)
    }

    @Test
    fun `test_networkSnapshot_deserialization`() {
        val jsonString = """{"runtime":{"identity":{"peer_id":"12D3KooW"}},"connected_peers":["peer1","peer2"]}"""
        val snapshot = json.decodeFromString(NetworkSnapshot.serializer(), jsonString)

        assertEquals("12D3KooW", snapshot.runtime?.identity?.peer_id)
        assertEquals(2, snapshot.connected_peers.size)
        assertEquals("peer1", snapshot.connected_peers[0])
        assertEquals("peer2", snapshot.connected_peers[1])
    }

    @Test
    fun `test_networkSnapshot_missing_runtime_fields`() {
        val jsonString = """{"connected_peers":[]}"""
        val snapshot = json.decodeFromString(NetworkSnapshot.serializer(), jsonString)

        assertEquals(null, snapshot.runtime)
        assertTrue(snapshot.connected_peers.isEmpty())
    }

    @Test
    fun `test_admissionPolicy_serialization`() {
        val policy = AdmissionPolicy(
            mode = AdmissionPolicy.MODE_CLUB_ONLY,
            allowed_clubs = listOf("club-a", "club-b"),
            requires_verified_email = true,
            allowed_email_domains = listOf("example.com")
        )

        val encoded = json.encodeToString(AdmissionPolicy.serializer(), policy)

        assertTrue(encoded.contains("\"mode\":\"club-only\""))
        assertTrue(encoded.contains("\"allowed_clubs\":[\"club-a\",\"club-b\"]"))
        assertTrue(encoded.contains("\"requires_verified_email\":true"))
        assertTrue(encoded.contains("\"allowed_email_domains\":[\"example.com\"]"))
    }

    @Test
    fun `test_admissionPolicy_default_values`() {
        val policy = AdmissionPolicy(mode = AdmissionPolicy.MODE_PUBLIC)

        val encoded = json.encodeToString(AdmissionPolicy.serializer(), policy)

        assertTrue(encoded.contains("\"mode\":\"public\""))
        assertTrue(encoded.contains("\"allowed_clubs\":[]"))
        assertTrue(encoded.contains("\"requires_verified_email\":false"))
    }

    @Test
    fun `test_authRequest_serialization`() {
        val request = AuthRequest(
            cmd_type = "create-instance",
            payload = buildJsonObject { put("name", "test-instance") },
            public_key = "base64pubkey=="
        )

        val encoded = json.encodeToString(AuthRequest.serializer(), request)

        assertTrue(encoded.contains("\"cmd_type\":\"create-instance\""))
        assertTrue(encoded.contains("\"public_key\":\"base64pubkey==\""))
        assertTrue(encoded.contains("\"name\":\"test-instance\""))
    }

    @Test
    fun `test_authResult_deserialization`() {
        val successJson = """{"success":true,"message":"ok"}"""
        val success = json.decodeFromString(AuthResult.serializer(), successJson)
        assertTrue(success.success)
        assertEquals("ok", success.message)

        val failureJson = """{"success":false,"message":"denied"}"""
        val failure = json.decodeFromString(AuthResult.serializer(), failureJson)
        assertFalse(failure.success)
        assertEquals("denied", failure.message)
    }

    @Test
    fun `test_signResponse_serialization`() {
        val response = SignResponse(nonce = "nonce1", signature = "sig1")

        val encoded = json.encodeToString(SignResponse.serializer(), response)

        assertTrue(encoded.contains("\"nonce\":\"nonce1\""))
        assertTrue(encoded.contains("\"signature\":\"sig1\""))
    }
}

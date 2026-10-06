package com.jlucraft.manager.nativecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnionNativeTest {
    @Test fun identityRoundTripUsesPackagedAndroidLibrary() {
        val secret = UnionNative.generateSecret()
        try {
            assertEquals(UnionNative.peerId(secret), UnionNative.peerId(secret.clone()))
            assertTrue(UnionNative.peerId(secret).isNotBlank())
        } finally { secret.fill(0) }
    }
    @Test fun opsRejectsInvalidInputBeforeOpeningNetwork() {
        val secret = UnionNative.generateSecret()
        try {
            for (input in listOf("{invalid", "{}", "x".repeat(65537))) {
                assertFalse(org.json.JSONObject(UnionNative.executeOps(secret, input)).isNull("error_code"))
            }
        } finally { secret.fill(0) }
    }
    @Test fun fileVersionsUsePackagedCidVerification() {
        val files = """{"revision":0,"admins":[],"members":{},"clock_ms":0,"files":{"blocks":{"bafkreigdg6n6gudw43q635jqqns45zwp3epbuxyotdjtnydlnps3h5zxva":"b2ZmaWNlIGZpeHR1cmU","baguqeerawzzpt6wo3a7qc7j5jhvu2kyjycyru5cdxf64kqhby5hhccio4eaq":"eyJjaGVja3N1bSI6ImMzMzc5YmUzNTA3NmU2ZTFlZGY1MzA4MzY1Y2VlNmNmZDkxZTFhNWYwZTk4ZDMzNmUwNmI2YmU1YjNmNzM3YTgiLCJkZXB0aCI6MCwibGF5b3V0Ijp7InRyZWUiOnsiLyI6ImJhZmtyZWlnZGc2bjZndWR3NDNxNjM1anFxbnM0NXp3cDNlcGJ1eHlvdGRqdG55ZGxucHMzaDV6eHZhIn0sInR5cGUiOiJmdWxsIn0sInBhcmVudCI6bnVsbCwic2l6ZSI6MTQsInR5cGUiOiJmaWxlIn0"},"branches":{}}}"""
        val secret = UnionNative.generateSecret()
        val snapshot = try {
            org.json.JSONObject(files).put("identity", org.json.JSONObject()
                .put("genesis", "a".repeat(64)).put("namespace", "office")
                .put("audience", UnionNative.peerId(secret)).put("service", "office").put("club", "club"))
                .toString()
        } finally { secret.fill(0) }
        val cid = "baguqeerawzzpt6wo3a7qc7j5jhvu2kyjycyru5cdxf64kqhby5hhccio4eaq"
        assertArrayEquals("office fixture".toByteArray(), UnionNative.readOfficeFile(snapshot, cid))
        val corrupt = org.json.JSONObject(snapshot)
        corrupt.getJSONObject("files").getJSONObject("blocks").put("bafkreigdg6n6gudw43q635jqqns45zwp3epbuxyotdjtnydlnps3h5zxva", "dGFtcGVy")
        assertThrows(IllegalStateException::class.java) { UnionNative.readOfficeFile(corrupt.toString(), cid) }
        assertThrows(IllegalStateException::class.java) { UnionNative.readOfficeFile(snapshot, "../file") }
    }
    @Test fun businessRequestsRequireUnifiedIdentityBeforeNetwork() {
        val secret = UnionNative.generateSecret()
        try {
            val input = org.json.JSONObject().put("address", "invalid")
                .put("peer", UnionNative.peerId(secret)).put("genesis_hash", "a".repeat(64)).toString()
            val league = org.json.JSONObject(UnionNative.execute(secret, input))
            assertEquals("invalid_request", league.getString("error_code"))
            val office = org.json.JSONObject(UnionNative.executeOffice(secret, input))
            assertEquals("invalid_request", office.getString("error_code"))
        } finally { secret.fill(0) }
    }
    @Test fun replicaNodeIdsRemainExactAcrossJsonAndJni() {
        val secret = UnionNative.generateSecret()
        try {
            val peer = UnionNative.peerId(secret)
            val id = "18446744073709551615"
            val input = org.json.JSONObject().put("operation", "build_consensus").put("members", org.json.JSONArray().put(org.json.JSONObject().put("id", id)
                .put("node", org.json.JSONObject().put("peer", peer).put("addresses", org.json.JSONArray().put("/ip4/127.0.0.1/tcp/4001/p2p/$peer")))))
            val action = org.json.JSONObject(UnionNative.management(input.toString()))
            assertTrue(action.getJSONObject("members").has(id))
        } finally { secret.fill(0) }
    }
    @Test fun invalidSecretsAreRejected() {
        for (secret in listOf(byteArrayOf(), ByteArray(4097), byteArrayOf(1, 2, 3))) {
            assertThrows(IllegalStateException::class.java) { UnionNative.peerId(secret) }
        }
    }
}

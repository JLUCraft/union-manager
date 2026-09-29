package com.jlucraft.manager.nativecore
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
@RunWith(AndroidJUnit4::class)
class IdentityProtocolTest {
    private fun genesis(secret: ByteArray) = JSONObject().put("federation", "android-test")
        .put("founders", JSONArray().put(JSONObject().put("member", "stable-person").put("club", "test-club")
            .put("public_key", JSONArray(UnionNative.publicKey(secret))))).put("quorum", 1)
    private fun digest(value: JSONObject) = MessageDigest.getInstance("SHA-256")
        .digest(value.toString().toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun record(pin: String, sequence: Long, previous: String, action: JSONObject) = JSONObject()
        .put("federation", "android-test").put("genesis", pin).put("sequence", sequence)
        .put("previous", previous).put("action", action).put("signatures", JSONArray())
    private fun review(genesis: JSONObject, pin: String, records: JSONArray): JSONObject = JSONObject(
        UnionNative.reviewIdentityHistory(JSONObject().put("genesis", genesis).put("genesis_hash", pin).put("records", records).toString()))
    @Test fun actualJniVerifiesDeviceReplacementWithoutChangingPerson() {
        val old = UnionNative.generateSecret(); val next = UnionNative.generateSecret()
        try {
            val genesis = genesis(old); val pin = digest(genesis)
            val binding = record(pin, 1, pin, JSONObject().put("type", "bind_device").put("member", "stable-person").put("device", UnionNative.peerId(next)))
            val signed = JSONObject(UnionNative.signIdentityRecord(next, UnionNative.signIdentityRecord(old, binding.toString())))
            val records = JSONArray().put(signed); val state = review(genesis, pin, records)
            assertEquals("stable-person", identityMember(state, UnionNative.peerId(next))?.memberId)
            val revoke = record(pin, 2, state.getJSONObject("checkpoint").getString("head"), JSONObject()
                .put("type", "revoke_device").put("member", "stable-person").put("device", UnionNative.peerId(old)))
            records.put(JSONObject(UnionNative.signIdentityRecord(next, revoke.toString())))
            val updated = review(genesis, pin, records)
            assertNull(identityMember(updated, UnionNative.peerId(old)))
            assertEquals(setOf("test-club"), identityMember(updated, UnionNative.peerId(next))?.chairOf)
        } finally { old.fill(0); next.fill(0) }
    }
    @Test fun actualJniRejectsModifiedParentAndQualificationExpiryFields() {
        val secret = UnionNative.generateSecret()
        try {
            val genesis = genesis(secret); val pin = digest(genesis)
            val action = JSONObject().put("type", "revoke_membership").put("member", "stable-person").put("club", "test-club")
            val signed = JSONObject(UnionNative.signIdentityRecord(secret, record(pin, 1, pin, action).toString())).put("previous", "0".repeat(64))
            assertThrows(IllegalStateException::class.java) { review(genesis, pin, JSONArray().put(signed)) }
            action.put("expires_at", 1)
            assertThrows(IllegalStateException::class.java) { UnionNative.signIdentityRecord(secret, record(pin, 1, pin, action).toString()) }
        } finally { secret.fill(0) }
    }
    @Test fun placeholderGenesisIsNeverTrusted() {
        val genesis = JSONObject().put("federation", "placeholder").put("quorum", 1)
            .put("founders", JSONArray().put(JSONObject().put("member", "placeholder").put("club", "placeholder").put("public_key", JSONArray())))
        assertThrows(IllegalStateException::class.java) { review(genesis, digest(genesis), JSONArray()) }
    }
}

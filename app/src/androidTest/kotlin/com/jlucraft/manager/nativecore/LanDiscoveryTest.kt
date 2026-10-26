package com.jlucraft.manager.nativecore

import android.content.Context
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Real two-device LAN discovery against a supplied, disposable host. */
@RequiresFixture
@RunWith(AndroidJUnit4::class)
class LanDiscoveryTest {
    private fun fixture(): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = checkNotNull(InstrumentationRegistry.getArguments().getString("lanFixture")) { "Set lanFixture" }
        require(path.matches(Regex("/data/local/tmp/[A-Za-z0-9._-]+\\.json")))
        return ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cat $path"),
        ).use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) }
    }

    private fun fixtureHost(fixture: JSONObject): String {
        val address = fixture.getString("address")
        require(address.startsWith("/ip4/"))
        return address.removePrefix("/ip4/").substringBefore('/')
    }

    /** This public HTTP control is separate from the product's QUIC transport. */
    private fun tcpControl(host: String, port: Int, fixture: JSONObject) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 3_000)
            socket.soTimeout = 3_000
            socket.getOutputStream().write("GET /health HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            val response = socket.getInputStream().bufferedReader().readText()
            assertTrue("TCP control returned $response", response.lineSequence().first().contains(" 200 "))
            val body = JSONObject(response.substringAfter("\r\n\r\n"))
            assertEquals(fixture.getString("peer"), body.getString("peer"))
        }
    }

    @Test fun directTcpControlReachesThePublicHealthEndpoint() {
        val fixture = fixture()
        tcpControl(fixtureHost(fixture), fixture.getInt("tcp_control_port"), fixture)
    }

    @Test fun reverseTcpControlReachesThePublicHealthEndpoint() {
        val fixture = fixture()
        val port = checkNotNull(InstrumentationRegistry.getArguments().getString("reverseControlPort")).toInt()
        tcpControl("127.0.0.1", port, fixture)
    }

    /** A nonce echo establishes only the independent UDP path, never a QUIC handshake. */
    @Test fun udpControlEchoesTheNonceOverTheLanInterface() {
        val fixture = fixture()
        val address = InetAddress.getByName(fixtureHost(fixture))
        val port = fixture.getInt("udp_echo_port")
        val nonce = "jlucraft-lan-control-${UUID.randomUUID()}".toByteArray(Charsets.US_ASCII)
        DatagramSocket().use { socket ->
            socket.soTimeout = 3_000
            socket.send(DatagramPacket(nonce, nonce.size, address, port))
            val received = DatagramPacket(ByteArray(1024), 1024)
            socket.receive(received)
            assertEquals(address, received.address)
            assertEquals(port, received.port)
            assertArrayEquals(nonce, received.data.copyOfRange(received.offset, received.offset + received.length))
        }
    }

    @Test fun directlyDialsThePublicFixtureAddress() {
        val fixture = fixture()
        val secret = UnionNative.generateSecret()
        try {
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("fixture_operation", "direct_genesis")
                putString("fixture_address", fixture.getString("address"))
            })
            val fetched = JSONObject(UnionNative.fetchGenesis(secret, JSONObject()
                .put("address", fixture.getString("address"))
                .put("peer", fixture.getString("peer")).toString()))
            assertFalse("Direct QUIC genesis fetch failed: $fetched", fetched.has("error"))
            assertEquals(fixture.getString("identity_genesis_hash"), fetched.getJSONObject("identity").getString("genesis_hash"))
            assertEquals(fixture.getString("application_genesis_hash"), fetched.getJSONObject("application").getString("genesis_hash"))
        } finally {
            secret.fill(0)
        }
    }

    /** The same device identity opens six short sessions; every call must finish without retry. */
    @Test fun successiveGenesisReadsReleaseEveryConnection() {
        val fixture = fixture()
        val secret = UnionNative.generateSecret()
        val peer = UnionNative.peerId(secret)
        try {
            repeat(6) { index ->
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("fixture_operation", "successive_genesis")
                    putString("fixture_client_peer", peer)
                    putString("fixture_address", fixture.getString("address"))
                    putInt("fixture_iteration", index + 1)
                })
                val fetched = JSONObject(UnionNative.fetchGenesis(secret, JSONObject()
                    .put("address", fixture.getString("address"))
                    .put("peer", fixture.getString("peer")).toString()))
                assertFalse("Genesis read ${index + 1} failed: $fetched", fetched.has("error"))
                assertEquals(fixture.getString("identity_genesis_hash"), fetched.getJSONObject("identity").getString("genesis_hash"))
                assertEquals(fixture.getString("application_genesis_hash"), fetched.getJSONObject("application").getString("genesis_hash"))
            }
        } finally {
            secret.fill(0)
        }
    }

    @Test fun discoversAndDialsTheHostWithDistinctIdentityAndApplicationPins() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = fixture()
        val peer = fixture.getString("peer")
        val identityPin = fixture.getString("identity_genesis_hash")
        val applicationPin = fixture.getString("application_genesis_hash")
        assertNotEquals(identityPin, applicationPin)
        assertTrue(GENESIS_HASH_PATTERN.matches(identityPin))
        assertTrue(GENESIS_HASH_PATTERN.matches(applicationPin))
        val wifi = instrumentation.targetContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicast = wifi.createMulticastLock("jlucraft-lan-fixture")
        val secret = UnionNative.generateSecret()
        try {
            multicast.acquire()
            val result = JSONObject(UnionNative.discover(secret, JSONObject().put("wait_ms", 10_000).toString()))
            assertFalse(result.toString(), result.has("error"))
            val servers = result.getJSONArray("servers")
            val found = (0 until servers.length()).map { servers.getJSONObject(it) }
                .singleOrNull { it.optString("peer") == peer }
            assertNotNull("Fixture host absent from real mDNS results: $result", found)
            val server = checkNotNull(found)
            assertEquals(identityPin, server.getString("genesis_hash"))
            assertEquals(applicationPin, server.getJSONObject("application").getString("genesis_hash"))
            val target = checkNotNull(server.applicationConnectionTarget())
            instrumentation.sendStatus(2, Bundle().apply {
                putString("fixture_operation", "discovered_genesis")
                putString("fixture_address", target.address)
            })
            assertEquals(applicationPin, target.genesisHash)
            val fetched = JSONObject(UnionNative.fetchGenesis(secret, JSONObject()
                .put("address", target.address).put("peer", target.peer).toString()))
            assertFalse(fetched.toString(), fetched.has("error"))
            assertEquals(identityPin, fetched.getJSONObject("identity").getString("genesis_hash"))
            assertEquals(applicationPin, fetched.getJSONObject("application").getString("genesis_hash"))
        } finally {
            if (multicast.isHeld) multicast.release()
            secret.fill(0)
        }
    }
}

package com.jlucraft.console.data.remote

import kotlinx.coroutines.flow.SharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushServiceTest {

    @Test
    fun `test_wsUrl_constructs_correctly`() {
        val service = PushService("https://example.com")
        val method = PushService::class.java.getDeclaredMethod("wsUrl", String::class.java)
        method.isAccessible = true

        val result = method.invoke(service, "https://example.com") as String
        assertEquals("ws://example.com/v1/ws", result)
    }

    @Test
    fun `test_wsUrl_removes_trailing_slash`() {
        val service = PushService("https://example.com")
        val method = PushService::class.java.getDeclaredMethod("wsUrl", String::class.java)
        method.isAccessible = true

        val result = method.invoke(service, "https://example.com/") as String
        assertEquals("ws://example.com/v1/ws", result)
    }

    @Test
    fun `test_wsUrl_handles_http_scheme`() {
        val service = PushService("https://example.com")
        val method = PushService::class.java.getDeclaredMethod("wsUrl", String::class.java)
        method.isAccessible = true

        val result = method.invoke(service, "http://example.com") as String
        assertEquals("ws://example.com/v1/ws", result)
    }

    @Test
    fun `test_events_flow_sends_webSocket_events`() {
        val service = PushService("https://example.com")
        assertTrue(service.events is SharedFlow<*>)
    }

    @Test
    fun `test_updateServerUrl_reconnects`() {
        val service = PushService("https://example.com")
        val cachedWsUrlField = PushService::class.java.getDeclaredField("cachedWsUrl")
        cachedWsUrlField.isAccessible = true

        assertEquals("ws://example.com/v1/ws", cachedWsUrlField.get(service))

        service.updateServerUrl("https://newhost.example.com")

        assertEquals("ws://newhost.example.com/v1/ws", cachedWsUrlField.get(service))
    }

    @Test
    fun `test_updateServerUrl_removes_trailing_slash`() {
        val service = PushService("https://example.com")
        val cachedWsUrlField = PushService::class.java.getDeclaredField("cachedWsUrl")
        cachedWsUrlField.isAccessible = true

        service.updateServerUrl("https://newhost.example.com/")

        assertEquals("ws://newhost.example.com/v1/ws", cachedWsUrlField.get(service))
    }
}

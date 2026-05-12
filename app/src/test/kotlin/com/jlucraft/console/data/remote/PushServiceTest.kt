package com.jlucraft.console.data.remote

import com.jlucraft.console.data.model.PushEventPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushServiceTest {

    @Test
    fun `test_events_flow_is_shared_flow`() {
        val service = PushService()
        assertNotNull(service.events)
    }

    @Test
    fun `test_connect_is_idempotent`() {
        val service = PushService()
        service.connect()
        service.connect()
        service.disconnect()
    }

    @Test
    fun `test_disconnect_before_connect_is_safe`() {
        val service = PushService()
        service.disconnect()
    }

    @Test
    fun `test_no_ws_url_construction`() {
        val service = PushService()
        val methods = PushService::class.java.declaredMethods.map { it.name }
        assertTrue("wsUrl should not exist", "wsUrl" !in methods)
    }

    @Test
    fun `test_no_update_server_url_method`() {
        val service = PushService()
        val methods = PushService::class.java.declaredMethods.map { it.name }
        assertTrue("updateServerUrl should not exist", "updateServerUrl" !in methods)
    }

    @Test
    fun `test_push_event_data_class`() {
        val testPayload = com.jlucraft.console.data.model.GenericPushEventData(raw = "test")
        val event = PushService.PushEvent(
            type = "auth_challenge",
            data = testPayload
        )
        assertEquals("auth_challenge", event.type)
        assertEquals(testPayload, event.data)
    }
}

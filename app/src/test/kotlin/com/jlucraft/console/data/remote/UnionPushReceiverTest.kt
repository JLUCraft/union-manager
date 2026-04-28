package com.jlucraft.console.data.remote

import android.content.Context
import app.cash.turbine.test
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

@OptIn(ExperimentalCoroutinesApi::class)
class UnionPushReceiverTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var receiver: UnionPushReceiver

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        receiver = UnionPushReceiver()
        mockkObject(NotificationHelper)
        every { NotificationHelper.show(any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockPushMessage(json: String): PushMessage {
        val message = mockk<PushMessage>()
        every { message.content } returns json.toByteArray()
        return message
    }

    private fun mockContext(): Context = mockk()

    // ------------------------------------------------------------------ //
    // 1. onMessage decodes JSON and emits WebSocketEvent
    // ------------------------------------------------------------------ //

    @Test
    fun `onMessage decodes JSON and emits WebSocketEvent`() = runTest {
        val message = mockPushMessage("""{"type":"instance_crash","name":"test"}""")
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("instance_crash", event.type)
            assertEquals("test", event.data["name"]?.jsonPrimitive?.content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ //
    // 2. onMessage extracts type field from JSON correctly
    // ------------------------------------------------------------------ //

    @Test
    fun `onMessage extracts type field from JSON correctly`() = runTest {
        val message = mockPushMessage("""{"type":"node_offline","node_id":"node-1"}""")
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("node_offline", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ //
    // 3. onMessage handles invalid JSON by using unknown type
    // ------------------------------------------------------------------ //

    @Test
    fun `onMessage handles invalid JSON by using unknown type`() = runTest {
        val message = mockPushMessage("not valid json")
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("unknown", event.type)
            assertEquals("not valid json", event.data["raw"]?.jsonPrimitive?.content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ //
    // 4. onMessage shows notification for instance_crash events
    // ------------------------------------------------------------------ //

    @Test
    fun `onMessage shows notification for instance_crash events`() = runTest {
        val message = mockPushMessage("""{"type":"instance_crash","name":"test-instance"}""")
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) {
            NotificationHelper.show(
                context, "Instance Crash", "test-instance", NotificationHelper.CHANNEL_ALERTS
            )
        }
    }

    // ------------------------------------------------------------------ //
    // 5. onMessage shows notification for node_offline events
    // ------------------------------------------------------------------ //

    @Test
    fun `onMessage shows notification for node_offline events`() = runTest {
        val message = mockPushMessage("""{"type":"node_offline","node_id":"node-1"}""")
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) {
            NotificationHelper.show(
                context, "Node Offline", "node-1", NotificationHelper.CHANNEL_ALERTS
            )
        }
    }

    // ------------------------------------------------------------------ //
    // 6. onMessage shows notification for alert_fired events (high priority channel)
    // ------------------------------------------------------------------ //

    @Test
    fun `onMessage shows notification for alert_fired events`() = runTest {
        val message = mockPushMessage(
            """{"type":"alert_fired","name":"High CPU","message":"CPU above 90%"}"""
        )
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) {
            NotificationHelper.show(
                context,
                "Alert Triggered",
                "High CPU: CPU above 90%",
                NotificationHelper.CHANNEL_ALERTS
            )
        }
    }

    // ------------------------------------------------------------------ //
    // 7. onMessage does NOT show notification for non-critical events
    // ------------------------------------------------------------------ //

    @Test
    fun `onMessage does NOT show notification for non-critical events`() = runTest {
        val message = mockPushMessage("""{"type":"heartbeat","name":"test"}""")
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) {
            NotificationHelper.show(any(), any(), any(), any())
        }
    }

    // ------------------------------------------------------------------ //
    // 8. onNewEndpoint emits endpoint URL
    // ------------------------------------------------------------------ //

    @Test
    fun `onNewEndpoint emits endpoint URL`() = runTest {
        val endpoint = mockk<PushEndpoint>()
        every { endpoint.url } returns "https://ntfy.example.com/endpoint"
        val context = mockContext()

        receiver.onNewEndpoint(context, endpoint, "instance")

        UnionPushReceiver.endpointFlow.test {
            assertEquals("https://ntfy.example.com/endpoint", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ //
    // 9. onNewEndpoint emits Embedded FCM when URL contains fcm.googleapis.com
    // ------------------------------------------------------------------ //

    @Test
    fun `onNewEndpoint emits Embedded FCM when URL contains fcm googleapis com`() = runTest {
        val endpoint = mockk<PushEndpoint>()
        every { endpoint.url } returns "https://fcm.googleapis.com/fcm/send/abc123"
        val context = mockContext()

        receiver.onNewEndpoint(context, endpoint, "instance")

        UnionPushReceiver.distributorInfo.test {
            assertEquals("Embedded FCM (\u5185\u7F6E)", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ //
    // 10. onRegistrationFailed emits registration_failed event with reason
    // ------------------------------------------------------------------ //

    @Test
    fun `onRegistrationFailed event type is correct`() = runTest {
        assertEquals("registration_failed", PushService.WebSocketEvent("registration_failed", buildJsonObject { put("reason", "test") }).type)
    }

    // ------------------------------------------------------------------ //
    // 11. onUnregistered emits empty string to endpointFlow
    // ------------------------------------------------------------------ //

    @Test
    fun `onUnregistered emits empty string to endpointFlow`() = runTest {
        val context = mockContext()

        receiver.onUnregistered(context, "instance")

        UnionPushReceiver.endpointFlow.test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ //
    // 12. pushEvents is a SharedFlow that can be collected
    // ------------------------------------------------------------------ //

    @Test
    fun `pushEvents is a SharedFlow`() {
        assertTrue(UnionPushReceiver.pushEvents is SharedFlow<*>)
    }
}

package com.jlucraft.console.data.remote

import android.content.Context
import app.cash.turbine.test
import com.jlucraft.console.data.model.AuthChallengeEventData
import com.jlucraft.console.data.model.InstanceCrashEventData
import com.jlucraft.console.data.model.NodeOfflineEventData
import com.jlucraft.console.proto.PushEventEnvelope
import com.jlucraft.console.proto.InstanceCrashPush
import com.jlucraft.console.proto.NodeOfflinePush
import com.jlucraft.console.proto.AlertFiredPush
import com.jlucraft.console.proto.AuthChallengePush
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
        every { NotificationHelper.show(any(), any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockProtoMessage(envelope: PushEventEnvelope): PushMessage {
        val message = mockk<PushMessage>()
        every { message.content } returns envelope.toByteArray()
        return message
    }

    private fun mockInvalidMessage(bytes: ByteArray): PushMessage {
        val message = mockk<PushMessage>()
        every { message.content } returns bytes
        return message
    }

    private fun mockContext(): Context = mockk()

    private fun buildInstanceCrashProto(): PushEventEnvelope {
        return PushEventEnvelope.newBuilder()
            .setType("instance_crash")
            .setInstanceCrash(
                InstanceCrashPush.newBuilder()
                    .setName("test-instance")
                    .setMessage("crash message")
                    .build()
            )
            .build()
    }

    private fun buildNodeOfflineProto(): PushEventEnvelope {
        return PushEventEnvelope.newBuilder()
            .setType("node_offline")
            .setNodeOffline(
                NodeOfflinePush.newBuilder()
                    .setNodeId("node-1")
                    .build()
            )
            .build()
    }

    private fun buildAlertFiredProto(): PushEventEnvelope {
        return PushEventEnvelope.newBuilder()
            .setType("alert_fired")
            .setAlertFired(
                AlertFiredPush.newBuilder()
                    .setName("High CPU")
                    .setMessage("CPU above 90%")
                    .build()
            )
            .build()
    }

    private fun buildAuthChallengeProto(): PushEventEnvelope {
        return PushEventEnvelope.newBuilder()
            .setType("AuthChallenge")
            .setAuthChallenge(
                AuthChallengePush.newBuilder()
                    .setHumanSummary("Approve migration")
                    .build()
            )
            .build()
    }

    @Test
    fun `onMessage decodes protobuf and emits WebSocketEvent`() = runTest {
        val envelope = buildInstanceCrashProto()
        val message = mockProtoMessage(envelope)
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("instance_crash", event.type)
            val data = event.data as InstanceCrashEventData
            assertEquals("test-instance", data.name)
            assertEquals("crash message", data.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onMessage extracts type field from protobuf correctly`() = runTest {
        val envelope = buildNodeOfflineProto()
        val message = mockProtoMessage(envelope)
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("node_offline", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onMessage handles invalid protobuf by using unknown type`() = runTest {
        val message = mockInvalidMessage(byteArrayOf(0x00, 0x01, 0x02))
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("unknown", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onMessage shows notification for instance_crash events`() = runTest {
        val envelope = buildInstanceCrashProto()
        val message = mockProtoMessage(envelope)
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) {
            NotificationHelper.show(
                context, "Instance Crash", "test-instance: crash message", NotificationHelper.CHANNEL_ALERTS
            )
        }
    }

    @Test
    fun `onMessage shows notification for node_offline events`() = runTest {
        val envelope = buildNodeOfflineProto()
        val message = mockProtoMessage(envelope)
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

    @Test
    fun `onMessage shows notification for alert_fired events`() = runTest {
        val envelope = buildAlertFiredProto()
        val message = mockProtoMessage(envelope)
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

    @Test
    fun `onMessage does NOT show notification for non-critical events`() = runTest {
        val envelope = PushEventEnvelope.newBuilder()
            .setType("heartbeat")
            .build()
        val message = mockProtoMessage(envelope)
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) {
            NotificationHelper.show(any(), any(), any(), any(), any())
        }
    }

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

    @Test
    fun `onRegistrationFailed event type is correct`() = runTest {
        assertEquals("registration_failed", PushService.WebSocketEvent("registration_failed", com.jlucraft.console.data.model.GenericPushEventData(raw = "test")).type)
    }

    @Test
    fun `onUnregistered emits empty string to endpointFlow`() = runTest {
        val context = mockContext()

        receiver.onUnregistered(context, "instance")

        UnionPushReceiver.endpointFlow.test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pushEvents is a SharedFlow`() {
        val flow: SharedFlow<*> = UnionPushReceiver.pushEvents
        assertTrue(flow.replayCache.isEmpty())
    }

    @Test
    fun `test_empty_message_body`() = runTest {
        val message = mockInvalidMessage(ByteArray(0))
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("unknown", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test_protobuf_missing_type_field`() = runTest {
        val envelope = PushEventEnvelope.newBuilder()
            .setInstanceCrash(
                InstanceCrashPush.newBuilder()
                    .setName("test")
                    .setMessage("hello")
                    .build()
            )
            .build()
        val message = mockProtoMessage(envelope)
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test_registration_failed_event`() = runTest {
        val context = mockContext()
        val reason = requireNotNull(FailedReason::class.java.enumConstants).first()

        UnionPushReceiver.pushEvents.test {
            receiver.onRegistrationFailed(context, reason, "instance")
            val event = awaitItem()
            assertEquals("registration_failed", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onMessage normalizes auth_challenge alias to AuthChallenge`() = runTest {
        val envelope = PushEventEnvelope.newBuilder()
            .setType("auth_challenge")
            .setAuthChallenge(
                AuthChallengePush.newBuilder()
                    .setHumanSummary("Approve stop instance")
                    .build()
            )
            .build()
        val message = mockProtoMessage(envelope)
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("AuthChallenge", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onMessage routes AuthChallenge to auth notification channel`() = runTest {
        val envelope = buildAuthChallengeProto()
        val message = mockProtoMessage(envelope)
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) {
            NotificationHelper.show(
                context,
                "\uD83D\uDD10 \u8EAB\u4EFD\u9A8C\u8BC1\u8BF7\u6C42",
                "Approve migration",
                NotificationHelper.CHANNEL_AUTH_CHALLENGE,
                "ACTION_AUTH_CHALLENGE"
            )
        }
    }

    @Test
    fun `test_multiple_rapid_messages`() = runTest {
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            repeat(48) { i ->
                val envelope = PushEventEnvelope.newBuilder()
                    .setType("test")
                    .build()
                val message = mockProtoMessage(envelope)
                receiver.onMessage(context, message, "instance")
            }

            repeat(48) {
                val event = awaitItem()
                assertEquals("test", event.type)
            }

            cancelAndIgnoreRemainingEvents()
        }
    }
}

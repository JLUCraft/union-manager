package com.jlucraft.console.data.remote

import android.content.Context
import app.cash.turbine.test
import com.jlucraft.console.data.model.PushEventPayload
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(NotificationHelper)
        every { NotificationHelper.show(any(), any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mockPushMessage(bytes: ByteArray): PushMessage {
        val message = mockk<PushMessage>()
        every { message.content } returns bytes
        return message
    }

    private fun mockContext(): Context = mockk()

    @Test
    fun `onMessage emits PushEvent to pushEvents flow`() = runTest {
        val receiver = UnionPushReceiver()
        val context = mockContext()

        val message = mockPushMessage(ByteArray(0))

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("unknown", event.type)
            assertTrue(event.data is PushEventPayload)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pushEvents is a SharedFlow`() {
        val flow: SharedFlow<*> = UnionPushReceiver.pushEvents
        assertTrue(flow.replayCache.isEmpty())
    }

    @Test
    fun `onNewEndpoint emits endpoint URL`() = runTest {
        val receiver = UnionPushReceiver()
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
        val receiver = UnionPushReceiver()
        val endpoint = mockk<PushEndpoint>()
        every { endpoint.url } returns "https://fcm.googleapis.com/fcm/send/abc123"
        val context = mockContext()

        receiver.onNewEndpoint(context, endpoint, "instance")

        UnionPushReceiver.distributorInfo.test {
            assertEquals("Embedded FCM (内置)", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onUnregistered emits empty string to endpointFlow`() = runTest {
        val receiver = UnionPushReceiver()
        val context = mockContext()

        receiver.onUnregistered(context, "instance")

        UnionPushReceiver.endpointFlow.test {
            assertEquals("", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test_empty_message_body`() = runTest {
        val receiver = UnionPushReceiver()
        val message = mockPushMessage(ByteArray(0))
        val context = mockContext()

        UnionPushReceiver.pushEvents.test {
            receiver.onMessage(context, message, "instance")
            val event = awaitItem()
            assertEquals("unknown", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test_registration_failed_event`() = runTest {
        val receiver = UnionPushReceiver()
        val context = mockContext()
        val reason = FailedReason.NETWORK

        UnionPushReceiver.pushEvents.test {
            receiver.onRegistrationFailed(context, reason, "instance")
            val event = awaitItem()
            assertEquals("registration_failed", event.type)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

package com.jlucraft.console.data.remote.libp2p

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


 *
 *
 *
class EventStreamManager(
    private val transport: Libp2pTransport,
    private val eventProtocolId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    private val subscriptions = ConcurrentHashMap<String, Subscription>()


    private val _events = MutableSharedFlow<EventEnvelope>(extraBufferCapacity = 256)
    val events: SharedFlow<EventEnvelope> = _events.asSharedFlow()


    private val _activeSubscriptionCount = MutableStateFlow(0)
    val activeSubscriptionCount: StateFlow<Int> = _activeSubscriptionCount.asStateFlow()


     *
    fun subscribe(topics: List<String>, groupKey: String): Flow<EventEnvelope> {

        subscriptions.remove(groupKey)?.job?.cancel()

        val topicList = topics.toList()
        val flow: Flow<EventEnvelope> = flow {
            val request = buildSubscribeRequest(topicList)
            val frameFlow = transport.openBidiStream(eventProtocolId, request)

            frameFlow.collect { frameBytes ->
                val envelope = parseEventEnvelope(frameBytes)
                if (envelope != null) {
                    _events.emit(envelope)
                    emit(envelope)
                }
            }
        }.retryWhen { cause, attempt ->
            calculateBackoff(attempt)
            true
        }

        val job = scope.launch {
            flow.collect {  }
        }

        subscriptions[groupKey] = Subscription(topics = topicList, job = job)
        _activeSubscriptionCount.value = subscriptions.size



        return _events.filter { envelope ->
            topicList.any { topic -> matchesTopic(envelope.topic, topic) }
        }
    }


    fun unsubscribe(groupKey: String) {
        subscriptions.remove(groupKey)?.job?.cancel()
        _activeSubscriptionCount.value = subscriptions.size
    }


    fun shutdown() {
        subscriptions.values.forEach { it.job.cancel() }
        subscriptions.clear()
        _activeSubscriptionCount.value = 0
        scope.cancel()
    }

    private fun buildSubscribeRequest(topics: List<String>): ByteArray {
        val requestId = UUID.randomUUID().toString()
        val protoSubscribe = com.jlucraft.control.v1.SubscribeEventsRequest.newBuilder()
            .addAllTopics(topics)
            .build()
        val controlRequest = com.jlucraft.control.v1.ControlRequest.newBuilder()
            .setRequestId(requestId)
            .setSubscribeEvents(protoSubscribe)
            .build()
        return controlRequest.toByteArray()
    }

    private fun parseEventEnvelope(bytes: ByteArray): EventEnvelope? {
        return runCatching {
            val proto = com.jlucraft.events.v1.EventEnvelope.parseFrom(bytes)
            EventEnvelope(
                topic = proto.topic,
                eventType = proto.eventType,
                timestamp = proto.occurredAt,
                payload = bytes,
            )
        }.getOrNull()
    }

    private fun matchesTopic(actualTopic: String, filter: String): Boolean {
        return when {
            filter.endsWith(".*") -> {
                val prefix = filter.removeSuffix(".*")
                actualTopic.startsWith(prefix)
            }
            filter.endsWith(">") -> {

                actualTopic.startsWith(filter.dropLast(1))
            }
            else -> actualTopic == filter
        }
    }

    private suspend fun calculateBackoff(attempt: Long) {
        val maxAttempt = attempt.coerceAtMost(6).toInt()
        val seconds = (1L shl maxAttempt).coerceAtMost(60)
        kotlinx.coroutines.delay(seconds * 1000)
    }

    private data class Subscription(
        val topics: List<String>,
        val job: Job,
    )
}


 *
data class EventEnvelope(
    val topic: String,
    val eventType: String,
    val timestamp: String,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventEnvelope) return false
        return topic == other.topic &&
            eventType == other.eventType &&
            timestamp == other.timestamp &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + eventType.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

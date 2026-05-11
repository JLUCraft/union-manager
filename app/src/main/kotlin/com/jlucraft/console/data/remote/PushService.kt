package com.jlucraft.console.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.jlucraft.console.data.model.PushEventPayload

/**
 * UnifiedPush-only event bus.
 *
 * Forwards push events from [UnionPushReceiver] to consumers (ViewModels etc.).
 * UnifiedPush is the sole push channel. `connect()`/`disconnect()` forward
 * events from [UnionPushReceiver] without performing network operations.
 */
class PushService {
    private val _events = MutableSharedFlow<PushEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<PushEvent> = _events.asSharedFlow()

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    /**
     * Start collecting UnifiedPush events from [UnionPushReceiver.pushEvents].
     * Idempotent: if already collecting, this is a no-op.
     */
    fun connect() {
        if (job?.isActive == true) return
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        job = newScope.launch {
            UnionPushReceiver.pushEvents.collect { event ->
                _events.emit(event)
            }
        }
    }

    /** Stop collecting UnifiedPush events. */
    fun disconnect() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
    }

    data class PushEvent(
        val type: String,
        val data: PushEventPayload
    )
}

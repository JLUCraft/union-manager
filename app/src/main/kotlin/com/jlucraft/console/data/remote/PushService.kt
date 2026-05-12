package com.jlucraft.console.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.jlucraft.console.data.model.PushEventPayload


 *
class PushService {
    private val _events = MutableSharedFlow<PushEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<PushEvent> = _events.asSharedFlow()

    private var job: Job? = null
    private var scope: CoroutineScope? = null


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

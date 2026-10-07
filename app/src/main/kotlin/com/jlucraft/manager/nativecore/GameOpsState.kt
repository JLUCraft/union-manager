package com.jlucraft.manager.nativecore

internal data class GameOpsTarget(val address: String = "", val peer: String = "", val service: String = "") {
    val complete: Boolean get() = listOf(address, peer, service).all { it.isNotBlank() }
}

// Presentation data only. The operations schema lives in the union-core protocol crate.
internal data class GameInstance(
    val id: String, val state: String, val version: String,
    val online: Int, val maximum: Int,
)

internal data class GameOpsResult(
    val ok: Boolean, val instances: List<GameInstance>, val logs: String? = null,
)

internal data class GameOpsState(
    val instances: List<GameInstance> = emptyList(),
    val selected: String = "",
    val logs: String? = null,
    val failed: Boolean = false,
) {
    fun select(id: String): GameOpsState =
        if (instances.any { it.id == id }) copy(selected = id, logs = null) else this

    fun receive(operation: String, result: GameOpsResult): GameOpsState {
        if (!result.ok) return copy(logs = null, failed = true)
        val updates = result.instances.associateBy { it.id }
        val next = if (operation == "list_instances") result.instances else instances.map { updates[it.id] ?: it }
        return copy(instances = next, selected = selected.takeIf { id -> next.any { it.id == id } } ?: "",
            logs = result.logs, failed = false)
    }
}


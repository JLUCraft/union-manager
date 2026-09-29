package com.jlucraft.manager.nativecore

import org.json.JSONArray
import org.json.JSONObject

internal fun gameOpsRequest(target: GameOpsTarget, operation: String, instance: String): JSONObject {
    require(target.complete)
    require(operation in setOf("list_instances", "status", "start", "stop", "restart", "kill", "logs"))
    val op = JSONObject().put("op", operation)
    if (operation != "list_instances") {
        require(instance.isNotBlank())
        op.put("instance", instance)
    }
    return JSONObject().put("address", target.address.trim()).put("peer", target.peer.trim())
        .put("service", target.service.trim())
        .put("request", JSONObject().put("op", op))
}

internal fun gameOpsResult(value: JSONObject): GameOpsResult {
    val ok = value.getBoolean("ok")
    if (!ok) return GameOpsResult(false, emptyList())
    val entries = value.optJSONArray("instances")
    val instances = if (entries == null) emptyList() else (0 until entries.length()).map { index ->
        val instance = entries.getJSONObject(index)
        val id = instance.getString("id")
        val online = instance.getInt("players_online")
        val maximum = instance.getInt("players_max")
        require(id.isNotBlank() && online >= 0 && maximum >= 0)
        GameInstance(id, instance.getString("state"), instance.getString("version"), online, maximum)
    }
    require(instances.map { it.id }.distinct().size == instances.size)
    return GameOpsResult(true, instances, if (value.isNull("logs")) null else value.getString("logs"))
}

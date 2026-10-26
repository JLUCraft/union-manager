package com.jlucraft.manager.nativecore

import org.json.JSONArray
import org.json.JSONObject

/** Only peers from a previously verified consensus snapshot are candidates. */
object ApplicationConnection {
    fun execute(secret: ByteArray, input: JSONObject, replicas: JSONObject?): JSONObject {
        val candidates = mutableListOf(input.getString("peer") to input.getString("address"))
        replicas?.keys()?.asSequence()?.toList()?.sorted()?.forEach { id ->
            val replica = replicas.getJSONObject(id)
            val peer = replica.getString("peer")
            val addresses = replica.optJSONArray("addresses") ?: JSONArray()
            for (i in 0 until addresses.length()) {
                val address = addresses.getString(i).let { if (it.endsWith("/p2p/$peer")) it else "$it/p2p/$peer" }
                if ((peer to address) !in candidates) candidates.add(peer to address)
            }
        }
        var failure = JSONObject().put("error", "Application authority unavailable").put("error_code", "authority_unavailable")
        for ((peer, address) in candidates) {
            val probe = JSONObject(input.toString()).put("peer", peer).put("address", address)
            probe.remove("action")
            val response = JSONObject(UnionNative.execute(secret, probe.toString()))
            if (!response.isNull("error")) { failure = response; continue }
            // Writes are attempted once, after selecting a reachable replica. A lost
            // write response must not silently replay the user's business action.
            val result = if (input.has("action")) JSONObject(UnionNative.execute(secret,
                JSONObject(input.toString()).put("peer", peer).put("address", address).toString())) else response
            return result.put("application_peer", peer).put("application_address", address)
        }
        return failure
    }
}

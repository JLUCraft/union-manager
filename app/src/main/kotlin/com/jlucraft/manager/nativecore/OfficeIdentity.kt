package com.jlucraft.manager.nativecore

import org.json.JSONObject

internal fun officeMemberId(state: JSONObject?, device: String): String? {
    if (device.isBlank()) return null
    val members = state?.optJSONObject("members") ?: return null
    return members.keys().asSequence().firstOrNull { id ->
        val member = members.getJSONObject(id)
        member.optString("device") == device || member.optJSONArray("additional_devices").containsString(device)
    }
}

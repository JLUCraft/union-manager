package com.jlucraft.manager.nativecore

import org.json.JSONArray

internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

internal fun JSONArray?.containsString(value: String?): Boolean =
    this != null && (0 until length()).any { optString(it) == value }

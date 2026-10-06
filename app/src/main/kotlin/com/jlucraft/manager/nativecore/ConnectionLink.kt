package com.jlucraft.manager.nativecore

import java.net.URI
import java.net.URLDecoder

data class ConnectionTarget(val address: String, val peer: String, val genesisHash: String)

internal const val CONNECTION_LINK_SCHEME = "folly"
internal val GENESIS_HASH_PATTERN = Regex("[a-f0-9]{64}")
private const val CONNECTION_LINK_HOST = "federation"

/** Parses `folly://federation?address=…&peer=…&genesis=…`, the league link an organizer hands to members. */
internal fun parseConnectionLink(text: String): ConnectionTarget? = runCatching {
    val uri = URI(text.trim())
    if (uri.scheme != CONNECTION_LINK_SCHEME || uri.rawAuthority != CONNECTION_LINK_HOST ||
        (uri.rawPath != "" && uri.rawPath != "/")
    ) return@runCatching null
    val query = mutableMapOf<String, String>()
    uri.rawQuery.orEmpty().split('&').forEach {
        val pair = it.split('=', limit = 2)
        val key = URLDecoder.decode(pair[0], Charsets.UTF_8)
        // Match URLSearchParams.get: decode names and retain the first value.
        query.putIfAbsent(key, URLDecoder.decode(pair.getOrElse(1) { "" }, Charsets.UTF_8))
    }
    val address = query["address"]?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val peer = query["peer"]?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val genesis = query["genesis"]?.takeIf { GENESIS_HASH_PATTERN.matches(it) } ?: return@runCatching null
    ConnectionTarget(address, peer, genesis)
}.getOrNull()

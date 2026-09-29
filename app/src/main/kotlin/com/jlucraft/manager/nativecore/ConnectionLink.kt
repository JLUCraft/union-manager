package com.jlucraft.manager.nativecore

import java.net.URI
import java.net.URLDecoder

data class ConnectionTarget(val address: String, val peer: String, val genesisHash: String)

internal const val CONNECTION_LINK_SCHEME = "folly"
internal val GENESIS_HASH_PATTERN = Regex("[a-f0-9]{64}")
private const val CONNECTION_LINK_HOST = "federation"

/** Parses `folly://federation?address=…&peer=…&genesis=…`, the link an organizer hands to members. */
internal fun parseConnectionLink(text: String): ConnectionTarget? = runCatching {
    val uri = URI(text.trim())
    if (uri.scheme != CONNECTION_LINK_SCHEME || uri.host != CONNECTION_LINK_HOST) return@runCatching null
    val query = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
        val (key, value) = it.split('=', limit = 2)
        key to URLDecoder.decode(value, Charsets.UTF_8)
    }
    val address = query["address"]?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val peer = query["peer"]?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val genesis = query["genesis"]?.takeIf { GENESIS_HASH_PATTERN.matches(it) } ?: return@runCatching null
    ConnectionTarget(address, peer, genesis)
}.getOrNull()

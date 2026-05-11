package com.jlucraft.console.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Lightweight local revocation cache.
 *
 * Before performing any sensitive operation (instance stop/delete/migrate,
 * credential issuance, device revocation), the coordinator checks this cache
 * to reject known-revoked devices without a server round-trip.
 *
 * The cache is periodically refreshed from the server via [refresh].
 * Entries older than [ttlMillis] are evicted automatically.
 *
 * Thread-safe via [mutex] and persistent via DataStore (Preferences).
 */
object RevocationChecker {
    private const val DATASTORE_NAME = "revocation_cache"
    private val Context.revocationStore by preferencesDataStore(name = DATASTORE_NAME)

    private val json = Json { ignoreUnknownKeys = true }

    /** TTL for cache entries: 5 minutes. */
    private const val DEFAULT_TTL_MILLIS = 5 * 60 * 1000L

    /** Key for storing the serialized revocation list in DataStore. */
    private val REVOKED_DEVICES_KEY = stringPreferencesKey("revoked_devices")
    private val LAST_REFRESH_KEY = longPreferencesKey("last_refresh_ms")

    private val mutex = Mutex()

    @Volatile
    private var cache: RevokedDeviceCache = RevokedDeviceCache()

    /** Check if a device by [pubkey] is currently revoked (cache hit). */
    suspend fun isRevoked(context: Context, pubkey: String): Boolean {
        loadIfNeeded(context)
        return mutex.withLock {
            val entry = cache.entries[pubkey] ?: return@withLock false
            if (isExpired(entry, DEFAULT_TTL_MILLIS)) {
                cache = cache.copy(entries = cache.entries - pubkey)
                false
            } else {
                entry.status == DeviceStatus.REVOKED
            }
        }
    }

    /**
     * Check and reject if revoked. Returns [Result.success] if allowed,
     * or [Result.failure] with a descriptive message if the device is revoked.
     */
    suspend fun assertNotRevoked(context: Context, pubkey: String, operation: String): Result<Unit> {
        return if (isRevoked(context, pubkey)) {
            Result.failure(Exception("设备已被吊销，无法执行: $operation"))
        } else {
            Result.success(Unit)
        }
    }

    /**
     * Merge the server's revocation list into the local cache.
     *
     * This is an additive merge: entries in the server list that are not yet in the cache
     * are added. Existing entries are NOT removed — the server may return a partial list
     * (e.g. only recently revoked devices). Entries are naturally evicted by TTL.
     *
     * Call this periodically (e.g. on app foreground, before sensitive screens).
     */
    suspend fun refresh(context: Context, revokedPubkeys: List<String>, revokedAt: Long = System.currentTimeMillis()) {
        mutex.withLock {
            val updatedEntries = cache.entries.toMutableMap()
            for (pubkey in revokedPubkeys) {
                updatedEntries[pubkey] = RevokedDeviceEntry(
                    pubkey = pubkey,
                    status = DeviceStatus.REVOKED,
                    cachedAtMs = revokedAt,
                    reason = null
                )
            }
            cache = RevokedDeviceCache(
                entries = updatedEntries,
                lastRefreshMs = revokedAt
            )
            persist(context)
        }
    }

    /**
     * Clear the in-memory cache. Does not affect persistent storage.
     * Thread-safe via mutex.
     */
    suspend fun clearMemory() {
        mutex.withLock {
            cache = RevokedDeviceCache()
        }
    }

    // ── Private helpers ──

    private fun isExpired(entry: RevokedDeviceEntry, ttlMillis: Long): Boolean {
        return (System.currentTimeMillis() - entry.cachedAtMs) > ttlMillis
    }

    private suspend fun persist(context: Context) {
        try {
            val serialized = json.encodeToString(cache)
            context.revocationStore.edit { prefs ->
                prefs[REVOKED_DEVICES_KEY] = serialized
                prefs[LAST_REFRESH_KEY] = cache.lastRefreshMs
            }
        } catch (_: Exception) {
            // Best-effort persistence; in-memory cache still works
        }
    }

    private suspend fun loadIfNeeded(context: Context) {
        if (cache.entries.isNotEmpty()) return
        mutex.withLock {
            if (cache.entries.isNotEmpty()) return@withLock
            try {
                val serialized = context.revocationStore.data.map { prefs ->
                    prefs[REVOKED_DEVICES_KEY]
                }.first()
                if (serialized != null) {
                    cache = json.decodeFromString<RevokedDeviceCache>(serialized)
                }
            } catch (_: Exception) {
                cache = RevokedDeviceCache()
            }
        }
    }
}

@Serializable
data class RevokedDeviceCache(
    val entries: Map<String, RevokedDeviceEntry> = emptyMap(),
    val lastRefreshMs: Long = 0L
)

@Serializable
data class RevokedDeviceEntry(
    val pubkey: String,
    val status: DeviceStatus,
    val cachedAtMs: Long,
    val reason: String? = null
)

@Serializable
enum class DeviceStatus {
    ACTIVE, REVOKED
}

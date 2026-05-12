package com.jlucraft.console.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


 *
 *
class PushRuntimeConfigStore(private val context: Context) {
    private val Context.dataStore by preferencesDataStore(name = "push_runtime_config")

    @Volatile
    private var cachedVapidPublicKey: String? = null


    suspend fun getVapidPublicKey(): String? {
        cachedVapidPublicKey?.let { return it }
        val stored = context.dataStore.data.map { prefs ->
            prefs[KEY_VAPID_PUBLIC_KEY]
        }.first()
        cachedVapidPublicKey = stored
        return stored
    }


    suspend fun setVapidPublicKey(key: String) {
        cachedVapidPublicKey = key
        context.dataStore.edit { prefs ->
            prefs[KEY_VAPID_PUBLIC_KEY] = key
        }
    }


    suspend fun refreshCache() {
        cachedVapidPublicKey = context.dataStore.data.map { prefs ->
            prefs[KEY_VAPID_PUBLIC_KEY]
        }.first()
    }

    companion object {
        private val KEY_VAPID_PUBLIC_KEY = stringPreferencesKey("vapid_public_key")
    }
}

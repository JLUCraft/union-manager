package com.jlucraft.console.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    @Volatile
    var currentServerUrl: String = DEFAULT_SERVER_URL
        private set

    val serverUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    suspend fun setServerUrl(url: String) {
        val normalized = url.removeSuffix("/")
        dataStore.edit { it[SERVER_URL] = normalized }
        currentServerUrl = normalized
    }

    suspend fun readServerUrl(): String {
        val value = serverUrlFlow.first()
        currentServerUrl = value
        return value
    }

    companion object {
        val DEFAULT_SERVER_URL: String = com.jlucraft.console.BuildConfig.DEFAULT_SERVER_URL
        private val SERVER_URL = stringPreferencesKey("server_url")
    }
}

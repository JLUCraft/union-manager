package com.jlucraft.console.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore



    val onboardingCompletedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return onboardingCompletedFlow.first()
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }




    val pushEnabledEventTypesFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PUSH_ENABLED_EVENT_TYPES]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: DEFAULT_ENABLED_EVENT_TYPES
    }

    suspend fun getPushEnabledEventTypes(): Set<String> {
        return pushEnabledEventTypesFlow.first()
    }

    suspend fun setPushEnabledEventTypes(types: Set<String>) {
        dataStore.edit { it[PUSH_ENABLED_EVENT_TYPES] = types.joinToString(",") }
    }


    val dndStartHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[DND_START_HOUR] ?: 22
    }

    suspend fun getDndStartHour(): Int = dndStartHourFlow.first()

    suspend fun setDndStartHour(hour: Int) {
        dataStore.edit { it[DND_START_HOUR] = hour.coerceIn(0, 23) }
    }


    val dndEndHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[DND_END_HOUR] ?: 7
    }

    suspend fun getDndEndHour(): Int = dndEndHourFlow.first()

    suspend fun setDndEndHour(hour: Int) {
        dataStore.edit { it[DND_END_HOUR] = hour.coerceIn(0, 23) }
    }


    val dndEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DND_ENABLED] ?: false
    }

    suspend fun isDndEnabled(): Boolean = dndEnabledFlow.first()

    suspend fun setDndEnabled(enabled: Boolean) {
        dataStore.edit { it[DND_ENABLED] = enabled }
    }



    suspend fun getLibp2pPrivateKey(): String? =
        dataStore.data.map { it[LIBP2P_PRIVATE_KEY] }.first()

    suspend fun setLibp2pPrivateKey(base64Key: String) {
        dataStore.edit { it[LIBP2P_PRIVATE_KEY] = base64Key }
    }



    suspend fun getBootstrapPeers(): List<String> =
        dataStore.data.map { prefs ->
            prefs[LIBP2P_BOOTSTRAP_PEERS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        }.first()

    suspend fun setBootstrapPeers(peers: List<String>) {
        dataStore.edit { it[LIBP2P_BOOTSTRAP_PEERS] = peers.joinToString("\n") }
    }

    companion object {
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val PUSH_ENABLED_EVENT_TYPES = stringPreferencesKey("push_enabled_event_types")
        private val DND_START_HOUR = intPreferencesKey("dnd_start_hour")
        private val DND_END_HOUR = intPreferencesKey("dnd_end_hour")
        private val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
        private val LIBP2P_PRIVATE_KEY = stringPreferencesKey("libp2p_private_key")
        private val LIBP2P_BOOTSTRAP_PEERS = stringPreferencesKey("libp2p_bootstrap_peers")


        val FORCE_ENABLED_EVENT_TYPES = setOf("AuthChallenge", "InstanceCrash")

        val DEFAULT_ENABLED_EVENT_TYPES = setOf(
            "InstanceCrash", "AuthChallenge", "InstanceMigrated",
            "ProposalCreated", "ProposalExecuted", "TournamentCreated",
            "MatchResult"
        )
    }
}

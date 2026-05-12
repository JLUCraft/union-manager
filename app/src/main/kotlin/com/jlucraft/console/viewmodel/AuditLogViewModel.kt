package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.model.AuditAnomaly
import com.jlucraft.console.data.model.AuditChainVerification
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Calendar

data class AuditLogUiState(
    val isLoading: Boolean = false,
    val entries: List<AuditEntry> = emptyList(),
    val chainVerification: AuditChainVerification? = null,
    val anomalies: List<AuditAnomaly> = emptyList(),
    val localAnomalies: List<LocalAnomaly> = emptyList(),
    val error: String? = null,

    val cmdTypeFilter: String? = null,
    val timeRangeStart: Long? = null,
    val timeRangeEnd: Long? = null,
    val triggerFilter: String? = null,
    val targetFilter: String? = null,
    val resultFilter: String? = null,
    val searchText: String? = null,
    val filteredEntries: List<AuditEntry> = emptyList(),
    val isFiltered: Boolean = false,
    val selectedEntry: AuditEntry? = null
)

data class LocalAnomaly(
    val type: String,
    val severity: AnomalySeverity = AnomalySeverity.WARNING,
    val description: String,
    val entryId: Long,
    val detail: Map<String, String> = emptyMap()
)

enum class AnomalySeverity(val label: String) {
    INFO("信息"),
    WARNING("警告"),
    CRITICAL("严重")
}

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val client: Libp2pClient,
    pushService: PushService,
) : ViewModel() {

    private val _uiState = mutableStateOf(AuditLogUiState())
    val uiState: State<AuditLogUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            pushService.events.collect { event ->
                when (event.type) {
                    "audit_entry", "audit_created" -> refresh()
                }
            }
        }
    }



    fun setCmdTypeFilter(cmdType: String?) {
        _uiState.value = _uiState.value.copy(cmdTypeFilter = cmdType)
    }

    fun setTimeRange(start: Long?, end: Long?) {
        _uiState.value = _uiState.value.copy(
            timeRangeStart = start,
            timeRangeEnd = end
        )
    }

    fun setTriggerFilter(trigger: String?) {
        _uiState.value = _uiState.value.copy(triggerFilter = trigger)
    }

    fun setTargetFilter(target: String?) {
        _uiState.value = _uiState.value.copy(targetFilter = target)
    }

    fun setResultFilter(result: String?) {
        _uiState.value = _uiState.value.copy(resultFilter = result)
    }

    fun setSearchText(text: String?) {
        _uiState.value = _uiState.value.copy(searchText = text)
    }



    fun applyFilters() {
        val state = _uiState.value
        val filtered = computeFilteredEntries(state)
        val hasActiveFilter = state.timeRangeStart != null ||
            state.timeRangeEnd != null ||
            !state.cmdTypeFilter.isNullOrBlank() ||
            !state.triggerFilter.isNullOrBlank() ||
            !state.targetFilter.isNullOrBlank() ||
            (state.resultFilter != null && state.resultFilter != "all") ||
            !state.searchText.isNullOrBlank()

        _uiState.value = state.copy(
            filteredEntries = filtered,
            isFiltered = hasActiveFilter
        )
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            cmdTypeFilter = null,
            timeRangeStart = null,
            timeRangeEnd = null,
            triggerFilter = null,
            targetFilter = null,
            resultFilter = null,
            searchText = null,
            filteredEntries = emptyList(),
            isFiltered = false
        )
    }



    fun selectEntry(entry: AuditEntry?) {
        _uiState.value = _uiState.value.copy(selectedEntry = entry)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedEntry = null)
    }



    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val entriesDeferred = async {
                client.listAuditEntries(cmdType = _uiState.value.cmdTypeFilter, limit = 100)
            }
            val verifyDeferred = async { client.verifyAuditChain() }
            val anomaliesDeferred = async {
                runCatching { client.getAuditAnomalies() }
                    .getOrElse { Result.failure(it) }
            }

            val entriesResult = entriesDeferred.await()
            val verifyResult = verifyDeferred.await()
            val anomaliesResult = anomaliesDeferred.await()

            val entries = entriesResult.getOrNull() ?: emptyList()
            val localAnomalies = detectLocalAnomalies(entries)

            val currentState = _uiState.value
            val wasFiltered = currentState.isFiltered

            _uiState.value = currentState.copy(
                isLoading = false,
                entries = entries,
                error = entriesResult.exceptionOrNull()?.message,
                chainVerification = verifyResult.getOrNull(),
                anomalies = anomaliesResult.getOrNull() ?: emptyList(),
                localAnomalies = localAnomalies,
                filteredEntries = if (wasFiltered)
                    computeFilteredEntries(currentState.copy(entries = entries))
                else
                    entries
            )
        }
    }



    private fun computeFilteredEntries(state: AuditLogUiState): List<AuditEntry> {
        var filtered = state.entries

        state.timeRangeStart?.let { start ->
            filtered = filtered.filter { it.ts >= start }
        }
        state.timeRangeEnd?.let { end ->
            filtered = filtered.filter { it.ts <= end }
        }
        state.cmdTypeFilter?.let { cmdType ->
            if (cmdType.isNotBlank()) {
                filtered = filtered.filter { it.cmdType.contains(cmdType, ignoreCase = true) }
            }
        }
        state.triggerFilter?.let { trigger ->
            if (trigger.isNotBlank()) {
                filtered = filtered.filter { it.actorPubkey.contains(trigger, ignoreCase = true) }
            }
        }
        state.targetFilter?.let { target ->
            if (target.isNotBlank()) {
                filtered = filtered.filter { it.target.contains(target, ignoreCase = true) }
            }
        }
        state.resultFilter?.let { result ->
            if (result.isNotBlank() && result != "all") {
                filtered = filtered.filter { it.outcome == result }
            }
        }
        state.searchText?.let { text ->
            if (text.isNotBlank()) {
                filtered = filtered.filter { entry ->
                    entry.cmdType.contains(text, ignoreCase = true) ||
                        entry.target.contains(text, ignoreCase = true) ||
                        entry.actorPubkey.contains(text, ignoreCase = true) ||
                        (entry.error?.contains(text, ignoreCase = true) == true)
                }
            }
        }

        return filtered
    }



    private fun detectLocalAnomalies(entries: List<AuditEntry>): List<LocalAnomaly> {
        if (entries.size < 2) return emptyList()
        val anomalies = mutableListOf<LocalAnomaly>()
        val sortedEntries = entries.sortedBy { it.id }

        anomalies.addAll(detectHashChainBreaks(sortedEntries))
        anomalies.addAll(detectOutOfOrderTimestamps(sortedEntries))
        anomalies.addAll(detectDuplicateOperations(sortedEntries))
        anomalies.addAll(detectHighFrequency(sortedEntries))
        anomalies.addAll(detectLateNightOps(sortedEntries))
        anomalies.addAll(detectSameTargetAbuse(sortedEntries))
        anomalies.addAll(detectHighFailureRate(sortedEntries))

        return anomalies
    }

    private fun detectHashChainBreaks(entries: List<AuditEntry>): List<LocalAnomaly> {
        val anomalies = mutableListOf<LocalAnomaly>()
        val knownHashes = entries.map { it.payloadHash }.toSet()

        for (entry in entries) {
            if (entry.prevHash.isNotEmpty() && entry.prevHash !in knownHashes) {
                anomalies.add(
                    LocalAnomaly(
                        type = "prev_hash_unknown",
                        severity = AnomalySeverity.INFO,
                        description = "Entry #${entry.id}: prev_hash references unknown predecessor",
                        entryId = entry.id,
                        detail = mapOf(
                            "prev_hash" to entry.prevHash.take(16) + "...",
                            "actor" to entry.actorPubkey.take(12) + "..."
                        )
                    )
                )
            }
        }
        return anomalies
    }

    private fun detectOutOfOrderTimestamps(entries: List<AuditEntry>): List<LocalAnomaly> {
        val anomalies = mutableListOf<LocalAnomaly>()
        var prevTs = 0L
        for (entry in entries) {
            if (entry.ts < prevTs) {
                anomalies.add(
                    LocalAnomaly(
                        type = "timestamp_out_of_order",
                        severity = AnomalySeverity.WARNING,
                        description = "Entry #${entry.id} has timestamp ${entry.ts} before previous ${prevTs}",
                        entryId = entry.id
                    )
                )
            }
            prevTs = entry.ts
        }
        return anomalies
    }

    private fun detectDuplicateOperations(entries: List<AuditEntry>): List<LocalAnomaly> {
        val anomalies = mutableListOf<LocalAnomaly>()
        val seen = mutableMapOf<String, AuditEntry>()
        for (entry in entries) {
            val key = "${entry.actorPubkey}|${entry.cmdType}|${entry.target}"
            val prev = seen[key]
            if (prev != null && (entry.ts - prev.ts) < 5000) {
                anomalies.add(
                    LocalAnomaly(
                        type = "duplicate_operation",
                        severity = AnomalySeverity.WARNING,
                        description = "Duplicate: #${prev.id} and #${entry.id} (${entry.cmdType} on ${entry.target})",
                        entryId = entry.id
                    )
                )
            }
            seen[key] = entry
        }
        return anomalies
    }

    private fun detectHighFrequency(entries: List<AuditEntry>): List<LocalAnomaly> {
        val anomalies = mutableListOf<LocalAnomaly>()
        val WINDOW_MS = 60_000L
        val THRESHOLD = 10

        val byActor = entries.filter { it.ts > 0 }.groupBy { it.actorPubkey }
        for ((actor, actorEntries) in byActor) {
            if (actorEntries.size < THRESHOLD) continue
            val sorted = actorEntries.sortedBy { it.ts }
            for (i in 0..sorted.size - THRESHOLD) {
                val windowEnd = i + THRESHOLD - 1
                if (windowEnd < sorted.size) {
                    val duration = sorted[windowEnd].ts - sorted[i].ts
                    if (duration <= WINDOW_MS) {
                        anomalies.add(
                            LocalAnomaly(
                                type = "high_frequency",
                                severity = AnomalySeverity.CRITICAL,
                                description = "Actor ${actor.take(12)}... performed ${THRESHOLD}+ ops in ${duration}ms",
                                entryId = sorted[windowEnd].id,
                                detail = mapOf(
                                    "actor" to actor.take(16) + "...",
                                    "ops_in_window" to THRESHOLD.toString(),
                                    "window_ms" to duration.toString()
                                )
                            )
                        )
                        break
                    }
                }
            }
        }
        return anomalies
    }

    private fun detectLateNightOps(entries: List<AuditEntry>): List<LocalAnomaly> {
        val anomalies = mutableListOf<LocalAnomaly>()
        val lateNightStart = 22
        val lateNightEnd = 5

        for (entry in entries) {
            if (entry.ts <= 0) continue
            val cal = Calendar.getInstance().apply { timeInMillis = entry.ts }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val isLateNight = if (lateNightStart > lateNightEnd) {
                hour >= lateNightStart || hour < lateNightEnd
            } else {
                hour in lateNightStart until lateNightEnd
            }
            if (isLateNight && entry.cmdType in SUSPICIOUS_LATE_NIGHT_CMDS) {
                anomalies.add(
                    LocalAnomaly(
                        type = "late_night_ops",
                        severity = AnomalySeverity.WARNING,
                        description = "Late-night ${entry.cmdType} by ${entry.actorPubkey.take(12)}... at hour $hour",
                        entryId = entry.id,
                        detail = mapOf(
                            "hour" to hour.toString(),
                            "cmd_type" to entry.cmdType
                        )
                    )
                )
            }
        }
        return anomalies
    }

    private fun detectSameTargetAbuse(entries: List<AuditEntry>): List<LocalAnomaly> {
        val anomalies = mutableListOf<LocalAnomaly>()
        val THRESHOLD = 5
        val WINDOW_MS = 300_000L

        val byTarget = entries.filter { it.target.isNotEmpty() }.groupBy { it.target }
        for ((target, targetEntries) in byTarget) {
            if (targetEntries.size < THRESHOLD) continue
            val sorted = targetEntries.sortedBy { it.ts }
            val uniqueActors = sorted.map { it.actorPubkey }.toSet()
            if (uniqueActors.size >= 2) {
                val windowEnd = sorted.last().ts
                val windowStart = sorted.first().ts
                if (windowEnd - windowStart <= WINDOW_MS) {
                    anomalies.add(
                        LocalAnomaly(
                            type = "same_target_abuse",
                            severity = AnomalySeverity.CRITICAL,
                            description = "Target $target received ${sorted.size} ops from ${uniqueActors.size} actors in ${(windowEnd - windowStart) / 1000}s",
                            entryId = sorted.last().id,
                            detail = mapOf(
                                "target" to target.take(24),
                                "actor_count" to uniqueActors.size.toString(),
                                "op_count" to sorted.size.toString()
                            )
                        )
                    )
                }
            }
        }
        return anomalies
    }

    private fun detectHighFailureRate(entries: List<AuditEntry>): List<LocalAnomaly> {
        val anomalies = mutableListOf<LocalAnomaly>()
        val FAILURE_THRESHOLD_RATIO = 0.5f
        val MIN_OPS = 5

        val byActor = entries.groupBy { it.actorPubkey }
        for ((actor, actorEntries) in byActor) {
            if (actorEntries.size < MIN_OPS) continue
            val failures = actorEntries.count { it.outcome == "rejected" || it.outcome == "error" }
            val ratio = failures.toFloat() / actorEntries.size
            if (ratio >= FAILURE_THRESHOLD_RATIO) {
                anomalies.add(
                    LocalAnomaly(
                        type = "high_failure_rate",
                        severity = AnomalySeverity.WARNING,
                        description = "Actor ${actor.take(12)}... has ${(ratio * 100).toInt()}% failure rate (${failures}/${actorEntries.size})",
                        entryId = actorEntries.last().id,
                        detail = mapOf(
                            "actor" to actor.take(16) + "...",
                            "failures" to failures.toString(),
                            "total" to actorEntries.size.toString()
                        )
                    )
                )
            }
        }
        return anomalies
    }

    companion object {
        private val SUSPICIOUS_LATE_NIGHT_CMDS = setOf(
            "delete-instance", "revoke-device", "emergency-revoke-device",
            "revoke-credential", "execute-proposal", "archive-season",
            "stop-instance"
        )
    }
}

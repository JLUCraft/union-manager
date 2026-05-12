package com.jlucraft.console.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant


 *
object AuthStateHolder {


    var sessionDurationMinutes: Long = 15


    var challengeTtlSeconds: Long = 60

    private val _state = MutableStateFlow<AppAuthState>(AppAuthState.Locked)
    val state: StateFlow<AppAuthState> = _state.asStateFlow()


    private val _readOnlyMode = MutableStateFlow<ReadOnlyMode>(
        ReadOnlyMode.ReadOnly("TEE 状态检测中...")
    )
    val readOnlyMode: StateFlow<ReadOnlyMode> = _readOnlyMode.asStateFlow()


     *
    fun setReadOnlyModeFrom(teeAuth: TeeAuthManager) {
        _readOnlyMode.value = if (teeAuth.isTeeBacked) {
            ReadOnlyMode.ReadWrite
        } else {
            val reason = (teeAuth.capability as? TeeCapability.NoHardwareBackedKey)?.reason
                ?: "TEE 不可用"
            ReadOnlyMode.ReadOnly(reason)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var expiryJob: Job? = null

    private fun scheduleStateExpiry(expiresAt: Instant) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            val delayMillis = java.time.Duration.between(Instant.now(), expiresAt).toMillis().coerceAtLeast(0)
            delay(delayMillis)
            when (val current = _state.value) {
                is AppAuthState.Unlocked -> if (!current.until.isAfter(Instant.now())) {
                    _state.value = AppAuthState.Locked
                }
                is AppAuthState.SignWindow -> if (!current.expiresAt.isAfter(Instant.now())) {
                    _state.value = AppAuthState.Locked
                }
                else -> Unit
            }
        }
    }


    fun startVerifying() {
        _state.value = AppAuthState.Verifying
    }


    fun onUnlockSuccess() {
        val until = Instant.now().plusSeconds(sessionDurationMinutes * 60)
        _state.value = AppAuthState.Unlocked(until)
        scheduleStateExpiry(until)
    }


    fun onVerificationFailed() {
        expiryJob?.cancel()
        _state.value = AppAuthState.Locked
    }


    fun openSignWindow(challengeId: String) {
        val expiresAt = Instant.now().plusSeconds(challengeTtlSeconds)
        _state.value = AppAuthState.SignWindow(challengeId, expiresAt)
        scheduleStateExpiry(expiresAt)
    }


    fun closeSignWindow() {
        expiryJob?.cancel()
        _state.value = AppAuthState.Locked
    }


    fun lock() {
        expiryJob?.cancel()
        _state.value = AppAuthState.Locked
    }


    fun resetReadOnlyMode() {
        _readOnlyMode.value = ReadOnlyMode.ReadOnly("TEE 状态检测中...")
    }


    fun isReadAllowed(): Boolean {
        return when (val s = _state.value) {
            is AppAuthState.Unlocked -> s.until.isAfter(Instant.now())
            is AppAuthState.SignWindow -> s.expiresAt.isAfter(Instant.now())
            else -> false
        }
    }


    fun isChallengeValid(): Boolean {
        return when (val s = _state.value) {
            is AppAuthState.SignWindow -> s.expiresAt.isAfter(Instant.now())
            else -> false
        }
    }


    fun currentChallengeId(): String? {
        return (_state.value as? AppAuthState.SignWindow)?.challengeId
    }
}

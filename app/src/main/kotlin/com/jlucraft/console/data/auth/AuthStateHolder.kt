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

/**
 * Shared observable holder for [AppAuthState].
 * Drives lock-screen, unlock duration, and challenge-signing window UI.
 *
 * Default unlock TTL: 15 minutes (design §4.3).
 * Challenge TTL: 60 seconds (design §1.2, §4.3).
 */
object AuthStateHolder {

    /** Session duration in minutes (configurable: 5, 15, 60). */
    var sessionDurationMinutes: Long = 15

    /** Challenge TTL in seconds. */
    var challengeTtlSeconds: Long = 60

    private val _state = MutableStateFlow<AppAuthState>(AppAuthState.Locked)
    val state: StateFlow<AppAuthState> = _state.asStateFlow()
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

    /** Initiate biometric verification to unlock read operations. */
    fun startVerifying() {
        _state.value = AppAuthState.Verifying
    }

    /** Called on successful biometric verification. */
    fun onUnlockSuccess() {
        val until = Instant.now().plusSeconds(sessionDurationMinutes * 60)
        _state.value = AppAuthState.Unlocked(until)
        scheduleStateExpiry(until)
    }

    /** Called on failed/cancelled verification. */
    fun onVerificationFailed() {
        expiryJob?.cancel()
        _state.value = AppAuthState.Locked
    }

    /** Open a challenge-signing window. */
    fun openSignWindow(challengeId: String) {
        val expiresAt = Instant.now().plusSeconds(challengeTtlSeconds)
        _state.value = AppAuthState.SignWindow(challengeId, expiresAt)
        scheduleStateExpiry(expiresAt)
    }

    /** Close the sign window (after sign success or expiry). */
    fun closeSignWindow() {
        expiryJob?.cancel()
        _state.value = AppAuthState.Locked
    }

    /** Force lock (e.g., after clearAuth or timeout). */
    fun lock() {
        expiryJob?.cancel()
        _state.value = AppAuthState.Locked
    }

    /** Check if current state allows read operations. */
    fun isReadAllowed(): Boolean {
        return when (val s = _state.value) {
            is AppAuthState.Unlocked -> s.until.isAfter(Instant.now())
            is AppAuthState.SignWindow -> s.expiresAt.isAfter(Instant.now())
            else -> false
        }
    }

    /** Check if a challenge still has valid TTL. */
    fun isChallengeValid(): Boolean {
        return when (val s = _state.value) {
            is AppAuthState.SignWindow -> s.expiresAt.isAfter(Instant.now())
            else -> false
        }
    }

    /** Get current challenge ID if in sign window. */
    fun currentChallengeId(): String? {
        return (_state.value as? AppAuthState.SignWindow)?.challengeId
    }
}

package com.jlucraft.console.data.auth

import java.time.Instant

/**
 * Shared auth state machine governing app-level authentication:
 * - [Locked]: app requires biometric unlock before read operations.
 * - [Verifying]: biometric prompt is in progress.
 * - [Unlocked]: read operations allowed; signature TTL tracked via [until].
 * - [SignWindow]: a challenge-signing window with [challengeId] and [expiresAt].
 */
sealed interface AppAuthState {
    data object Locked : AppAuthState
    data object Verifying : AppAuthState
    data class Unlocked(val until: Instant) : AppAuthState
    data class SignWindow(val challengeId: String, val expiresAt: Instant) : AppAuthState
}

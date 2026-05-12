package com.jlucraft.console.data.auth

import java.time.Instant


sealed interface AppAuthState {
    data object Locked : AppAuthState
    data object Verifying : AppAuthState
    data class Unlocked(val until: Instant) : AppAuthState
    data class SignWindow(val challengeId: String, val expiresAt: Instant) : AppAuthState
}

package com.jlucraft.console.data.auth

/** Represents the device's TEE/StrongBox capability tier for Ed25519 key operations. */
sealed interface TeeCapability {
    /** StrongBox-backed Ed25519 keys are available (highest security). */
    data object StrongBoxAvailable : TeeCapability

    /** Standard TEE-backed Ed25519 keys are available. */
    data object TeeOnlyAvailable : TeeCapability

    /** Keystore is available but the generated key is not hardware-backed. */
    data class NoHardwareBackedKey(val reason: String) : TeeCapability
}

package com.jlucraft.console.data.auth


sealed interface TeeCapability {

    data object StrongBoxAvailable : TeeCapability


    data object TeeOnlyAvailable : TeeCapability


    data class NoHardwareBackedKey(val reason: String) : TeeCapability
}

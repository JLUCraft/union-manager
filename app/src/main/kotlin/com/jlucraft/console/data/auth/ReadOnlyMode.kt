package com.jlucraft.console.data.auth


 *
 *
sealed interface ReadOnlyMode {

    data object ReadWrite : ReadOnlyMode


data class ReadOnly(val reason: String) : ReadOnlyMode
}

data class SignedDeviceMessage(
    val devicePublicKey: String,
    val signature: String
)

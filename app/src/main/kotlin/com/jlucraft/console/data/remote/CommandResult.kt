package com.jlucraft.console.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CommandResult(
    val result: String
)

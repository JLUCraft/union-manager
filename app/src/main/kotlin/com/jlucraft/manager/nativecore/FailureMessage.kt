package com.jlucraft.manager.nativecore

import com.jlucraft.manager.R

/** Message that tells the user the likely cause and next step, chosen from the native core's stable error codes. */
internal fun failureMessageRes(error: Exception): Int = when ((error as? NativeFailure)?.code) {
    "identity_unavailable", "authority_unavailable", "access_denied" -> R.string.identity_unavailable
    "network", "timeout" -> R.string.management_network
    "invalid_request", "not_found" -> R.string.management_invalid_request
    else -> R.string.management_failed
}

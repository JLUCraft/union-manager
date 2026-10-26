package com.jlucraft.manager.nativecore

internal enum class IdentityAvailability { Unconfigured, Syncing, Cached, Current, Unavailable }
internal data class MemberIdentityView(
    val memberId: String,
    val devices: Set<String>,
    val clubs: Set<String>,
    val chairOf: Set<String>,
    val permissions: Set<String>,
)
internal data class IdentityState(
    val availability: IdentityAvailability = IdentityAvailability.Unconfigured,
    val member: MemberIdentityView? = null,
) {
    fun syncing() = copy(availability = IdentityAvailability.Syncing)
    fun failed() = copy(availability = IdentityAvailability.Unavailable)
    fun received(member: MemberIdentityView?, current: Boolean = true) = IdentityState(if (current) IdentityAvailability.Current else IdentityAvailability.Cached, member)
}

/** Sync and inspect decide whether a verified identity still holds; record writes do not. */
internal fun isIdentityCheck(operation: String): Boolean =
    operation == "sync" || operation == "inspect"

/**
 * Only identity reads and the unsigned draft assembly may reuse the remembered session. Signing,
 * submission and any unknown operation authenticate on every attempt.
 */
internal fun usesRememberedSession(operation: String): Boolean =
    operation == "inspect" || operation == "sync" || operation == "draft"

/**
 * A failed record write keeps the prior verified state (and its signed draft); only a failed
 * identity check may mark the identity unavailable.
 */
internal fun identityStateAfterFailure(
    operation: String,
    current: IdentityState,
    previous: IdentityState,
): IdentityState = if (isIdentityCheck(operation)) current.failed() else previous

/**
 * One automatic sync per configuration/device scope. It starts after the first idle moment and
 * never retries when that sync fails; an explicit refresh remains available for the user.
 */
internal class IdentityAutoSync {
    private var started = false

    fun shouldStart(enabled: Boolean, idle: Boolean): Boolean {
        if (!enabled || !idle || started) return false
        started = true
        return true
    }
}

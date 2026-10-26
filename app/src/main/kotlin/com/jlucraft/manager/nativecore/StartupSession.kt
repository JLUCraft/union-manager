package com.jlucraft.manager.nativecore

/** Ordered launch gates: device unlock, identity, selected business connection, business pages. */
internal enum class StartupStage { Locked, Identity, Connection, Business }

/** Facts an async callback started with; anything else is stale after lock, configuration, or device change. */
internal data class StartupScope(val epoch: Long, val configuration: String, val device: String) {
    fun matches(epoch: Long, configuration: String, device: String): Boolean =
        this.epoch == epoch && this.configuration == configuration && this.device == device
}

/**
 * Identity scope plus the connection generation and application target.
 * A reply is current only while every captured value still matches; a cached snapshot for
 * another server never substitutes for the endpoint that was checked.
 */
internal data class ConnectionScope(
    val startup: StartupScope,
    val generation: Long,
    val target: ConnectionTarget,
) {
    fun matches(
        epoch: Long,
        configuration: String,
        device: String,
        generation: Long,
        target: ConnectionTarget,
    ): Boolean = startup.matches(epoch, configuration, device) &&
        this.generation == generation && this.target == target
}

/**
 * One automatic connection check per identity/configuration generation. A failure stays on the
 * connection step for an explicit retry; editing the target only invalidates old replies.
 */
internal class ConnectionAutoCheck {
    private var checkedGeneration: Long? = null

    fun shouldCheck(generation: Long, ready: Boolean): Boolean {
        if (!ready || checkedGeneration == generation) return false
        checkedGeneration = generation
        return true
    }

    /** A manual connection attempt also consumes the automatic attempt for this generation. */
    fun markAttempted(generation: Long) {
        checkedGeneration = generation
    }
}

/** Only a current ledger result bound to this device with at least one active club clears the identity gate. */
internal fun IdentityState.isVerifiedIdentity(): Boolean =
    availability == IdentityAvailability.Current && member != null &&
        member.memberId.isNotBlank() && member.clubs.isNotEmpty()

internal data class StartupSession(
    val stage: StartupStage = StartupStage.Locked,
    val identity: IdentityState = IdentityState(),
    val connectionVerified: Boolean = false,
) {
    val identityVerified: Boolean get() = identity.isVerifiedIdentity()
    val businessReady: Boolean get() = stage == StartupStage.Business && identityVerified && connectionVerified
    fun effectiveStage(): StartupStage = when {
        stage == StartupStage.Locked -> StartupStage.Locked
        !identityVerified -> StartupStage.Identity
        businessReady -> StartupStage.Business
        else -> StartupStage.Connection
    }
}
internal fun StartupSession.unlocked(): StartupSession = StartupSession(stage = StartupStage.Identity)
internal fun StartupSession.locked(): StartupSession = StartupSession()
internal fun StartupSession.identityResult(state: IdentityState): StartupSession = when {
    stage == StartupStage.Locked -> this
    !state.isVerifiedIdentity() -> copy(stage = StartupStage.Identity, identity = state, connectionVerified = false)
    stage == StartupStage.Business && state.member == identity.member -> copy(identity = state)
    else -> copy(stage = StartupStage.Connection, identity = state, connectionVerified = false)
}
internal fun StartupSession.connectionSucceeded(): StartupSession = when {
    stage == StartupStage.Locked -> this
    !identityVerified -> copy(stage = StartupStage.Identity, connectionVerified = false)
    else -> copy(stage = StartupStage.Business, connectionVerified = true)
}
internal fun StartupSession.connectionInvalidated(): StartupSession = when {
    stage == StartupStage.Locked -> this
    identityVerified -> copy(stage = StartupStage.Connection, connectionVerified = false)
    else -> copy(stage = StartupStage.Identity, connectionVerified = false)
}
internal fun StartupSession.configurationChanged(): StartupSession =
    if (stage == StartupStage.Locked) this
    else copy(stage = StartupStage.Identity, identity = IdentityState(), connectionVerified = false)

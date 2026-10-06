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

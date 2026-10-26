package com.jlucraft.manager.nativecore

import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject

/** Club roles that may maintain drafts and fill in the details of their own club. */
internal val OFFICE_MANAGEMENT_ROLES = setOf("admin", "manager")

/** A member as published by the office snapshot, including every device that may act for them. */
internal data class OfficeMemberProfile(
    val id: String,
    val club: String,
    val roles: Set<String>,
    val devices: Set<String>,
) {
    val president: Boolean get() = "president" in roles
    val manager: Boolean get() = roles.any { it in OFFICE_MANAGEMENT_ROLES }
}

/** The signed-in device resolved to its member, club, presidency and management permission. */
internal data class OfficeActor(
    val member: String,
    val club: String,
    val roles: Set<String>,
    val admin: Boolean,
) {
    val president: Boolean get() = "president" in roles

    /** A club manager may maintain drafts and details for the club only. */
    val manager: Boolean get() = roles.any { it in OFFICE_MANAGEMENT_ROLES }

    /** Server administrators, presidents and club managers all run management actions. */
    val canManage: Boolean get() = admin || president || manager

    /** Only a president opens a new draft; managers fill it in afterwards. */
    val canCreateDraft: Boolean get() = president

    val bound: Boolean get() = member.isNotBlank()

    fun managesClub(club: String): Boolean =
        admin || (bound && this.club == club && (president || manager))

    fun canManageEvent(event: JSONObject): Boolean =
        admin || (bound && managesClub(event.optString("organizer")))
}

private fun JSONObject.stringSet(key: String): Set<String> {
    val values = optJSONArray(key) ?: return emptySet()
    return (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() }.toSet()
}

private fun JSONObject.deviceSet(): Set<String> =
    buildSet {
        optString("device").takeIf { it.isNotBlank() }?.let(::add)
        addAll(stringSet("additional_devices"))
    }

/** All members from the snapshot, in snapshot order. */
internal fun officeMemberProfiles(state: JSONObject?): List<OfficeMemberProfile> {
    val members = state?.optJSONObject("members") ?: return emptyList()
    return members.keys().asSequence().mapNotNull { id ->
        val entry = members.optJSONObject(id) ?: return@mapNotNull null
        OfficeMemberProfile(id, entry.optString("club"), entry.stringSet("roles"), entry.deviceSet())
    }.toList()
}

/** The member that owns [device], if the device is bound to one. */
internal fun officeMemberProfile(state: JSONObject?, device: String): OfficeMemberProfile? {
    if (device.isBlank()) return null
    return officeMemberProfiles(state).firstOrNull { device in it.devices }
}

internal fun officeActor(state: JSONObject?, device: String): OfficeActor {
    val member = officeMemberProfile(state, device)
    val admins = state?.optJSONArray("admins") ?: JSONArray()
    var admin = false
    for (index in 0 until admins.length()) if (admins.optString(index) == device) admin = true
    return OfficeActor(
        member = member?.id.orEmpty(),
        club = member?.club.orEmpty(),
        roles = member?.roles.orEmpty(),
        admin = admin,
    )
}

/** Resource for a role name; unknown roles stay visible as their raw id. */
internal fun officeRoleLabel(role: String): Int? = when (role) {
    "president" -> R.string.office_role_president
    "admin" -> R.string.office_role_admin
    "manager" -> R.string.office_role_manager
    else -> null
}

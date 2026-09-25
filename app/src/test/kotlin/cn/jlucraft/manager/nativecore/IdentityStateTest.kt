package cn.jlucraft.manager.nativecore
import org.junit.Assert.*
import org.junit.Test
class IdentityStateTest {
    private val member = MemberIdentityView("person-42", setOf("old-phone"), setOf("club"), setOf("club"), setOf("server: manage"))
    @Test fun synchronizationFailureDoesNotExpireMembership() {
        val state = IdentityState().received(member).syncing().failed()
        assertEquals(IdentityAvailability.Unavailable, state.availability)
        assertEquals(member, state.member)
    }
    @Test fun replacementPhoneKeepsPersonAndIndependentRoles() {
        val state = IdentityState().received(member).received(member.copy(devices = setOf("new-phone")))
        assertEquals("person-42", state.member?.memberId)
        assertEquals(member.chairOf, state.member?.chairOf)
        assertEquals(member.permissions, state.member?.permissions)
    }
    @Test fun revokingMembershipDoesNotImplyRemovingOfficeOrPermission() {
        val state = IdentityState().received(member.copy(clubs = emptySet()))
        assertTrue(state.member!!.clubs.isEmpty())
        assertEquals(setOf("club"), state.member.chairOf)
        assertEquals(setOf("server: manage"), state.member.permissions)
    }
}

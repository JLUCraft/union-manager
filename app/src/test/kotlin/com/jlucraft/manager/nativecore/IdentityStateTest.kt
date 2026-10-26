package com.jlucraft.manager.nativecore
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
    @org.junit.Test fun cachedHistoryNeverClaimsCurrentAuthority() {
        val cached = IdentityState().received(null, current = false)
        org.junit.Assert.assertEquals(IdentityAvailability.Cached, cached.availability)
    }

    @Test fun onlySyncAndInspectMayRevokeAVerifiedIdentity() {
        assertTrue(isIdentityCheck("sync"))
        assertTrue(isIdentityCheck("inspect"))
        listOf("draft", "sign", "sign_offline", "submit").forEach { operation ->
            assertFalse(isIdentityCheck(operation))
        }
    }

    @Test fun onlyReadAndDraftOperationsReuseTheRememberedSession() {
        listOf("inspect", "sync", "draft").forEach { operation ->
            assertTrue(operation, usesRememberedSession(operation))
        }
        // Signing, submission and any unknown operation must authenticate every time. Draft stays
        // outside the identity check so a failed draft never invalidates the verified identity.
        listOf("sign", "sign_offline", "submit", "unknown", "", "Draft").forEach { operation ->
            assertFalse(operation, usesRememberedSession(operation))
        }
        assertFalse(isIdentityCheck("draft"))
    }

    @Test fun failedRecordWritesKeepThePriorVerifiedIdentity() {
        val verified = IdentityState().received(member)
        val syncing = verified.syncing()
        listOf("draft", "sign", "sign_offline", "submit").forEach { operation ->
            assertEquals(verified, identityStateAfterFailure(operation, syncing, verified))
        }
        assertEquals(IdentityAvailability.Unavailable, identityStateAfterFailure("sync", syncing, verified).availability)
        assertEquals(IdentityAvailability.Unavailable, identityStateAfterFailure("inspect", syncing, verified).availability)
    }

    @Test fun automaticSyncWaitsForTheFirstIdleMomentAndNeverLoopsAfterFailure() {
        val autoSync = IdentityAutoSync()
        assertFalse(autoSync.shouldStart(enabled = true, idle = false))
        assertFalse(autoSync.shouldStart(enabled = true, idle = false))
        assertTrue(autoSync.shouldStart(enabled = true, idle = true))
        // The sync may have failed; later bound->idle toggles must not start it again.
        assertFalse(autoSync.shouldStart(enabled = true, idle = true))
        assertFalse(autoSync.shouldStart(enabled = true, idle = false))
        assertFalse(autoSync.shouldStart(enabled = true, idle = true))
    }

    @Test fun automaticSyncDoesNotConsumeItsAttemptBeforeItIsEnabled() {
        val autoSync = IdentityAutoSync()
        assertFalse(autoSync.shouldStart(enabled = false, idle = true))
        assertTrue(autoSync.shouldStart(enabled = true, idle = true))
    }
}

package com.jlucraft.manager.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSessionTest {
    private val member = MemberIdentityView(
        memberId = "member-42",
        devices = setOf("device-42"),
        clubs = setOf("club-a"),
        chairOf = emptySet(),
        permissions = emptySet(),
    )
    private val verified = IdentityState().received(member)
    private val cached = IdentityState().received(member, current = false)
    private val target = ConnectionTarget("addr-a", "peer-a", "a".repeat(64))

    @Test fun onlyACurrentLedgerIdentityWithAnActiveClubClearsTheGate() {
        assertTrue(verified.isVerifiedIdentity())
        assertFalse(IdentityState().isVerifiedIdentity())
        assertFalse(IdentityState().syncing().isVerifiedIdentity())
        assertFalse(IdentityState().received(null).isVerifiedIdentity())
        assertFalse(cached.isVerifiedIdentity())
        assertFalse(IdentityState().received(member.copy(clubs = emptySet())).isVerifiedIdentity())
        assertFalse(verified.failed().isVerifiedIdentity())
    }

    @Test fun applicationNeedsIdentityAndOneVerifiedConnection() {
        val identity = StartupSession().unlocked().identityResult(verified)
        assertEquals(StartupStage.Connection, identity.effectiveStage())
        assertFalse(identity.businessReady)

        val business = identity.connectionSucceeded()
        assertEquals(StartupStage.Business, business.effectiveStage())
        assertTrue(business.businessReady)
        assertTrue(business.connectionVerified)

        val businessWithoutConnection = business.copy(connectionVerified = false)
        assertEquals(StartupStage.Connection, businessWithoutConnection.effectiveStage())
        assertFalse(businessWithoutConnection.businessReady)
    }

    @Test fun losingIdentityDropsConnectionAndBusinessBackToTheIdentityStep() {
        val business = StartupSession(stage = StartupStage.Business, identity = verified, connectionVerified = true)
        val lost = business.identityResult(verified.failed())
        assertEquals(StartupStage.Identity, lost.effectiveStage())
        assertFalse(lost.connectionVerified)
        assertFalse(lost.businessReady)
    }

    @Test fun aServerSideRevocationReturnsToTheIdentityStepAndCannotKeepOldRoleForms() {
        val business = StartupSession(stage = StartupStage.Business, identity = verified, connectionVerified = true)
        val revoked = business.identityResult(IdentityState().received(member.copy(clubs = emptySet())))
        assertEquals(StartupStage.Identity, revoked.effectiveStage())
        assertFalse(revoked.connectionVerified)
        assertFalse(revoked.businessReady)
    }

    @Test fun anUnavailableIdentityCheckStopsAtTheIdentityRetryStep() {
        val business = StartupSession(stage = StartupStage.Business, identity = verified, connectionVerified = true)
        val unavailable = business.identityResult(verified.failed())
        assertEquals(StartupStage.Identity, unavailable.effectiveStage())
        assertFalse(unavailable.connectionVerified)
        assertFalse(unavailable.identityVerified)
        assertFalse(unavailable.businessReady)
    }

    @Test fun invalidatingTheConnectionReturnsToTheConnectionStepNotTheWorkbench() {
        val business = StartupSession(stage = StartupStage.Business, identity = verified, connectionVerified = true)
        val invalidated = business.connectionInvalidated()
        assertEquals(StartupStage.Connection, invalidated.effectiveStage())
        assertFalse(invalidated.connectionVerified)
        assertFalse(invalidated.businessReady)
    }

    @Test fun changingConfigurationResetsTheVerifiedIdentity() {
        val business = StartupSession(stage = StartupStage.Business, identity = verified, connectionVerified = true)
        val changed = business.configurationChanged()
        assertEquals(StartupStage.Identity, changed.effectiveStage())
        assertEquals(IdentityState(), changed.identity)
        assertFalse(changed.connectionVerified)
    }

    @Test fun staleCallbacksCannotPassTheGateAfterLockConfigurationOrDeviceChange() {
        val scope = StartupScope(epoch = 7, configuration = "config-a", device = "device-a")
        assertTrue(scope.matches(7, "config-a", "device-a"))
        assertFalse(scope.matches(8, "config-a", "device-a"))
        assertFalse(scope.matches(7, "config-b", "device-a"))
        assertFalse(scope.matches(7, "config-a", "device-b"))
    }

    @Test fun aLockedSessionCannotBePromotedByAnOldIdentityResult() {
        val locked = StartupSession().locked()
        assertEquals(StartupStage.Locked, locked.identityResult(verified).effectiveStage())
        assertEquals(StartupStage.Locked, locked.connectionSucceeded().effectiveStage())
    }

    @Test fun aReplyFromAnOldAddressOrPinIsRejected() {
        val scope = ConnectionScope(StartupScope(7, "config-a", "device-a"), generation = 3, target = target)
        val epoch = 7L
        val configuration = "config-a"
        val device = "device-a"
        val generation = 3L
        assertTrue(scope.matches(epoch, configuration, device, generation, target))
        assertFalse(scope.matches(epoch, configuration, device, generation, target.copy(address = "addr-b")))
        assertFalse(scope.matches(epoch, configuration, device, generation, target.copy(peer = "peer-b")))
        assertFalse(scope.matches(epoch, configuration, device, generation, target.copy(genesisHash = "b".repeat(64))))
        assertFalse(scope.matches(epoch, configuration, device, generation + 1, target))
        assertFalse(scope.matches(epoch + 1, configuration, device, generation, target))
    }

    @Test fun oneVerifiedConnectionServesTheApplicationUntilItsTargetIsInvalidated() {
        val session = StartupSession().unlocked().identityResult(verified).connectionSucceeded()
        assertEquals(StartupStage.Business, session.effectiveStage())
        assertTrue(session.businessReady)
        val changedTarget = session.connectionInvalidated()
        assertEquals(StartupStage.Connection, changedTarget.effectiveStage())
        assertFalse(changedTarget.connectionVerified)
    }

    @Test fun identityRecordChangesInvalidateTheProvedBusinessConnection() {
        val business = StartupSession(stage = StartupStage.Business, identity = verified, connectionVerified = true)
        val unchanged = business.identityResult(verified)
        assertEquals(StartupStage.Business, unchanged.effectiveStage())

        val changedFacts = listOf(
            member.copy(memberId = "member-43"),
            member.copy(clubs = setOf("club-b")),
            member.copy(chairOf = setOf("club-a")),
            member.copy(permissions = setOf("server: manage")),
        )
        changedFacts.forEach { changed ->
            val next = business.identityResult(IdentityState().received(changed))
            assertEquals(StartupStage.Connection, next.effectiveStage())
            assertFalse(next.connectionVerified)
        }
    }

    @Test fun automaticConnectionCheckRunsOncePerGenerationAndWaitsForRetry() {
        val check = ConnectionAutoCheck()
        assertFalse(check.shouldCheck(generation = 5, ready = false))
        assertTrue(check.shouldCheck(generation = 5, ready = true))
        // A failed check keeps the same generation; idle/busy toggles must not retry it.
        assertFalse(check.shouldCheck(generation = 5, ready = true))
        assertFalse(check.shouldCheck(generation = 5, ready = true))
        // A new identity/configuration generation re-arms exactly once.
        assertTrue(check.shouldCheck(generation = 6, ready = true))
        assertFalse(check.shouldCheck(generation = 6, ready = true))
    }

    @Test fun aManualConnectionAttemptConsumesTheAutomaticAttempt() {
        val check = ConnectionAutoCheck()
        check.markAttempted(generation = 5)
        assertFalse(check.shouldCheck(generation = 5, ready = true))
        assertTrue(check.shouldCheck(generation = 6, ready = true))
    }
}

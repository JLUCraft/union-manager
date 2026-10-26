package com.jlucraft.manager.nativecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficeRoleTest {
    private val state = JSONObject(
        """
        {
          "admins": ["server-admin-device"],
          "members": {
            "president": {"device": "president-device", "club": "a", "roles": ["president"]},
            "president-b": {"device": "president-b-device", "club": "b", "roles": ["president"]},
            "manager": {"device": "manager-device", "club": "a", "roles": ["manager"]},
            "local-admin": {"device": "admin-device", "club": "a", "roles": ["admin"]},
            "outsider": {"device": "outsider-device", "club": "b", "roles": []}
          },
          "events": {
            "event": {
              "title": "Draft",
              "creator": "president",
              "organizer": "a",
              "phase": "review",
              "epoch": 2
            }
          }
        }
        """,
    )

    private fun flow(delegations: String = "{}"): JSONObject = JSONObject(
        """
        {
          "template": "approval",
          "template_epoch": 1,
          "current": 0,
          "status": "active",
          "proposer": "president",
          "payload": {"type": "events.approve", "id": "event", "epoch": 2},
          "workflow": {
            "name": "Approval",
            "payload_kinds": ["events.approve"],
            "stages": [
              {
                "name": "President confirmation",
                "approver": {"type": "role", "club": "a", "role": "president"},
                "timeout_ms": null,
                "on_timeout": "reject"
              }
            ]
          },
          "records": [{"approvals": {}, "delegations": $delegations}]
        }
        """,
    )

    private fun presidentsFlow(delegations: String = "{}", approvals: String = "{}"): JSONObject = JSONObject(
        """
        {
          "template": "",
          "template_epoch": 0,
          "current": 0,
          "status": "active",
          "proposer": "president",
          "payload": {"type": "events.approve", "id": "event", "epoch": 2, "co_organizers": ["b"]},
          "workflow": {
            "name": "Club president approval",
            "payload_kinds": ["events.approve"],
            "stages": [
              {
                "name": "Club president signatures",
                "approver": {"type": "club_presidents", "clubs": ["a", "b"]},
                "timeout_ms": null,
                "on_timeout": "reject"
              }
            ]
          },
          "records": [{"approvals": $approvals, "delegations": $delegations}]
        }
        """,
    )

    @Test fun onlyPresidentOpensDraftsButClubManagersMaintainThem() {
        val event = state.getJSONObject("events").getJSONObject("event")
        val president = officeActor(state, "president-device")
        val manager = officeActor(state, "manager-device")
        val localAdmin = officeActor(state, "admin-device")
        val outsider = officeActor(state, "outsider-device")
        val serverAdmin = officeActor(state, "server-admin-device")

        assertTrue(president.canCreateDraft)
        assertFalse(manager.canCreateDraft)
        assertFalse(localAdmin.canCreateDraft)
        assertFalse(serverAdmin.canCreateDraft)

        assertTrue(president.canManageEvent(event))
        assertTrue(manager.canManageEvent(event))
        assertTrue(localAdmin.canManageEvent(event))
        assertTrue(serverAdmin.canManageEvent(event))
        assertFalse(outsider.canManageEvent(event))
    }

    @Test fun approvalSeatFollowsTheCurrentStageIdentity() {
        val president = officeApprovalSeat(state, "president-device", flow())
        assertNotNull(president)
        assertTrue(president!!.canApprove)
        assertNull(officeApprovalSeat(state, "manager-device", flow()))
        assertNull(officeApprovalSeat(state, "outsider-device", flow()))
    }

    @Test fun delegationMovesTheSeatToASameClubMember() {
        val delegated = flow("""{"role": {"from_device": "president-device", "to_member": "manager"}}""")
        val manager = officeApprovalSeat(state, "manager-device", delegated)
        assertNotNull(manager)
        assertTrue(manager!!.canApprove)
        assertTrue(manager.delegatedBy == "president-device")
        assertNull(officeApprovalSeat(state, "president-device", delegated))
    }

    @Test fun clubPresidentStageCountsOneSeatPerClubAndRefusesDelegation() {
        val approver = officeApprover(officeFlowStage(state, presidentsFlow()))
        assertTrue(approver is OfficeApprover.ClubPresidents)
        assertEquals(setOf("a", "b"), (approver as OfficeApprover.ClubPresidents).clubs)
        assertEquals(2, officeApprovalThreshold(approver))

        assertNotNull(officeApprovalSeat(state, "president-device", presidentsFlow()))
        assertNotNull(officeApprovalSeat(state, "president-b-device", presidentsFlow()))
        assertNull(officeApprovalSeat(state, "manager-device", presidentsFlow()))

        val delegated = presidentsFlow("""{"a": {"from_device": "president-device", "to_member": "manager"}}""")
        assertNull(officeApprovalSeat(state, "manager-device", delegated))
        val seat = officeApprovalSeat(state, "president-device", delegated)
        assertNotNull(seat)
        assertNull(seat!!.delegatedBy)
    }

    @Test fun participatingPresidentExecutesButManagersDoNot() {
        val approved = presidentsFlow().put("status", "approved")
        assertTrue(officeCanExecuteFlow(state, "president-device", approved))
        assertTrue(officeCanExecuteFlow(state, "president-b-device", approved))
        assertFalse(officeCanExecuteFlow(state, "manager-device", approved))
        assertFalse(officeCanExecuteFlow(state, "outsider-device", approved))
        assertFalse(officeCanExecuteFlow(state, "president-b-device", presidentsFlow()))
    }

    @Test fun creatorBypassIsGoneAndRevokedRolesLoseManagement() {
        val event = JSONObject(state.getJSONObject("events").getJSONObject("event").toString())
        val president = officeActor(state, "president-device")
        assertFalse(president.canManageEvent(event.put("organizer", "b")))

        val revoked = JSONObject(state.toString())
        revoked.getJSONObject("members").getJSONObject("manager").put("roles", org.json.JSONArray())
        assertFalse(officeActor(revoked, "manager-device").canManageEvent(event.put("organizer", "a")))
    }

    @Test fun approvalOfTheCurrentSlotIsNotOfferedTwice() {
        val approvedFlow = flow()
        approvedFlow.getJSONArray("records").getJSONObject(0).put(
            "approvals",
            JSONObject("""{"role": {"device": "president-device", "comment_hash": "digest"}}"""),
        )
        val seat = officeApprovalSeat(state, "president-device", approvedFlow)
        assertNotNull(seat)
        assertTrue(seat!!.approved)
        assertFalse(seat.canApprove)
        assertEquals(1, officeApprovalProgress(state, approvedFlow))
    }

    @Test fun revokedPresidentReceiptDoesNotBlockTheNewPresidentOfTheSameClub() {
        val revoked = JSONObject(state.toString())
        revoked.getJSONObject("members").getJSONObject("president").put("roles", org.json.JSONArray())
        revoked.getJSONObject("members").put(
            "president-new",
            JSONObject().put("device", "president-new-device").put("club", "a")
                .put("roles", org.json.JSONArray().put("president")),
        )
        val signed = flow()
        signed.getJSONArray("records").getJSONObject(0).put(
            "approvals",
            JSONObject("""{"role": {"device": "president-device", "comment_hash": "digest"}}"""),
        )

        assertNull(officeApprovalSeat(revoked, "president-device", signed))
        assertEquals(0, officeApprovalProgress(revoked, signed))
        val seat = officeApprovalSeat(revoked, "president-new-device", signed)
        assertNotNull(seat)
        assertFalse(seat!!.approved)
        assertTrue(seat.canApprove)

        signed.getJSONArray("records").getJSONObject(0).put(
            "approvals",
            JSONObject("""{"role": {"device": "president-new-device", "comment_hash": "digest"}}"""),
        )
        assertEquals(1, officeApprovalProgress(revoked, signed))
        assertTrue(officeApprovalSeat(revoked, "president-new-device", signed)!!.approved)
    }

    @Test fun staleApprovalTargetsLeaveTheTodoAndCannotExecute() {
        val stale = presidentsFlow().apply { getJSONObject("payload").put("epoch", 1) }
        assertTrue(officeFlowVisible(state, "president-device", stale))
        assertFalse(officeFlowNeedsAttention(state, "president-device", stale))
        stale.put("status", "approved")
        assertFalse(officeCanExecuteFlow(state, "president-device", stale))

        val missing = presidentsFlow().apply { getJSONObject("payload").put("id", "missing") }
        assertFalse(officeFlowNeedsAttention(state, "president-device", missing))
        missing.put("status", "approved")
        assertFalse(officeCanExecuteFlow(state, "president-device", missing))

        val settled = JSONObject(state.toString())
        settled.getJSONObject("events").getJSONObject("event").put("phase", "approved")
        val current = presidentsFlow()
        assertFalse(officeFlowNeedsAttention(settled, "president-device", current))
        current.put("status", "approved")
        assertFalse(officeCanExecuteFlow(settled, "president-device", current))

        assertTrue(officeFlowNeedsAttention(state, "president-device", presidentsFlow()))
    }

    @Test fun taskDeskScopeFollowsAuthorityAndKeepsLocalWorkInsideItsClub() {
        val scoped = JSONObject(state.toString()).put(
            "events",
            JSONObject("""{
                "a-draft": {"title": "A", "organizer": "a", "phase": "draft", "epoch": 1},
                "a-open": {"title": "Open", "organizer": "a", "phase": "open", "epoch": 1},
                "b-draft": {"title": "B", "organizer": "b", "phase": "draft", "epoch": 1}
            }"""),
        )

        assertEquals(OfficeTaskScope.President, officeTaskScope(officeActor(scoped, "president-device")))
        assertEquals(listOf("a-draft"), officeTaskEventIds(scoped, officeActor(scoped, "president-device")))
        assertEquals(OfficeTaskScope.ClubManager, officeTaskScope(officeActor(scoped, "manager-device")))
        assertEquals(listOf("a-draft"), officeTaskEventIds(scoped, officeActor(scoped, "manager-device")))
        assertEquals(OfficeTaskScope.ServerAdmin, officeTaskScope(officeActor(scoped, "server-admin-device")))
        assertEquals(listOf("a-draft", "b-draft"), officeTaskEventIds(scoped, officeActor(scoped, "server-admin-device")))
        assertEquals(OfficeTaskScope.Member, officeTaskScope(officeActor(scoped, "outsider-device")))
        assertEquals(listOf("a-open"), officeTaskEventIds(scoped, officeActor(scoped, "outsider-device")))

        val combined = JSONObject(scoped.toString())
        combined.getJSONArray("admins").put("president-device")
        assertEquals(OfficeTaskScope.PresidentAdmin, officeTaskScope(officeActor(combined, "president-device")))
        assertEquals(listOf("a-draft", "b-draft"), officeTaskEventIds(combined, officeActor(combined, "president-device")))
    }
}

package com.jlucraft.manager.nativecore

import androidx.compose.ui.test.*
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MembershipIssuanceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun node(id: Int) = compose.onNodeWithText(context.getString(id))
    private val genesis = "a".repeat(64)
    private val otherGenesis = "c".repeat(64)
    private val head = "b".repeat(64)

    private fun facts(
        verifier: Boolean = false,
        chairs: JSONObject = JSONObject().put("club-a", "chair-person").put("club-b", "other-person"),
        ledger: String = genesis,
    ): JSONObject = JSONObject()
        .put("federation", "test-federation")
        .put("checkpoint", JSONObject().put("genesis", ledger).put("sequence", 7).put("head", head))
        .put("members", JSONObject()
            .put("chair-person", JSONArray().put("device"))
            .put("existing-person", JSONArray().put("other-device"))
            .put("outsider-person", JSONArray().put("outsider-device")))
        .put("memberships", JSONArray()
            .put(JSONArray().put("chair-person").put("club-a"))
            .put(JSONArray().put("existing-person").put("club-a"))
            .put(JSONArray().put("outsider-person").put("club-b")))
        .put("chairs", chairs)
        .put("permissions", JSONObject())
        .put("verifiers", if (verifier) JSONObject().put("auto-verifier", JSONObject()
            .put("club", "club-a").put("device", "verifier-device")
            .put("evidence", JSONArray().put("institutional_email")).put("revoked", false)) else JSONObject())

    private val config = JSONObject().put("genesis_hash", genesis).put("genesis", JSONObject())
        .put("peer", "test-peer").put("address", "test-address").toString()

    private fun record(action: JSONObject, ledger: String = genesis): JSONObject = JSONObject()
        .put("federation", "test-federation")
        .put("genesis", ledger)
        .put("sequence", 8)
        .put("previous", head)
        .put("action", action)
        .put("signatures", JSONArray())

    private fun manualAction(member: String, club: String, device: String? = null): JSONObject =
        JSONObject().put("type", "confirm_member").put("member", member).put("club", club)
            .put("evidence", "manual").put("verifier", JSONObject.NULL).apply {
                if (device != null) put("device", device)
            }

    private fun applicantDevice(): String {
        val secret = UnionNative.generateSecret()
        return try { UnionNative.peerId(secret) } finally { secret.fill(0) }
    }

    private fun screen(
        facts: JSONObject = facts(),
        proposal: String = "",
        execute: (JSONObject, (JSONObject) -> Unit) -> Unit = { _, _ -> error("must not execute") },
    ): IdentityOps {
        val ops = IdentityOps(
            config = config,
            device = "device",
            directory = context.cacheDir,
            review = { request -> JSONObject(request).getJSONObject("config").toString() },
            execute = execute,
            onConfig = {},
            onStateChange = { _, _ -> },
            onBusinessGeneses = {},
            onFetchFailed = {},
            initialState = IdentityState().received(identityMember(facts, "device")),
            initialFacts = facts,
        )
        if (proposal.isNotBlank()) ops.editProposal(proposal)
        compose.setContent {
            JluManagerTheme {
                IssuanceFlowHost(
                    ops = ops,
                    busy = false,
                    current = ops.state.availability == IdentityAvailability.Current,
                    configured = true,
                    facts = ops.facts,
                    knownMembers = knownMemberIds(ops.facts),
                    onBack = {},
                )
            }
        }
        return ops
    }

    private fun openTarget() {
        node(R.string.identity_issuance_open_target).scrollToIfScrollable().performClick()
    }

    @Test fun chairIssuesNewAndExistingMembersFromTheNormalFlow() {
        var action: JSONObject? = null
        val state = facts()
        val device = applicantDevice()
        screen(state) { input, receive ->
            when (input.optString("operation")) {
                "sync", "inspect" -> receive(state)
                "draft" -> {
                    action = input.getJSONObject("action")
                    receive(record(action))
                }
                else -> receive(JSONObject().put("error", "unexpected"))
            }
        }
        node(R.string.identity_issuance_open_roster).scrollToIfScrollable().performClick()
        compose.onNodeWithText(context.getString(R.string.identity_issuance_roster_title, "club-a")).assertExists()
        compose.onNodeWithText("club-b", substring = true).assertDoesNotExist()
        compose.onNodeWithText("outsider-person").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()

        openTarget()
        node(R.string.identity_field_member).scrollToIfScrollable().performTextInput("new-person")
        node(R.string.identity_issuance_new).assertExists()
        node(R.string.identity_issuance_device).assertExists()
        node(R.string.identity_issuance_draft).assertIsNotEnabled()
        node(R.string.identity_issuance_device).performTextInput(device)
        node(R.string.identity_issuance_draft).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            val drafted = checkNotNull(action)
            assertEquals("confirm_member", drafted.getString("type"))
            assertEquals("new-person", drafted.getString("member"))
            assertEquals("club-a", drafted.getString("club"))
            assertEquals(device, drafted.getString("device"))
            assertEquals("manual", drafted.getString("evidence"))
            assertTrue(drafted.isNull("verifier"))
        }
        node(R.string.identity_issuance_evidence_label).assertExists()
        node(R.string.identity_issuance_evidence_manual).assertExists()
        compose.onNodeWithText("manual", substring = true).assertDoesNotExist()
    }

    @Test fun existingMemberDoesNotBindADeviceUnlessAsked() {
        var action: JSONObject? = null
        val state = facts()
        screen(state) { input, receive ->
            when (input.optString("operation")) {
                "draft" -> {
                    action = input.getJSONObject("action")
                    receive(record(action))
                }
                else -> receive(state)
            }
        }
        openTarget()
        node(R.string.identity_issuance_known).scrollToIfScrollable().performClick()
        compose.onNode(hasText("existing-person") and hasClickAction()).performClick()
        node(R.string.identity_issuance_device).assertDoesNotExist()
        node(R.string.identity_issuance_draft).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            val drafted = checkNotNull(action)
            assertEquals("existing-person", drafted.getString("member"))
            assertEquals("club-a", drafted.getString("club"))
            assertTrue(drafted.isNull("device"))
        }
    }

    @Test fun officePresidentRoleAloneDoesNotGrantIssuance() {
        val noChair = JSONObject("""
            {
              "members": {"president-person": ["device"]},
              "memberships": [["president-person", "club-a"]],
              "checkpoint": {"genesis": "$genesis", "sequence": 7, "head": "$head"},
              "chairs": {},
              "permissions": {}
            }
        """.trimIndent())
        screen(noChair)
        node(R.string.identity_issuance_no_chair).assertExists()
        node(R.string.identity_issuance_club).assertDoesNotExist()
        node(R.string.identity_issuance_open_target).assertDoesNotExist()
        node(R.string.identity_issuance_roster_title).assertDoesNotExist()
    }

    @Test fun losingTheChairRoleRemovesTheWriteEntries() {
        val current = facts()
        val withoutChair = JSONObject(current.toString()).put("chairs", JSONObject())
        var revoked = false
        val ops = screen(current) { _, receive -> receive(if (revoked) withoutChair else current) }
        node(R.string.identity_issuance_open_target).assertExists()
        revoked = true
        compose.runOnIdle { ops.run("sync") }
        node(R.string.identity_issuance_no_chair).assertExists()
        node(R.string.identity_issuance_club).assertDoesNotExist()
        node(R.string.identity_issuance_open_target).assertDoesNotExist()
    }

    @Test fun cachedFactsRemainReadableButCannotIssue() {
        var offline = false
        val state = facts()
        val ops = screen(state) { _, receive -> receive(if (offline) JSONObject().put("error", "offline") else state) }
        node(R.string.identity_issuance_open_target).assertExists()
        offline = true
        compose.runOnIdle { ops.run("sync") }
        node(R.string.identity_issuance_cached).assertExists()
        node(R.string.identity_issuance_open_target).assertIsNotEnabled()
        node(R.string.identity_issuance_open_roster).assertExists()
    }

    @Test fun signingAndFailedSubmissionReuseTheRecordWithoutClaimingSuccess() {
        val state = facts()
        screen(state) { input, receive ->
            when (input.optString("operation")) {
                "draft" -> receive(record(input.getJSONObject("action")))
                "sign" -> receive(JSONObject(input.getJSONObject("record").toString())
                    .put("signatures", JSONArray().put(JSONObject().put("device", "device"))))
                "submit" -> receive(JSONObject().put("error", "signatures missing"))
                else -> receive(state)
            }
        }
        openTarget()
        node(R.string.identity_issuance_known).scrollToIfScrollable().performClick()
        compose.onNode(hasText("existing-person") and hasClickAction()).performClick()
        node(R.string.identity_issuance_draft).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()

        node(R.string.identity_sign).scrollToIfScrollable().performClick()
        compose.onNodeWithText(context.getString(R.string.identity_signature_count, 1)).assertExists()

        node(R.string.identity_submit).scrollToIfScrollable().performClick()
        compose.onNode(hasText(context.getString(R.string.identity_submit)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        node(R.string.identity_issuance_failed).assertExists()
        node(R.string.identity_issuance_submitted).assertDoesNotExist()
        node(R.string.identity_summary_title).assertExists()
    }

    @Test fun automaticVerifierDoesNotReplaceTheManualIssuanceEntry() {
        screen(facts(verifier = true))
        openTarget()
        node(R.string.identity_issuance_known).scrollToIfScrollable().performClick()
        compose.onNode(hasText("existing-person") and hasClickAction()).performClick()
        node(R.string.identity_issuance_draft).assertIsEnabled()
    }

    @Test fun successfulSubmissionRefreshesTheRealClubRoster() {
        val initial = facts()
        val device = applicantDevice()
        val updated = JSONObject(initial.toString())
        updated.getJSONObject("checkpoint").put("sequence", 8).put("head", "d".repeat(64))
        updated.getJSONObject("members").put("new-person", JSONArray().put(device))
        updated.getJSONArray("memberships").put(JSONArray().put("new-person").put("club-a"))
        screen(initial) { input, receive ->
            when (input.optString("operation")) {
                "draft" -> receive(record(input.getJSONObject("action")))
                "sign" -> receive(JSONObject(input.getJSONObject("record").toString())
                    .put("signatures", JSONArray().put(JSONObject().put("device", "device"))))
                "submit" -> receive(updated)
                else -> receive(initial)
            }
        }
        openTarget()
        node(R.string.identity_field_member).scrollToIfScrollable().performTextInput("new-person")
        node(R.string.identity_issuance_device).performTextInput(device)
        node(R.string.identity_issuance_draft).scrollToIfScrollable().performClick()
        node(R.string.identity_sign).scrollToIfScrollable().performClick()
        node(R.string.identity_submit).scrollToIfScrollable().performClick()
        compose.onNode(hasText(context.getString(R.string.identity_submit)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        node(R.string.identity_issuance_submitted).assertExists()
    }

    @Test fun importedRecordsOutsideTheCurrentIssuanceCannotBeSignedThere() {
        screen()
        node(R.string.identity_issuance_open_import).scrollToIfScrollable().performClick()
        node(R.string.identity_import_record).scrollToIfScrollable().performClick()
        node(R.string.identity_issuance_import).performTextInput(
            record(manualAction("new-person", "club-a"), ledger = otherGenesis).toString())
        node(R.string.identity_review_changes).scrollToIfScrollable().performClick()
        node(R.string.identity_issuance_import_other_ledger).assertExists()
        node(R.string.identity_sign).assertDoesNotExist()
    }

    @Test fun manualConfirmMemberActionUsesTheSharedBuilder() {
        val device = applicantDevice()
        val existing = confirmMemberAction(membershipFields("existing-person", "club-a", null))
        assertEquals("confirm_member", existing.getString("type"))
        assertEquals("existing-person", existing.getString("member"))
        assertEquals("club-a", existing.getString("club"))
        assertEquals("manual", existing.getString("evidence"))
        assertTrue(existing.isNull("device"))
        assertTrue(existing.isNull("verifier"))

        val newMember = confirmMemberAction(membershipFields("new-person", "club-a", device))
        assertEquals(device, newMember.getString("device"))
        assertEquals("manual", newMember.getString("evidence"))
        assertFalse(newMember.isNull("device"))
    }
    @Test fun reopeningTheImportPageKeepsThePastedRecord() {
        val staff = record(manualAction("existing-person", "club-a"))
        screen(proposal = staff.toString())
        node(R.string.identity_issuance_open_import).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_issuance_import).assertDoesNotExist()
        node(R.string.identity_raw_record_show).scrollToIfScrollable().performClick()
        compose.onNodeWithText(staff.toString()).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
        node(R.string.identity_issuance_open_import).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_raw_record_show).scrollToIfScrollable().performClick()
        compose.onNodeWithText(staff.toString()).assertExists()
    }

    @Test fun onlyAnExplicitResetClearsTheRecord() {
        val staff = record(manualAction("existing-person", "club-a"))
        screen(proposal = staff.toString())
        node(R.string.identity_issuance_open_import).scrollToIfScrollable().performClick()
        node(R.string.identity_issuance_reset).scrollToIfScrollable().performClick()
        compose.onNodeWithText(staff.toString()).assertDoesNotExist()
        node(R.string.identity_import_record).assertExists()
        node(R.string.identity_issuance_import).assertDoesNotExist()
        node(R.string.identity_import_record).scrollToIfScrollable().performClick()
        node(R.string.identity_issuance_import).assertExists()
    }

    @Test fun officeTaskDeskIssuanceEntryOpensTheExistingChairFlow() {
        val state = facts()
        val ops = IdentityOps(
            config = config,
            device = "device",
            directory = context.cacheDir,
            review = { request -> JSONObject(request).getJSONObject("config").toString() },
            execute = { _, _ -> error("must not execute") },
            onConfig = {},
            onStateChange = { _, _ -> },
            onBusinessGeneses = {},
            onFetchFailed = {},
            initialState = IdentityState().received(identityMember(state, "device")),
            initialFacts = state,
        )
        val office = JSONObject()
            .put("admins", JSONArray())
            .put("members", JSONObject().put("chair-person", JSONObject().put("device", "device").put("club", "club-a").put("roles", JSONArray(listOf("president")))))
            .put("events", JSONObject())
        compose.setContent { JluManagerTheme {
            val stack = rememberNavBackStack(WorkspaceRoot)
            val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
            NavDisplay(backStack = stack, onBack = { pop() }, entryProvider = entryProvider {
                entry<WorkspaceRoot> {
                    WorkspaceDashboard(
                        state = office, identity = "device", busy = false,
                        onCreateActivity = {}, onMembershipIssuance = { stack.add(AccountIssuance) }, onPersonnel = {},
                        onOpenActivities = {}, onOpenApprovals = {}, onOpenReports = {}, onOpenResources = {},
                    )
                }
                entry<AccountIssuance> {
                    IssuanceFlowHost(
                        ops, busy = false, current = true, configured = true, facts = ops.facts,
                        knownMembers = knownMemberIds(ops.facts), onBack = pop,
                    )
                }
            })
        } }
        compose.onNodeWithTag(OFFICE_TASK_ISSUANCE_TAG).scrollToIfScrollable().performClick()
        node(R.string.identity_issuance_title).assertExists()
        node(R.string.identity_issuance_open_target).assertExists()
        node(R.string.identity_issuance_club).assertExists()
    }

    @Test fun losingTheChairOnTheTargetPageReturnsToTheReadOnlyHome() {
        val current = facts()
        val withoutChair = JSONObject(current.toString()).put("chairs", JSONObject())
        var revoked = false
        val ops = screen(current) { _, receive -> receive(if (revoked) withoutChair else current) }
        openTarget()
        revoked = true
        compose.runOnIdle { ops.run("sync") }
        node(R.string.identity_issuance_no_chair).assertExists()
        node(R.string.identity_issuance_open_target).assertDoesNotExist()
    }

}

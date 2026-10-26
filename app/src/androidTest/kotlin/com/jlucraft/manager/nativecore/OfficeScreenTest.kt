package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficeScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
    private fun node(id: Int) = compose.onNodeWithText(text(id))

    private fun requestsState(members: String, events: String, admins: String = "[]", signups: String = "{}") =
        JSONObject("""{"admins": $admins, "members": $members, "events": $events, "signups": $signups}""")

    private fun dashboardPage(
        state: JSONObject?,
        identity: String,
        onCreateDraft: () -> Unit = {},
        onOpenActivities: () -> Unit = {},
    ) {
        compose.setContent { JluManagerTheme {
            WorkspaceDashboard(
                state = state,
                identity = identity,
                busy = false,
                onCreateActivity = onCreateDraft,
                onMembershipIssuance = {},
                onPersonnel = {},
                onOpenActivities = onOpenActivities,
                onOpenApprovals = {},
                onOpenReports = {},
                onOpenResources = {},
            )
        } }
    }

    @Test fun disconnectedDashboardShowsAnEmptyStateWithoutBusinessEntries() {
        dashboardPage(null, "")
        node(R.string.office_disconnected_title).assertExists()
        node(R.string.office_tasks_title_president).assertDoesNotExist()
        node(R.string.office_create).assertDoesNotExist()
        node(R.string.office_role_president).assertDoesNotExist()
    }

    @Test fun connectionPageShowsTheSummaryBeforeTheSteps() {
        compose.setContent { JluManagerTheme {
            AccountConnectionPage(
                state = null, identity = "", member = null, identityReady = false,
                address = "", peer = "", genesisHash = "", connected = false, busy = false,
                failure = null, onConnectTarget = {}, onLink = {},
                discover = { reply -> reply(JSONObject().put("error", true)) }, onBack = {},
            )
        } }
        compose.onNodeWithText(text(R.string.connection_link)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.connection_address)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.account_connection_change)).performClick()
        compose.onNodeWithText(text(R.string.startup_connection_method_manual)).scrollToIfScrollable().performClick()
        compose.onNodeWithText(text(R.string.connection_address)).assertExists()
        compose.onNodeWithText(text(R.string.startup_connection_check)).scrollToIfScrollable().assertIsNotEnabled()
    }

    @Test fun identifiersAreCollapsedAndControlsMeetTouchMinimum() {
        val identifier = "bafy-test-identifier-full-content"
        compose.setContent { MaterialTheme { Column { IdentifierDetails(identifier) } } }
        compose.onNodeWithText(identifier).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.identifier_details)).assertHeightIsAtLeast(MIN_TOUCH_TARGET).performClick()
        compose.onNodeWithText(identifier).assertExists()
        compose.onNodeWithText(text(R.string.identifier_copy)).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test fun filesRequireConnectedStateBeforeImportOrExport() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeAttachment(null, false, "test-request", "", {}, { error("must not upload") })
        } } }
        compose.onNodeWithText(text(R.string.office_attachment_add)).assertIsNotEnabled().assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        compose.onNodeWithText(text(R.string.office_attachment_download)).assertDoesNotExist()
    }

    @Test fun aiTemplateShowsWorkflowStageWithoutModelControls() {
        val template = JSONObject("""{"name":"Review","payload_kinds":["events.approve"],"stages":[{"name":"Summary","approver":{"type":"ai","mode":"review","worker":"fixture-worker","model":"fixture-model","purpose":"events.summary"},"timeout_ms":null,"on_timeout":"reject"}]}""")
        compose.setContent { MaterialTheme { Column { FlowTemplatePreview(template) } } }
        compose.onNodeWithText("fixture-worker", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_stage_review)).assertExists()
    }

    @Test fun reviewWithoutPlanStartsPresidentSignaturesDirectly() {
        val state = JSONObject("""
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "Draft", "creator": "president", "organizer": "a", "phase": "review", "epoch": 2, "brief_hash": "digest", "co_organizers": ["b"]}}
            }
        """)
        var sent: JSONObject? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeEventDetail(state, "event", state.getJSONObject("events").getJSONObject("event"), false, officeActor(state, "device"), true, send = { sent = it })
        } } }
        compose.onNodeWithText(text(R.string.office_start_presidents)).scrollToIfScrollable().assertIsEnabled().performClick()
        val request = checkNotNull(sent)
        org.junit.Assert.assertEquals("flows.start_presidents", request.getString("type"))
        org.junit.Assert.assertFalse(request.has("template"))
        val payload = request.getJSONObject("payload")
        org.junit.Assert.assertEquals("events.approve", payload.getString("type"))
        val coOrganizers = payload.getJSONArray("co_organizers")
        org.junit.Assert.assertEquals(1, coOrganizers.length())
        org.junit.Assert.assertEquals("b", coOrganizers.getString(0))
        compose.onNodeWithText(text(R.string.office_request_approval)).assertDoesNotExist()
    }

    @Test fun planApprovalOnlyOffersAutomaticReviewAndUsesReviewMaterial() {
        val state = JSONObject("""
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "League", "creator": "president", "organizer": "a", "phase": "review", "epoch": 2, "brief_hash": "digest"}},
              "organization": {"plans": {"event": {"review_input": "review-input", "schools": {"a": {}, "b": {}}}}},
              "flow_templates": {
                "manual": {"active": true, "versions": [{"name": "Manual", "payload_kinds": ["events.approve"], "stages": [{"name": "Manual", "approver": {"type": "member", "member": "president"}}]}]},
                "automatic": {"active": true, "versions": [{"name": "Automatic review", "payload_kinds": ["events.approve"], "stages": [{"name": "Review", "approver": {"type": "ai", "mode": "review", "worker": "worker", "model": "model", "purpose": "events.approve", "max_tokens": 500}}]}]}
              }
            }
        """)
        var sent: JSONObject? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeEventDetail(state, "event", state.getJSONObject("events").getJSONObject("event"), false, officeActor(state, "device"), true, send = { sent = it })
        } } }
        compose.onNodeWithText(text(R.string.office_start_presidents)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_request_approval)).scrollToIfScrollable().performClick()
        compose.onNodeWithText("Automatic review").performClick()
        compose.onNodeWithText(text(R.string.office_flow_start_confirm_action)).performClick()
        val request = checkNotNull(sent)
        org.junit.Assert.assertEquals("flows.start", request.getString("type"))
        org.junit.Assert.assertEquals("automatic", request.getString("template"))
        org.junit.Assert.assertEquals("review-input", request.getString("input"))
        org.junit.Assert.assertEquals(OFFICE_FLOW_MAX_POINTS, request.getLong("max_points"))
        val clubs = request.getJSONObject("payload").getJSONArray("co_organizers")
        org.junit.Assert.assertEquals(1, clubs.length())
        org.junit.Assert.assertEquals("b", clubs.getString(0))
    }

    @Test fun rejectedApprovalCannotRestartTheSameVersion() {
        val state = JSONObject("""
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "Draft", "creator": "president", "organizer": "a", "phase": "review", "epoch": 2, "brief_hash": "digest"}},
              "flows": {"rejected": {"proposer": "president", "status": "rejected", "current": 0, "payload": {"type": "events.approve", "id": "event", "epoch": 2, "co_organizers": []}, "workflow": {"name": "Club president approval", "payload_kinds": ["events.approve"], "stages": [{"name": "Club president signatures", "approver": {"type": "club_presidents", "clubs": ["a"]}, "timeout_ms": null, "on_timeout": "reject"}]}, "records": [{"approvals": {}}]}}
            }
        """)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeEventDetail(state, "event", state.getJSONObject("events").getJSONObject("event"), false, officeActor(state, "device"), true, send = {})
        } } }
        compose.onNodeWithText(text(R.string.office_rejected_help)).scrollToIfScrollable().assertExists()
        compose.onNodeWithText(text(R.string.office_start_presidents)).assertDoesNotExist()
    }

    @Test fun clubPresidentStageShowsSignedCountAndHidesDelegation() {
        val state = JSONObject("""
            {
              "members": {
                "president": {"device": "device", "club": "a", "roles": ["president"]},
                "president-b": {"device": "president-b-device", "club": "b", "roles": ["president"]}
              },
              "events": {"event": {"title": "Draft", "creator": "president", "organizer": "a", "phase": "review", "epoch": 2, "brief_hash": "digest"}}
            }
        """)
        val flow = JSONObject("""
            {
              "proposer": "president",
              "status": "active",
              "current": 0,
              "payload": {"type": "events.approve", "id": "event", "epoch": 2, "co_organizers": ["b"]},
              "workflow": {"name": "Club president approval", "payload_kinds": ["events.approve"],
                "stages": [{"name": "Club president signatures", "approver": {"type": "club_presidents", "clubs": ["a", "b"]}, "timeout_ms": null, "on_timeout": "reject"}]},
              "records": [{"approvals": {"b": {"device": "president-b-device", "comment_hash": "digest"}}}]
            }
        """)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeFlowDetail(state, "flow", flow, false, true, "device", send = {})
        } } }
        val progress = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.office_approval_progress, 1, 2)
        compose.onNodeWithText(progress).scrollToIfScrollable().assertExists()
        compose.onNodeWithText(text(R.string.office_approval_presidents_no_delegation)).scrollToIfScrollable().assertExists()
        compose.onNodeWithText(text(R.string.office_delegate)).assertDoesNotExist()
    }

    @Test fun draftPlanHasAnExplicitEditEntryWithResetWarning() {
        val state = JSONObject("""
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "League", "creator": "president", "organizer": "a", "phase": "draft", "epoch": 2,
                "window": {"opens_ms": 1000, "closes_ms": 2000, "starts_ms": 3000}}},
              "organization": {
                "automation": {"worker": "president"},
                "schools": {"a": {"kind": "official"}, "b": {"kind": "independent"}},
                "resources": {"game": {"title": "Game"}},
                "plans": {"event": {"kind": "online", "schools": {"a": {"kind": "official", "participants": []}}, "candidates": ["game"],
                  "checkin_opens_ms": 1100, "checkin_closes_ms": 1900, "ends_ms": 2900, "final_epoch": null, "worker": "president"}}
              }
            }
        """)
        compose.setContent { JluManagerTheme {
            OfficeWorkflowEventPage(state, "online", "event", false, "device", { _ -> }, send = FormSender { _, _, _ -> }, onBack = {})
        } }
        compose.onNodeWithText(text(R.string.workflow_edit_plan)).scrollToIfScrollable().performClick()
        compose.onNodeWithText(text(R.string.workflow_edit_plan_warning)).scrollToIfScrollable().assertExists()
    }

    @Test fun competitionButtonOpensLeagueOnlyWhenSnapshotContainsIt() {
        val state = JSONObject("""
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "League", "creator": "president", "organizer": "a", "phase": "draft", "epoch": 2,
                "window": {"opens_ms": 3000}}},
              "organization": {
                "automation": {"worker": "president"},
                "schools": {"a": {"kind": "official"}},
                "resources": {"game": {"title": "Game"}},
                "plans": {"event": {"kind": "online", "schools": {"a": {"kind": "official", "participants": []}}, "candidates": ["game"],
                  "checkin_opens_ms": 1100, "checkin_closes_ms": 1900, "ends_ms": 2900, "final_epoch": null, "worker": "president",
                  "games": {"game": {"league": "spring", "resource": "game"}}}}
              }
            }
        """)
        var current by mutableStateOf(state)
        var opened: String? = null
        compose.setContent { JluManagerTheme {
            OfficeWorkflowEventPage(current, "online", "event", false, "device", { opened = it }, FormSender { _, _, _ -> }, onBack = {})
        } }
        compose.onNodeWithText(text(R.string.workflow_open_league)).scrollToIfScrollable().assertIsNotEnabled()
        val linked = JSONObject(state.toString()).put("leagues", JSONObject().put("spring", JSONObject()))
        compose.runOnIdle { current = linked }
        compose.onNodeWithText(text(R.string.workflow_open_league)).scrollToIfScrollable().assertIsEnabled().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("spring", opened) }
    }

    @Test fun draftStepBackKeepsTheTypedTitle() {
        compose.setContent { JluManagerTheme {
            OfficeDraftCreateFlow(state = JSONObject(), busy = false, canCreate = true, onBack = {}, onCreated = {}, send = FormSender { _, _, _ -> })
        } }
        compose.onNodeWithText(text(R.string.office_name)).performTextInput("春季联赛")
        compose.onNodeWithText(text(R.string.office_description)).performTextInput("周末校园活动")
        compose.onNodeWithText(text(R.string.office_next)).performClick()
        compose.onNodeWithText(text(R.string.office_closes)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText("春季联赛").assertExists()
        compose.onNodeWithText("周末校园活动").assertExists()
    }

    @Test fun failedBriefLoadDropsEditingWhenNoLongerEditable() {
        val state = JSONObject("""
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "Draft", "creator": "president", "organizer": "a", "phase": "draft", "epoch": 2, "brief_hash": "missing"}}
            }
        """)
        val event = state.getJSONObject("events").getJSONObject("event")
        var editable by mutableStateOf(true)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeRequestDetails(state, "event", event, false, editable, send = { _ -> })
        } } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.office_attachment_add)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { editable = false }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.office_attachment_add)).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText(text(R.string.office_description)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_amend)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_document_missing)).assertExists()
    }

    @Test fun reviewShowsRequiredChangesAsWorkflowFeedback() {
        val flow = JSONObject("""{"proposer":"member","template":"review","template_epoch":1,"status":"changes_requested","records":[{"ai_runs":[{"job":"job","output":"output-cid","error":null,"review":{"decision":"changes_requested","summary":"Consent document is missing","issues":["Attach the club consent document"]}}]}]}""")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            FlowReview(flow, null, null, false)
        } } }
        compose.onNodeWithText("Consent document is missing").assertExists()
        compose.onNodeWithText("Attach the club consent document").assertExists()
        compose.onNodeWithText(text(R.string.office_revision_required)).assertExists()
        compose.onNodeWithText(text(R.string.office_ai_retry)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_approve)).assertDoesNotExist()
    }

    @Test fun officeMemberCannotOpenDraftsOrIssueIdentity() {
        val state = JSONObject("""{"members": {"member": {"device": "device", "club": "a", "roles": []}}}""")
        dashboardPage(state, "device")
        compose.onNodeWithText(text(R.string.office_create)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_role_president)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.identity_issuance_draft)).assertDoesNotExist()
        compose.onNodeWithTag(OFFICE_TASK_ISSUANCE_TAG).assertDoesNotExist()
    }

    @Test fun unconfiguredWorkspaceShowsNoBusinessEntries() {
        dashboardPage(null, "")
        compose.onNodeWithText(text(R.string.office_disconnected_title)).assertExists()
        compose.onNodeWithText(text(R.string.office_workbench_flow)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_create)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_role_president)).assertDoesNotExist()
    }

    @Test fun revokingTheManagerSeatClosesTheOpenPlanEditor() {
        val state = JSONObject("""
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "League", "creator": "president", "organizer": "a", "phase": "draft", "epoch": 2,
                "window": {"opens_ms": 1000, "closes_ms": 2000, "starts_ms": 3000}}},
              "organization": {
                "automation": {"worker": "president"},
                "schools": {"a": {"kind": "official"}},
                "resources": {"game": {"title": "Game"}},
                "plans": {"event": {"kind": "online", "schools": {"a": {"kind": "official", "participants": []}}, "candidates": ["game"],
                  "checkin_opens_ms": 1100, "checkin_closes_ms": 1900, "ends_ms": 2900, "final_epoch": null, "worker": "president"}}
              }
            }
        """)
        var current by mutableStateOf(state)
        compose.setContent { JluManagerTheme {
            OfficeWorkflowEventPage(current, "online", "event", false, "device", { _ -> }, FormSender { _, _, _ -> }, onBack = {})
        } }
        compose.onNodeWithText(text(R.string.workflow_edit_plan)).scrollToIfScrollable().performClick()
        compose.onNodeWithText(text(R.string.workflow_edit_plan_warning)).scrollToIfScrollable().assertExists()
        val revoked = JSONObject(state.toString())
        revoked.getJSONObject("members").getJSONObject("president").put("roles", org.json.JSONArray())
        compose.runOnIdle { current = revoked }
        compose.onNodeWithText(text(R.string.workflow_edit_plan_warning)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.workflow_edit_plan)).assertDoesNotExist()
    }

    @Test fun presidentDashboardOffersCreateAndCountsOnlyOwnClubTasks() {
        val state = requestsState(
            members = """{"president": {"device": "president-device", "club": "a", "roles": ["president"]}}""",
            events = """{
                "a-draft": {"title": "A draft", "organizer": "a", "phase": "draft", "epoch": 1, "window": {"opens_ms": 1000}},
                "a-review": {"title": "A review", "organizer": "a", "phase": "review", "epoch": 2, "window": {"opens_ms": 2000}},
                "b-draft": {"title": "B draft", "organizer": "b", "phase": "draft", "epoch": 1}
            }""",
        )
        var drafts = 0
        dashboardPage(state, "president-device", onCreateDraft = { drafts++ })
        compose.onNodeWithText(text(R.string.office_tasks_scope_president)).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).scrollToIfScrollable().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, drafts) }
        compose.onNodeWithText(text(R.string.workspace_dashboard_event_tasks, 2)).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_ISSUANCE_TAG).assertExists()
    }

    @Test fun clubManagerDashboardMaintainsItsOwnClubWithoutCreate() {
        val state = requestsState(
            members = """{"manager": {"device": "manager-device", "club": "a", "roles": ["manager"]}}""",
            events = """{
                "a-draft": {"title": "A draft", "organizer": "a", "phase": "draft", "epoch": 1},
                "b-review": {"title": "B review", "organizer": "b", "phase": "review", "epoch": 1}
            }""",
        )
        dashboardPage(state, "manager-device")
        compose.onNodeWithText(text(R.string.office_tasks_scope_manager)).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.workspace_dashboard_event_tasks, 1)).assertExists()
    }

    @Test fun serverAdminDashboardMaintainsEveryClubWithoutCreate() {
        val state = requestsState(
            members = """{"admin-member": {"device": "admin-device", "club": "a", "roles": []}}""",
            events = """{
                "a-draft": {"title": "A draft", "organizer": "a", "phase": "draft", "epoch": 1},
                "b-draft": {"title": "B draft", "organizer": "b", "phase": "draft", "epoch": 1}
            }""",
            admins = """["admin-device"]""",
        )
        dashboardPage(state, "admin-device")
        compose.onNodeWithText(text(R.string.office_tasks_scope_admin)).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(OFFICE_TASK_PERSONNEL_TAG).scrollToIfScrollable().assertExists()
        compose.onNodeWithText(text(R.string.workspace_dashboard_event_tasks, 2)).assertExists()
    }

    @Test fun presidentServerAdminDashboardKeepsCreateAndShowsCrossClubScope() {
        val state = requestsState(
            members = """{"president": {"device": "president-device", "club": "a", "roles": ["president"]}}""",
            events = """{
                "a-draft": {"title": "A draft", "organizer": "a", "phase": "draft", "epoch": 1},
                "b-draft": {"title": "B draft", "organizer": "b", "phase": "draft", "epoch": 1}
            }""",
            admins = """["president-device"]""",
        )
        dashboardPage(state, "president-device")
        compose.onNodeWithText(text(R.string.office_tasks_scope_president_admin)).assertExists()
        compose.onNodeWithText(text(R.string.office_tasks_scope_admin)).assertDoesNotExist()
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).scrollToIfScrollable().assertExists()
        compose.onNodeWithText(text(R.string.workspace_dashboard_event_tasks, 2)).assertExists()
    }

    @Test fun memberDashboardOffersOnlyFormalActivities() {
        val state = requestsState(
            members = """{"member": {"device": "member-device", "club": "a", "roles": []}}""",
            events = """{
                "open": {"title": "Open activity", "organizer": "a", "phase": "open", "epoch": 1},
                "approved": {"title": "Approved activity", "organizer": "b", "phase": "approved", "epoch": 1},
                "draft": {"title": "Draft activity", "organizer": "a", "phase": "draft", "epoch": 1},
                "review": {"title": "Review activity", "organizer": "a", "phase": "review", "epoch": 1}
            }""",
            signups = """{"open": ["member"]}""",
        )
        dashboardPage(state, "member-device")
        compose.onNodeWithText(text(R.string.office_tasks_scope_member)).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.workspace_dashboard_event_tasks, 2)).assertExists()
    }

    @Test fun clubManagerDashboardShowsAnExplicitEmptyState() {
        val state = requestsState(
            members = """{"manager": {"device": "manager-device", "club": "a", "roles": ["manager"]}}""",
            events = """{"b-draft": {"title": "B draft", "organizer": "b", "phase": "draft", "epoch": 1}}""",
        )
        dashboardPage(state, "manager-device")
        compose.onNodeWithText(text(R.string.office_tasks_empty_club)).assertExists()
    }

    @Test fun memberDashboardShowsAnExplicitEmptyState() {
        val state = requestsState(
            members = """{"member": {"device": "member-device", "club": "a", "roles": []}}""",
            events = """{"draft": {"title": "Draft activity", "organizer": "a", "phase": "draft", "epoch": 1}}""",
        )
        dashboardPage(state, "member-device")
        compose.onNodeWithText(text(R.string.office_tasks_empty_member)).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).assertDoesNotExist()
    }

    @Test fun catalogOpensOneEventFromTheSharedCreateMenu() {
        val state = requestsState(
            members = """{"member": {"device": "member-device", "club": "a", "roles": []}}""",
            events = """{"open": {"title": "Open activity", "organizer": "a", "phase": "open", "epoch": 1,
                "window": {"opens_ms": 1000}}}""",
        )
        var opened = ""
        compose.setContent { JluManagerTheme {
            ActivityCatalog(state, "member-device", false, onCreate = {}, onOpenEvent = { opened = it }, onOpenCompetition = {})
        } }
        compose.onNodeWithTag("event:open").scrollToIfScrollable().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("open", opened) }
    }

    @Test fun workflowHomeOpensReportsAndSetupAsChildTasks() {
        val state = requestsState(
            members = """{"president": {"device": "device", "club": "a", "roles": ["president"]}}""",
            events = """{}""",
        )
        var reports = 0
        var setup = 0
        compose.setContent { JluManagerTheme {
            ActivityWorkflowHome(state, false, "device", null, {}, { _, _ -> }, { reports++ }, { setup++ }, {})
        } }
        node(R.string.workflow_reports).scrollToIfScrollable().performClick()
        node(R.string.workflow_setup).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(1, reports)
            org.junit.Assert.assertEquals(1, setup)
        }
    }

}

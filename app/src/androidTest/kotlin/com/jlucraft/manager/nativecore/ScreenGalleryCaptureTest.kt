package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in design-evidence gallery: one screenshot per page in its representative states. */
@RunWith(AndroidJUnit4::class)
class ScreenGalleryCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private fun page(content: @Composable () -> Unit) {
        compose.setContent {
            JluManagerTheme {
                Surface {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(SPACING_L),
                        verticalArrangement = Arrangement.spacedBy(SPACING_L),
                    ) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        // screencap races the window's first presented frame; give it a beat.
        Thread.sleep(500)
        captureScreen(name)
    }

    private fun fullPage(content: @Composable () -> Unit) {
        compose.setContent {
            JluManagerTheme { Surface(Modifier.fillMaxSize()) { content() } }
        }
        compose.waitForIdle()
    }

    /** Each route gets its own state while sharing one test activity and the current system theme. */
    private fun routes(names: List<String>, content: @Composable (Int) -> Unit) {
        var route by mutableStateOf(0)
        compose.setContent {
            JluManagerTheme {
                Surface(Modifier.fillMaxSize()) { key(route) { content(route) } }
            }
        }
        names.forEachIndexed { index, name ->
            compose.runOnIdle { route = index }
            shot(name)
        }
    }

    private val identityConfig = JSONObject().put("genesis_hash", "a".repeat(64)).put("genesis", JSONObject())
        .put("peer", "peer-fixture").put("address", "address-fixture").toString()

    private val leagueState = JSONObject("""{
        "seasons": {"2026-spring": {"title": "春季学期"}},
        "leagues": {
            "summer-cup": {"phase": "Registration", "organizer": "other-device", "rules": {"seats": 4, "cross_club": true},
                "teams": {"team-alpha": {"registered": true, "members": ["a1", "a2", "a3", "a4"]}, "team-beta": {"registered": false, "members": ["b1"]}},
                "team_scores": {"team-alpha": 12, "team-beta": 9}, "player_scores": {"alice": 5, "bob": 3}},
            "autumn-cup": {"phase": "Playing", "organizer": "device-fixture", "manual_pause": false, "rules": {"seats": 2, "cross_club": false}, "teams": {},
                "team_scores": {}, "player_scores": {}}
        },
        "disputes": {"dispute-1": {"league": "summer-cup", "member": "alice", "reason": "第三轮比分录入有误"}}
    }""")

    private val officeState = JSONObject("""{
        "events": {
            "event-autumn": {"title": "秋季迎新联赛", "phase": "open", "epoch": 3, "organizer": "computer-club",
                "co_organizers": ["math-club"], "brief_hash": "bafybriefautumnleague",
                "window": {"opens_ms": 1790000000000, "closes_ms": 1790100000000, "starts_ms": 1790200000000}},
            "event-draft": {"title": "冬季工作坊", "phase": "draft", "epoch": 1, "organizer": "computer-club",
                "brief_hash": "bafybriefdraftws",
                "window": {"opens_ms": 1791000000000, "closes_ms": 1791100000000, "starts_ms": 1791200000000}}
        },
        "signups": {"event-autumn": ["member-a", "member-b", "member-c"]},
        "tasks": {"task-venue": {"title": "场地与设备确认", "event": "event-autumn", "assignee": "member-li", "due_ms": 1790050000000}},
        "flows": {
            "flow-review": {"proposer": "member-li", "template": "活动审批", "template_epoch": 1, "status": "changes_requested", "current": 0,
                "payload": {"type": "events.approve", "id": "event-autumn", "epoch": 3, "co_organizers": ["math-club"]},
                "workflow": {"name": "活动审批", "payload_kinds": ["events.approve"],
                    "stages": [{"name": "材料审核", "approver": {"type": "member", "member": "admin-li"}, "on_timeout": "reject"}]},
                "records": [{"ai_runs": [{"job": "job-fixture", "output": "cid-output", "review": {"decision": "changes_requested",
                    "summary": "缺少安全预案", "issues": ["补充安全预案章节"]}}]}]}
        }
    }""")

    private fun filesFixture(): JSONObject {
        val manifest = JSONObject().put("files", JSONObject().put("plan/README.md", JSONObject().put("/", "cid-readme-1")))
        return JSONObject()
            .put("branches", JSONObject().put("main", JSONObject().put("commit", "cid-commit-1").put("author", "member-li")))
            .put("blocks", JSONObject().put("cid-commit-1",
                android.util.Base64.encodeToString(manifest.toString().toByteArray(), android.util.Base64.NO_WRAP)))
    }

    private val officeHomeState = officeState
        .put("organization", JSONObject("""{"automation": {"worker": "worker-li"},
            "schools": {"computer-club": {"kind": "official", "mua_requirements": "cid-mua-req", "mua_recipient": "校团委",
                "annual_requirements": "cid-annual-req", "annual_recipient": "教务处"}},
            "plans": {}, "resources": {}, "notices": {}}"""))
        .put("members", JSONObject("""{"member-li": {"device": "device-fixture", "club": "computer-club", "roles": ["president", "manager"]}}"""))
        .put("admins", JSONArray().put("device-fixture"))
        .put("files", filesFixture())

    @Test fun accountSettingsPages() {
        routes(listOf("android-30-account", "android-31-device", "android-32-admin", "android-33-audit", "android-34-help", "android-35-issuance")) { route ->
            when (route) {
                0 -> AccountHomeNav(officeHomeState, false, "device-fixture", true, true, {}, {}, {}, {}, {}, {}, {}, {})
                1 -> AccountDevicePage(JSONObject().put("peer", "device-fixture").put("public_key", "fixture-public-key").toString(), false) {}
                2 -> AccountAdminPage(officeHomeState, "device-fixture", false, {}, {})
                3 -> AccountAuditPage(officeHomeState, "device-fixture", false, {}, {})
                4 -> AccountHelpPage(false) {}
                5 -> AccountIssuancePage(
                    config = identityConfig, device = "device-fixture", busy = false,
                    directory = context.cacheDir, state = IdentityState(), facts = null,
                    knownMembers = listOf("member-li"), current = false, configured = true,
                    execute = { _, reply -> reply(JSONObject().put("error", true)) },
                    onStateChange = { _, _ -> }, onBack = {},
                )
            }
        }
    }

    @Test fun personnelListEditorAndReview() {
        compose.setContent {
            JluManagerTheme { PersonnelFlowHost(officeHomeState, "device-fixture", false, FormSender { _, _, _ -> }, {}) }
        }
        shot("android-37-personnel")
        compose.onNodeWithTag(personnelMemberTag("member-li")).scrollToIfScrollable().performClick()
        shot("android-38-personnel-edit")
        compose.onNodeWithText(text(R.string.office_next)).scrollToIfScrollable().performClick()
        shot("android-39-personnel-review")
    }

    @Test fun activityDraftCreationSteps() {
        compose.setContent {
            JluManagerTheme { OfficeDraftCreateFlow(officeHomeState, false, true, {}, {}, FormSender { _, _, _ -> }) }
        }
        shot("android-40-draft-basics")
        compose.onNodeWithText(text(R.string.office_name)).performTextInput("校园活动")
        compose.onNodeWithText(text(R.string.office_description)).performTextInput("主题审查夹具")
        compose.onNodeWithText(text(R.string.office_next)).scrollToIfScrollable().performClick()
        shot("android-41-draft-schedule")
        val start = LocalDateTime.now().plusDays(1)
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        listOf(R.string.office_opens, R.string.office_closes, R.string.office_starts).forEachIndexed { index, label ->
            compose.onNodeWithText(text(label)).scrollToIfScrollable().performTextInput(start.plusHours(index.toLong()).format(format))
        }
        compose.onNodeWithText(text(R.string.office_next)).scrollToIfScrollable().performClick()
        shot("android-42-draft-review")
    }

    @Test fun activityDraftEditor() {
        page {
            OfficeRequestDetails(officeHomeState, "event-draft", officeHomeState.getJSONObject("events").getJSONObject("event-draft"), false, true, {})
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.office_name)).fetchSemanticsNodes().isNotEmpty() }
        shot("android-43-draft-edit")
        compose.onNodeWithText(text(R.string.office_starts)).scrollToIfScrollable()
        shot("android-44-draft-edit-schedule")
    }

    @Test fun workflowEditorsAndReportCreation() {
        routes(listOf("android-45-automation", "android-46-school", "android-47-resource", "android-48-report-create")) { route ->
            when (route) {
                0 -> WorkflowAutomationPage(officeHomeState, "device-fixture", false, {}, {})
                1 -> WorkflowSchoolPage(officeHomeState, "device-fixture", false, FormSender { _, _, _ -> }, {})
                2 -> WorkflowResourcePage(officeHomeState, "device-fixture", false, FormSender { _, _, _ -> }, {})
                3 -> OfficeReportCreatePage(officeHomeState, "device-fixture", false, {}, {})
            }
        }
    }

    @Test fun leagueDetailAndSeasonEditors() {
        routes(listOf("android-49-league-detail", "android-50-league-teams", "android-51-disputes", "android-52-dispute-detail", "android-53-season-create")) { route ->
            when (route) {
                0 -> LeagueSummaryPage(leagueState, "summer-cup", false, {}, {}, {}, {}, {}, {})
                1 -> LeagueTeamsPage(leagueState, "summer-cup", false, {}, {})
                2 -> LeagueDisputesPage(leagueState, false, {}, {})
                3 -> LeagueDisputeDetailPage(leagueState, "dispute-1", false, {}, {})
                4 -> LeagueSeasonCreatePage(leagueState, false, {}, {})
            }
        }
    }

    @Test fun workspaceCreateAndTaskPages() {
        routes(listOf("android-54-create-menu", "android-55-tasks")) { route ->
            if (route == 0) ActivityCreateMenu(officeHomeState, "device-fixture", false, {}, {}, {}, {}, {})
            else TaskInbox(officeHomeState, "device-fixture", false, {})
        }
    }

    @Test fun identityCardStates() {
        var configured by mutableStateOf(false)
        fullPage {
            if (!configured) {
                val ops = remember {
                    IdentityOps("", "device-fixture", context.cacheDir, { it }, { _, _ -> }, {}, { _, _ -> }, {}, {}, IdentityState(), null)
                }
                IdentityHomePage(ops, IdentityState(), false, "", {}, {}, {}, {})
            } else {
                IdentityFlowHost(
                    mode = IdentityFlowMode.Startup,
                    config = identityConfig,
                    device = "device-fixture",
                    busy = false,
                    directory = context.cacheDir,
                    publicIdentity = "",
                    state = IdentityState(),
                    facts = null,
                    onConfig = {},
                    fetch = { _, reply -> reply(JSONObject().put("error", true)) },
                    discover = { reply -> reply(JSONObject().put("error", true)) },
                    onBusinessGeneses = {},
                    onFetchFailed = {},
                    execute = { _, reply ->
                        reply(JSONObject("""{"members": {"member-li": ["device-fixture"]}, "memberships": [["member-li", "computer-club"]],
                            "chairs": {"computer-club": "member-li"},
                            "permissions": {"perm-1": {"member": "member-li", "service": "game-ops", "permission": "admin", "revoked": false}}}"""))
                    },
                    onStateChange = { _, _ -> },
                    onBack = {},
                    autoSync = true,
                )
            }
        }
        shot("android-10-identity-empty")
        compose.runOnIdle { configured = true }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.identity_current)).fetchSemanticsNodes().isNotEmpty() }
        shot("android-11-identity-current")
    }

    @Test fun gameOpsCardStates() {
        var empty by mutableStateOf(false)
        page {
            if (!empty) {
                GameOpsContent(GameOpsTarget("10.0.0.8:25565", "peer-server", "svc-ops"), {}, false,
                    GameOpsState(listOf(GameInstance("survival", "running", "1.21.4", 7, 20)), "survival", "fixture server log"), {}, {})
            } else {
                GameOpsContent(GameOpsTarget(), {}, false, GameOpsState(), {}, {})
            }
        }
        shot("android-12-gameops")
        compose.runOnIdle { empty = true }
        shot("android-13-gameops-empty")
    }

    @Test fun accountConnectionState() {
        compose.setContent {
            JluManagerTheme {
                AccountConnectionPage(
                    state = null, identity = "device-fixture", member = null, identityReady = true,
                    address = "10.0.0.8:29637", peer = "peer-org", genesisHash = "a".repeat(64),
                    connected = true, busy = false,
                    failure = null, onConnectTarget = {},
                    onLink = {}, discover = { reply -> reply(JSONObject().put("error", true)) }, onBack = {},
                )
            }
        }
        shot("android-14-connection")
        compose.onNodeWithText(text(R.string.account_connection_change)).performClick()
        shot("android-14b-connection-steps")
    }

    @Test fun seasonCardStates() {
        page { SeasonCard(leagueState, "device-fixture", false, onOpenCreateSeason = {}, onOpenSchedule = {}) {} }
        shot("android-15-season-schedule")
        compose.onNodeWithText(text(R.string.governance_create_season)).scrollToIfScrollable()
        shot("android-16-season-create-entry")
    }

    @Test fun createLeagueFlowState() {
        compose.setContent {
            JluManagerTheme {
                LeagueCreateFlow(
                    league = "", seats = "4", crossClub = true, host = "", scorer = "",
                    connected = true, busy = false, seatsValid = true, nameTaken = false,
                    identityOptions = emptyList(),
                    onLeagueChange = {}, onSeatsChange = {}, onCrossClubChange = {},
                    onHostChange = {}, onScorerChange = {},
                    send = FormSender { _, _, _ -> }, onCreated = {}, onBack = {},
                )
            }
        }
        shot("android-17-create-league")
    }

    @Test fun leagueListSectionState() {
        page { LeagueListSection(leagueState, false, onStart = {}) }
        shot("android-18-league-list")
        compose.onNodeWithText("autumn-cup").scrollToIfScrollable()
        shot("android-19-league-list-playing")
    }

    @Test fun officeCardStates() {
        page { OfficeCard(officeState, false, "device-fixture") }
        shot("android-20-office-events")

    }

    @Test fun officeApprovalState() {
        page { OfficeCard(officeState, false, "device-fixture", approvals = true) }
        shot("android-21-office-flows")
    }

    @Test fun officeWorkflowsCardStates() {
        var setup by mutableStateOf(false)
        fullPage {
            if (setup) {
                OfficeWorkflowSetupHome(officeHomeState, "device-fixture", false, {}, {}, {}, {})
            } else {
                ActivityWorkflowHome(officeHomeState, false, "device-fixture", null,
                    onOpenKind = {}, onOpenEvent = { _, _ -> }, onOpenReports = {},
                    onOpenSetup = { setup = true }, onBack = {})
            }
        }
        shot("android-23-workflows")
        compose.onNodeWithText(text(R.string.workflow_setup)).performClick()
        shot("android-24-workflow-setup")
    }

    @Test fun workspaceNavigationStates() {
        var destination by mutableStateOf(WorkspaceDestination.Home)
        compose.setContent {
            JluManagerTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding().fillMaxSize()) {
                    WorkspaceNavigation(destination, false, { destination = it }) {
                        when (destination) {
                            WorkspaceDestination.Home -> WorkspaceDashboard(
                                officeHomeState, "device-fixture", false, {}, {}, {}, {}, {}, {}, {},
                            )
                            WorkspaceDestination.Activities -> ActivityCatalog(
                                officeHomeState, "device-fixture", false, {}, {}, {},
                            )
                            WorkspaceDestination.Tasks -> ActivityWorkflowHome(
                                officeHomeState, false, "device-fixture", null, {}, { _, _ -> }, {}, {}, {},
                            )
                            WorkspaceDestination.Settings -> Text(text(R.string.account_title))
                        }
                    }
                    }
                }
            }
        }
        compose.waitForIdle()
        shot("android-25-workspace-home")
        compose.onNodeWithText(text(R.string.workspace_nav_activities)).performClick()
        shot("android-26-workspace-activities")
    }

}

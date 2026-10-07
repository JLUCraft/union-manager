package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
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

    private val identityConfig = JSONObject().put("genesis_hash", "a".repeat(64)).put("genesis", JSONObject())
        .put("peer", "peer-fixture").put("address", "address-fixture").toString()

    private val leagueState = JSONObject("""{
        "seasons": {"2026-spring": {"title": "春季学期"}},
        "leagues": {
            "summer-cup": {"phase": "Registration", "organizer": "other-device", "rules": {"seats": 4, "cross_club": true},
                "teams": {"team-alpha": {"registered": true, "members": ["a1", "a2", "a3", "a4"]}, "team-beta": {"registered": false, "members": ["b1"]}},
                "team_scores": {"team-alpha": 12, "team-beta": 9}, "player_scores": {"alice": 5, "bob": 3}},
            "autumn-cup": {"phase": "Playing", "organizer": "device-fixture", "manual_pause": false, "rules": {"seats": 2, "cross_club": false}, "teams": {}}
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
        .put("office", JSONObject("""{"automation": {"worker": "worker-li"},
            "schools": {"computer-club": {"kind": "official", "mua_requirements": "cid-mua-req", "mua_recipient": "校团委",
                "annual_requirements": "cid-annual-req", "annual_recipient": "教务处"}},
            "plans": {}, "resources": {}, "notices": {}}"""))
        .put("members", JSONObject("""{"member-li": {"device": "device-fixture", "club": "computer-club"}}"""))
        .put("files", filesFixture())

    @Test fun identityCardStates() {
        var configured by mutableStateOf(false)
        page {
            if (!configured) {
                IdentityCard("", "device-fixture", false, context.cacheDir, onConfig = {}, execute = { _, _ -> })
            } else {
                IdentityCard(identityConfig, "device-fixture", false, context.cacheDir, onConfig = {}, execute = { _, reply ->
                    reply(JSONObject("""{"members": {"member-li": ["device-fixture"]}, "memberships": [["member-li", "computer-club"]],
                        "chairs": {"computer-club": "member-li"},
                        "permissions": {"perm-1": {"member": "member-li", "service": "game-ops", "permission": "admin", "revoked": false}}}"""))
                })
            }
        }
        shot("android-10-identity-empty")
        compose.runOnIdle { configured = true }
        compose.onNodeWithText(text(R.string.identity_sync)).performScrollTo().performClick()
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

    @Test fun connectionCardState() {
        page {
            ConnectionCard("10.0.0.8:29637", "peer-org", "a".repeat(64), "device-fixture", text(R.string.management_synced),
                connected = true, busy = false, {}, {}, {}, {})
        }
        shot("android-14-connection")
    }

    @Test fun seasonCardStates() {
        page { SeasonCard(leagueState, "device-fixture", false) {} }
        shot("android-15-season-schedule")
        compose.onNodeWithText(text(R.string.governance_resolve_dispute)).performScrollTo()
        shot("android-16-season-dispute")
    }

    @Test fun createLeagueCardState() {
        page { CreateLeagueCard("", "4", true, "", "", connected = true, busy = false, seatsValid = true, {}, {}, {}, {}, {}, {}) }
        shot("android-17-create-league")
    }

    @Test fun leagueListSectionState() {
        page { LeagueListSection(leagueState, false) {} }
        shot("android-18-league-list")
        compose.onNodeWithText("autumn-cup").performScrollTo()
        shot("android-19-league-list-playing")
    }

    @Test fun officeCardStates() {
        page { OfficeCard(officeState, false, "device-fixture") {} }
        shot("android-20-office-events")

    }

    @Test fun officeApprovalState() {
        page { OfficeCard(officeState, false, "device-fixture", approvals = true) {} }
        shot("android-21-office-flows")
    }

    @Test fun officeWorkflowsCardStates() {
        page { OfficeWorkflowsCard(officeHomeState, false, "device-fixture", null, { _, _ -> }) {} }
        shot("android-23-workflows")
        compose.onNodeWithText(text(R.string.workflow_setup)).performClick()
        shot("android-24-workflow-setup")
    }

    @Test fun officeHomeNavigationStates() {
        compose.setContent {
            JluManagerTheme {
                Surface(Modifier.fillMaxSize()) {
                    OfficeHomeNav(officeHomeState, false, "device-fixture", null, true, { _, _ -> }, {})
                }
            }
        }
        compose.waitForIdle()
        shot("android-25-office-menu")
        compose.onNodeWithText(text(R.string.office_flows)).performClick()
        shot("android-26-office-detail")
    }
}

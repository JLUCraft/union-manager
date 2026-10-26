package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Local rendering fixtures only: no system authentication, network calls, or committed writes. */
@RunWith(AndroidJUnit4::class)
class LeafGalleryCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pin = "a".repeat(64)
    private val head = "c".repeat(64)
    private fun node(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun click(id: Int) = node(id).scrollToIfScrollable().performClick()
    private fun input(id: Int, value: String) = node(id).scrollToIfScrollable().performTextInput(value)
    /** The stepper's edit box holds its accessible name; the visible label is a sibling Text. */
    private fun inputStepper(id: Int, value: String) =
        compose.onNodeWithContentDescription(context.getString(id)).scrollToIfScrollable().performTextInput(value)
    private fun back() = compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        captureScreen(name)
    }

    private fun screen(content: @Composable () -> Unit) {
        compose.setContent { JluManagerTheme { Surface(Modifier.fillMaxSize()) { content() } } }
    }

    private fun routes(names: List<String>, content: @Composable (Int) -> Unit) {
        var route by mutableStateOf(0)
        screen { key(route) { content(route) } }
        names.forEachIndexed { index, name -> compose.runOnIdle { route = index }; shot(name) }
    }

    private val publicIdentity by lazy {
        val secret = UnionNative.generateSecret()
        try { UnionNative.publicIdentity(secret) } finally { secret.fill(0) }
    }
    private val publicPeer get() = JSONObject(publicIdentity).getString("peer")
    private val config get() = JSONObject().put("genesis_hash", pin).put("genesis", JSONObject())
        .put("peer", publicPeer).put("address", "/ip4/127.0.0.1/udp/25565/quic-v1").toString()
    private fun facts() = JSONObject("""{
        "federation":"gallery-federation",
        "members":{"member-a":["device"],"member-b":["other-device"]},
        "memberships":[["member-a","club-a"],["member-b","club-a"]],
        "chairs":{"club-a":"member-a"},"permissions":{},"verifiers":{},
        "checkpoint":{"genesis":"$pin","sequence":7,"head":"$head"}
    }""")
    private fun record(action: JSONObject) = JSONObject().put("federation", "gallery-federation")
        .put("genesis", pin).put("sequence", 8).put("previous", head)
        .put("action", action).put("signatures", JSONArray())
    private fun ops(proposal: String = ""): IdentityOps {
        val facts = facts()
        return IdentityOps(
            config, "device", context.cacheDir,
            review = { JSONObject(it).getJSONObject("config").toString() },
            execute = { request, reply ->
                reply(if (request.optString("operation") == "draft") record(request.getJSONObject("action")) else facts)
            },
            onConfig = {}, onStateChange = { _, _ -> }, onBusinessGeneses = {}, onFetchFailed = {},
            initialState = IdentityState().received(identityMember(facts, "device")), initialFacts = facts,
        ).also { if (proposal.isNotBlank()) it.editProposal(proposal) }
    }

    private fun organization(): JSONObject {
        val built = JSONObject(UnionNative.management(JSONObject().put("operation", "build_genesis")
            .put("federation", "gallery-federation").put("quorum", 1)
            .put("founders", JSONArray().put(JSONObject().put("member", "member-a").put("club", "club-a")
                .put("identity", JSONObject(publicIdentity)))).toString()))
        return JSONObject().put("identity", built).put("application", JSONObject().put("genesis_hash", "b".repeat(64)))
    }

    private fun state(): JSONObject = JSONObject("""{
        "admins":["device"],"members":{"member-a":{"device":"device","club":"club-a","roles":["president","manager"]}},
        "events":{
          "draft":{"title":"Campus workshop","creator":"member-a","organizer":"club-a","phase":"draft","epoch":2,"brief_hash":"missing",
            "window":{"opens_ms":1793000000000,"closes_ms":1793100000000,"starts_ms":1793200000000}},
          "review":{"title":"Campus competition","creator":"member-a","organizer":"club-a","co_organizers":[],"phase":"review","epoch":2,"brief_hash":"missing"},
          "open":{"title":"Open activity","creator":"member-a","organizer":"club-a","phase":"open","epoch":2,"brief_hash":"missing"}
        },"signups":{"open":["member-a"]},"tasks":{},"leagues":{},"flows":{},
        "organization":{"automation":{"worker":"member-a"},
          "schools":{"club-a":{"title":"club-a","kind":"official"}},
          "resources":{"game-a":{"title":"Game resource","seats":4}},"plans":{},"reports":{},"notices":{}}
    }""")

    @Test fun identityLeafPages() {
        val ops = ops(record(JSONObject().put("type", "register_service").put("service", "application")
            .put("audience", publicPeer).put("club", "club-a")).toString())
        val fetched = organization()
        routes(listOf("leaf-identity-summary", "leaf-identity-import", "leaf-identity-technical", "leaf-identity-facts",
            "leaf-identity-record-summary", "leaf-identity-share", "leaf-identity-enrollment", "leaf-identity-genesis-facts")) { route ->
            when (route) {
                0 -> IdentitySummaryPage(ops, ops.state, false, {}, {}, {}, {})
                1 -> IdentityImportPage(ops, false, {}, {}, {})
                2 -> IdentityTechnicalPage(ops, false, {}, {})
                3 -> IdentityFactsPage(ops, false, {})
                4 -> IdentityRecordsPage(ops, false, listOf("member-a", "member-b"), {})
                5 -> IdentitySharePage(publicIdentity, false, {})
                6 -> IdentityEnrollmentPage(ops, false, {})
                7 -> GenesisFactsPage(fetched, {})
            }
        }
    }

    @Test fun identityRecordEditors() {
        val ops = ops()
        screen { IdentityRecordsPage(ops, false, listOf("member-a", "member-b"), {}) }
        click(R.string.identity_create_change_show)
        shot("leaf-identity-change-form")
        click(R.string.identity_create_change_hide)
        click(R.string.identity_import_record)
        shot("leaf-identity-record-import")
        input(R.string.identity_proposal, record(JSONObject().put("type", "register_service").put("service", "application")
            .put("audience", publicPeer).put("club", "club-a")).toString())
        click(R.string.identity_review_changes)
        click(R.string.identity_raw_record_show)
        shot("leaf-identity-record-raw")
    }

    @Test fun enrollmentRecordEditor() {
        val ops = ops()
        screen { IdentityEnrollmentPage(ops, false, {}) }
        click(R.string.identity_import_record)
        shot("leaf-enrollment-record-import")
        input(R.string.startup_identity_enrollment_label, record(JSONObject().put("type", "confirm_member")
            .put("member", "new-member").put("club", "club-a").put("device", publicPeer)
            .put("evidence", "manual").put("verifier", JSONObject.NULL)).toString())
        click(R.string.identity_review_changes)
        shot("leaf-enrollment-record-review")
    }

    @Test fun discoveredOrganizationTrustAndLostMaterial() {
        val fetched = organization()
        val restore = StateRestorationTester(compose)
        restore.setContent {
            JluManagerTheme { IdentityFlowHost(
                mode = IdentityFlowMode.Startup, config = "", device = "device", busy = false,
                directory = context.cacheDir, publicIdentity = publicIdentity, state = IdentityState(), facts = null,
                onConfig = {}, fetch = { _, reply -> reply(fetched) },
                discover = { reply -> reply(JSONObject().put("servers", JSONArray().put(JSONObject()
                    .put("peer", "gallery-peer").put("address", "gallery-address").put("federation", "Gallery organization")))) },
                onBusinessGeneses = {}, onFetchFailed = {}, execute = { _, reply -> reply(facts()) },
                onStateChange = { _, _ -> }, onBack = {},
            ) }
        }
        click(R.string.startup_identity_find_org)
        shot("leaf-identity-discovery")
        click(R.string.startup_identity_find_org_action)
        shot("leaf-identity-discovered-organizations")
        compose.onNode(hasText("gallery-peer", substring = true) and hasClickAction()).scrollToIfScrollable().performClick()
        shot("leaf-identity-organization")
        click(R.string.startup_identity_organization_fetch)
        shot("leaf-identity-trust")
        click(R.string.startup_identity_trust_details)
        shot("leaf-identity-trust-facts")
        back()
        restore.emulateSavedInstanceStateRestore()
        shot("leaf-identity-trust-material-lost")
    }

    @Test fun authorizedIssuancePages() {
        val ops = ops()
        screen { IssuanceFlowHost(ops, false, true, true, ops.facts, knownMemberIds(ops.facts), {}) }
        shot("leaf-issuance-authorized")
        click(R.string.identity_issuance_open_roster)
        shot("leaf-issuance-roster")
        back()
        click(R.string.identity_issuance_open_target)
        shot("leaf-issuance-target")
        input(R.string.identity_field_member, "new-member")
        input(R.string.identity_issuance_device, publicPeer)
        click(R.string.identity_issuance_draft)
        shot("leaf-issuance-review")
        back(); back()
        click(R.string.identity_issuance_open_import)
        shot("leaf-issuance-import")
    }

    @Test fun consensusEditorAndPreview() {
        screen { AccountAdminPage(state(), "device", false, {}, {}) }
        click(R.string.business_admin_open)
        click(R.string.business_replica_add)
        shot("leaf-consensus-editor")
        input(R.string.business_replica_id, "1")
        // The replica peer field has its own label now (3.2.3 split it from the audience field).
        input(R.string.business_replica_peer, publicPeer)
        input(R.string.business_replica_addresses, "/ip4/127.0.0.1/udp/25565/quic-v1/p2p/$publicPeer")
        click(R.string.business_replicas_preview)
        node(R.string.business_review).scrollToIfScrollable()
        shot("leaf-consensus-preview")
        click(R.string.business_submit)
        shot("leaf-consensus-confirmation-dialog")
    }

    @Test fun schoolProfileLaterSteps() {
        screen { WorkflowSchoolPage(state(), "device", false, FormSender { _, _, _ -> }, {}) }
        compose.onNodeWithText("club-a").scrollToIfScrollable().performClick()
        click(R.string.workflow_official)
        click(R.string.workflow_next)
        shot("leaf-school-mua")
        input(R.string.workflow_mua_requirements, "material-fixture")
        input(R.string.workflow_mua_recipient, "MUA reviewer")
        click(R.string.workflow_next)
        shot("leaf-school-annual")
    }

    @Test fun resourceLaterSteps() {
        screen { WorkflowResourcePage(state(), "device", false, FormSender { _, _, _ -> }, {}) }
        input(R.string.workflow_resource_id, "resource-a")
        input(R.string.workflow_resource_title_field, "Game resource")
        click(R.string.workflow_next)
        shot("leaf-resource-connection")
        input(R.string.workflow_resource_address, "/ip4/127.0.0.1/udp/25565/quic-v1")
        input(R.string.workflow_resource_peer, publicPeer)
        click(R.string.workflow_next)
        shot("leaf-resource-rules")
        input(R.string.workflow_resource_host, publicPeer)
        input(R.string.workflow_resource_scorer, publicPeer)
        inputStepper(R.string.workflow_resource_seats, "4")
        input(R.string.workflow_resource_operations, "11111111-1111-4111-8111-111111111111")
        input(R.string.workflow_resource_instance, "survival")
        click(R.string.workflow_next)
        shot("leaf-resource-review")
    }

    @Test fun businessConnectionEditorPages() {
        screen {
            var address by remember { mutableStateOf("") }
            var peer by remember { mutableStateOf("") }
            var genesis by remember { mutableStateOf(pin) }
            StartupConnectionStage(
                identity = identityMember(facts(), "device"), identityReady = true,
                address = address, peer = peer, genesisHash = genesis, busy = false,
                onAddressChange = { address = it }, onPeerChange = { peer = it }, onGenesisChange = { genesis = it },
                onConnect = {}, onLink = {},
                discover = { reply -> reply(JSONObject().put("servers", JSONArray().put(JSONObject()
                    .put("address", "/ip4/127.0.0.1/udp/25565/quic-v1").put("peer", publicPeer)
                    .put("federation", "Gallery business host").put("genesis_hash", pin)
                    .put("application", JSONObject().put("genesis_hash", "b".repeat(64)))))) },
                onRecheckIdentity = {},
            )
        }
        shot("leaf-connection-method")
        click(R.string.startup_connection_method_manual)
        shot("leaf-connection-manual-editor")
        back()
        click(R.string.connection_link)
        shot("leaf-connection-link-editor")
        back()
        click(R.string.startup_connection_method_discovery)
        click(R.string.connection_discover)
        shot("leaf-connection-discovery-result")
    }

    @Test fun businessConnectionBusyAndFailure() {
        routes(listOf("leaf-connection-busy", "leaf-connection-failure")) { route ->
            StartupConnectionStage(
                identity = identityMember(facts(), "device"), identityReady = true,
                address = "/ip4/127.0.0.1/udp/25565/quic-v1", peer = publicPeer, genesisHash = pin,
                busy = route == 0, failure = if (route == 1) "Fixture authority unavailable" else null,
                onAddressChange = {}, onPeerChange = {}, onGenesisChange = {}, onConnect = {}, onLink = {},
                discover = { reply -> reply(JSONObject().put("servers", JSONArray())) }, onRecheckIdentity = {},
            )
        }
    }

    @Test fun leagueCreationLaterSteps() {
        screen {
            LeagueCreateFlow(
                league = "gallery-cup", seats = "4", crossClub = true, host = publicPeer, scorer = publicPeer,
                connected = true, busy = false, seatsValid = true, nameTaken = false,
                identityOptions = listOf(publicPeer), onLeagueChange = {}, onSeatsChange = {}, onCrossClubChange = {},
                onHostChange = {}, onScorerChange = {}, send = FormSender { _, _, _ -> }, onCreated = {}, onBack = {},
            )
        }
        click(R.string.office_next)
        shot("leaf-league-hosts")
        click(R.string.office_next)
        shot("leaf-league-confirmation")
    }

    @Test fun seasonAndScheduleDestinations() {
        val state = state().put("seasons", JSONObject().put("term", JSONObject().put("title", "Current term")))
        routes(listOf("leaf-league-season-destination", "leaf-league-schedule-editor")) { route ->
            if (route == 0) LeagueSeasonPage(state, "device", false, {}, {}, {}, {})
            else LeagueSchedulePage(state, false, {}, {})
        }
    }

    @Test fun operationsTargetAndActions() {
        screen { LeagueOpsFlow({ _, reply -> reply(JSONObject().put("error", "No external service in this rendering fixture")) }, {}) }
        shot("leaf-operations-target")
        input(R.string.ops_address, "/ip4/127.0.0.1/udp/25565/quic-v1")
        input(R.string.ops_peer, publicPeer)
        input(R.string.assistant_service, "11111111-1111-4111-8111-111111111111")
        click(R.string.league_ops_open)
        shot("leaf-operations-actions")
    }

    private fun plan(kind: String): JSONObject = JSONObject().put("kind", kind).put("schools", JSONObject()
        .put("club-a", JSONObject().put("kind", "official").put("participants", JSONArray().put("member-a"))))
        .put("candidates", JSONArray().put("game-a")).put("checkin_opens_ms", 1793000000000)
        .put("checkin_closes_ms", 1793100000000).put("ends_ms", 1793200000000)
        .put("final_epoch", JSONObject.NULL).put("opinions", JSONObject()).put("consents", JSONObject()).put("rewards", JSONObject())

    private fun planDraft(kind: String) {
        screen { OfficeWorkflowEventPage(state(), kind, "draft", false, "device", {}, FormSender { _, _, _ -> }, {}) }
        shot("leaf-$kind-schools-participants")
        compose.onNodeWithText("club-a").scrollToIfScrollable().performClick()
        click(R.string.workflow_next)
        shot("leaf-$kind-schedule-candidates")
    }
    @Test fun onlinePreparationDraft() = planDraft("online")
    @Test fun meetingPreparationDraft() = planDraft("meeting")

    private fun planReview(kind: String) {
        val state = state()
        state.getJSONObject("organization").getJSONObject("plans").put("draft", plan(kind))
        screen { OfficeWorkflowEventPage(state, kind, "draft", false, "device", {}, FormSender { _, _, _ -> }, {}) }
        shot("leaf-$kind-material")
        click(R.string.workflow_next)
        shot("leaf-$kind-opinion-rewards-finalize")
        click(R.string.workflow_edit_plan)
        shot("leaf-$kind-plan-edit")
    }
    @Test fun onlineMaterialAndReview() = planReview("online")
    @Test fun meetingMaterialAndReview() = planReview("meeting")

    @Test fun reportsAndRevisionPages() {
        val state = state()
        state.getJSONObject("organization").put("reports", JSONObject()
            .put("report-draft", JSONObject().put("status", "draft").put("recipient", "MUA reviewer").put("evidence", "evidence-fixture").put("output", "output-fixture"))
            .put("report-published", JSONObject().put("status", "published").put("recipient", "School reviewer").put("evidence", "evidence-fixture").put("output", "published-fixture")))
        routes(listOf("leaf-reports-list", "leaf-report-draft-revision", "leaf-report-published-revision")) { route ->
            when (route) {
                0 -> OfficeReportsPage(state, "device", false, {}, {}, {})
                1 -> OfficeReportDetailPage(state, "device", "report-draft", false, {}, {})
                2 -> OfficeReportDetailPage(state, "device", "report-published", false, {}, {})
            }
        }
    }

    @Test fun workflowEventListsAndFinalizedDetails() {
        val state = state()
        val finished = plan("online").put("final_epoch", 2).put("polished", "polished-fixture").put("summary", "summary-fixture")
            .put("games", JSONObject().put("game-a", JSONObject().put("league", "league-fixture").put("started", true)
                .put("results", JSONObject().put("member-a", 7))))
            .put("awards", JSONObject().put("member-a", JSONObject().put("points", 7).put("material", "award-fixture")))
        state.getJSONObject("organization").getJSONObject("plans").put("open", finished)
        state.getJSONObject("leagues").put("league-fixture", JSONObject().put("phase", "Playing"))
        routes(listOf("leaf-online-event-list", "leaf-meeting-event-list", "leaf-finalized-workflow-detail")) { route ->
            when (route) {
                0 -> OfficeWorkflowEventsPage(state, "online", false, {}, {})
                1 -> OfficeWorkflowEventsPage(state, "meeting", false, {}, {})
                2 -> OfficeWorkflowEventPage(state, "online", "open", false, "device", {}, FormSender { _, _, _ -> }, {})
            }
        }
    }

    @Test fun activityAndApprovalDetails() {
        val state = state()
        state.put("flows", JSONObject("""{"approval":{
          "proposer":"member-a","template":"approval","template_epoch":1,"status":"active","current":0,
          "payload":{"type":"events.approve","id":"review","epoch":2,"co_organizers":[]},
          "workflow":{"name":"Activity review","payload_kinds":["events.approve"],"stages":[{"name":"Material review","approver":{"type":"member","member":"member-a"},"timeout_ms":null,"on_timeout":"reject"}]},
          "records":[{"approvals":{},"ai_runs":[]}]}}"""))
        routes(listOf("leaf-activity-draft-detail", "leaf-activity-open-detail", "leaf-activity-edit-page", "leaf-approval-detail", "leaf-approval-decision")) { route ->
            when (route) {
                0 -> TaskPage("Draft detail", false, {}) { OfficeEventDetailScreen(state, false, "device", "draft", {}, {}) }
                1 -> TaskPage("Activity detail", false, {}) { OfficeEventDetailScreen(state, false, "device", "open", {}, {}) }
                2 -> OfficeEventEditScreen(state, false, "device", "draft", {}, FormSender { _, _, _ -> })
                3 -> TaskPage("Approval detail", false, {}) { OfficeFlowDetailScreen(state, false, "device", "approval", {}, {}) }
                4 -> OfficeFlowDecisionScreen(state, false, "device", "approval", {}, {})
            }
        }
    }

    @Test fun loadedActivityBrief() {
        val file = "baguqeera5teby6nt6j6lbzcjqjzm3b7yu7iuh3zp3drdipsyn2huwapfkiia"
        val snapshot = JSONObject().put("revision", 0).put("admins", JSONArray()).put("members", JSONObject()).put("clock_ms", 0)
            .put("identity", JSONObject().put("genesis", pin).put("namespace", "organization").put("audience", publicPeer)
                .put("service", "application").put("club", "club-a"))
            .put("files", JSONObject().put("branches", JSONObject()).put("blocks", JSONObject()
                .put("bafkreiekraqzk6val4di6hv4wgk4huv23srvqkh6rhd3wdvhmduvp3kmoa", "eyJkZXNjcmlwdGlvbiI6IkFjdGl2aXR5IG1hdGVyaWFscyBmb3IgdmlzdWFsIHJldmlldy4iLCJhdHRhY2htZW50IjpudWxsfQ")
                .put(file, "eyJjaGVja3N1bSI6IjhhODgyMTk1N2FhMDVmMDY4ZjFlYmNiMTk1YzNkMmJhZGNhMzU4MjhmZTg5YzdiYjBlYTc2MGU5NTdlZDRjNzAiLCJkZXB0aCI6MCwibGF5b3V0Ijp7InRyZWUiOnsiLyI6ImJhZmtyZWlla3JhcXprNnZhbDRkaTZodjR3Z2s0aHV2MjNzcnZxa2g2cmhkM3dkdmhtZHV2cDNrbW9hIn0sInR5cGUiOiJmdWxsIn0sInBhcmVudCI6bnVsbCwic2l6ZSI6NzMsInR5cGUiOiJmaWxlIn0")))
        val event = state().getJSONObject("events").getJSONObject("draft").put("brief_hash", file)
        screen { TaskPage("Activity materials", false, {}) { OfficeRequestDetails(snapshot, "draft", event, false, true, {}) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Activity materials for visual review.").fetchSemanticsNodes().isNotEmpty()
        }
        shot("leaf-activity-loaded-brief")
        click(R.string.office_amend)
        shot("leaf-activity-loaded-editor")
    }
}

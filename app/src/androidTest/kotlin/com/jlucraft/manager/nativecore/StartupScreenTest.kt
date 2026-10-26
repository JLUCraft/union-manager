package com.jlucraft.manager.nativecore

import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun node(id: Int) = compose.onNodeWithText(context.getString(id))

    private val member = MemberIdentityView(
        memberId = "member-42",
        devices = setOf("device-42"),
        clubs = setOf("club-a"),
        chairOf = emptySet(),
        permissions = emptySet(),
    )
    private val verified = IdentityState().received(member)
    private val configuration = JSONObject()
        .put("genesis_hash", "a".repeat(64))
        .put("genesis", JSONObject())
        .put("peer", "test-peer")
        .put("address", "test-address")
        .toString()
    private val publicIdentityJson = JSONObject()
        .put("peer", "device-42")
        .put("public_key", "public-key")
        .toString()

    /** The native review path is replaced by the same normalization the app expects from it. */
    private val passThroughReview: (String) -> String = { request ->
        JSONObject(request).getJSONObject("config").toString()
    }

    private fun identityStage(
        configuration: String = "",
        identityState: IdentityState = IdentityState(),
        busy: Boolean = false,
        publicIdentity: String = publicIdentityJson,
        onConfig: (String) -> Unit = {},
        execute: (JSONObject, (JSONObject) -> Unit) -> Unit = { _, reply -> reply(JSONObject().put("error", true)) },
    ) {
        compose.setContent {
            JluManagerTheme {
                StartupIdentityStage(
                    configuration = configuration,
                    device = "device-42",
                    publicIdentity = publicIdentity,
                    identityState = identityState,
                    facts = null,
                    busy = busy,
                    directory = context.cacheDir,
                    onConfig = onConfig,
                    fetch = { _, reply -> reply(JSONObject().put("error", true)) },
                    discover = { reply -> reply(JSONObject().put("error", true)) },
                    onBusinessGeneses = {},
                    onFetchFailed = {},
                    execute = execute,
                    onStateChange = { _, _ -> },
                    review = passThroughReview,
                )
            }
        }
    }

    private fun assertNoBusinessPages() {
        node(R.string.identity_issuance_title).assertDoesNotExist()
        node(R.string.identity_actions_title).assertDoesNotExist()
        node(R.string.office_workbench_title).assertDoesNotExist()
        node(R.string.office_title).assertDoesNotExist()
        node(R.string.workspace_nav_activities).assertDoesNotExist()
    }

    private fun discoveredServer(peer: String, applicationHash: String? = null): JSONObject = JSONObject()
        .put("address", "/ip4/127.0.0.1/udp/25565/quic-v1")
        .put("peer", peer)
        .put("federation", "test-federation")
        .put("genesis_hash", "a".repeat(64))
        .put("application", applicationHash?.let { JSONObject().put("genesis_hash", it) } ?: JSONObject.NULL)

    private fun discoverBusinessConnection(
        servers: JSONArray,
        onSelected: (ConnectionTarget) -> Unit = {},
        onConnect: (ConnectionTarget) -> Unit = {},
    ) {
        compose.setContent {
            var address by remember { mutableStateOf("") }
            var peer by remember { mutableStateOf("") }
            var genesis by remember { mutableStateOf("a".repeat(64)) }
            JluManagerTheme {
                StartupConnectionStage(
                    identity = member,
                    identityReady = true,
                    address = address,
                    peer = peer,
                    genesisHash = genesis,
                    busy = false,
                    onAddressChange = { address = it },
                    onPeerChange = { peer = it },
                    onGenesisChange = {
                        genesis = it
                        onSelected(ConnectionTarget(address, peer, it))
                    },
                    onConnect = { onConnect(ConnectionTarget(address, peer, genesis)) },
                    onLink = {},
                    discover = { reply -> reply(JSONObject().put("servers", servers)) },
                    onRecheckIdentity = {},
                )
            }
        }
        node(R.string.startup_connection_method_discovery).scrollToIfScrollable().performClick()
        node(R.string.connection_discover).scrollToIfScrollable().performClick()
    }

    @Test fun discoveredBusinessConnectionUsesTheApplicationHashThroughTheCheckRequest() {
        var selected: ConnectionTarget? = null
        var requested: ConnectionTarget? = null
        val server = discoveredServer("application-peer", "b".repeat(64))
        discoverBusinessConnection(
            JSONArray().put(discoveredServer("identity-only-peer")).put(server),
            onSelected = { selected = it },
            onConnect = { requested = it },
        )
        compose.onNodeWithText("identity-only-peer", substring = true).assertDoesNotExist()
        // The discovered row has a separate select action; tapping the peer text no longer selects.
        node(R.string.connection_discover_select).scrollToIfScrollable().performClick()
        node(R.string.startup_connection_check).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            val expected = ConnectionTarget(server.getString("address"), "application-peer", "b".repeat(64))
            assertEquals(expected, selected)
            assertEquals(expected, requested)
        }
    }

    @Test fun discoveryWithoutAnApplicationHashCannotSelectABusinessConnection() {
        var selected: ConnectionTarget? = null
        discoverBusinessConnection(
            JSONArray().put(discoveredServer("identity-only-peer")),
            onSelected = { selected = it },
        )
        node(R.string.connection_discover_empty).assertExists()
        compose.onNodeWithText("identity-only-peer", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
        node(R.string.startup_connection_check).scrollToIfScrollable().assertIsNotEnabled()
        compose.runOnIdle { assertNull(selected) }
    }

    @Test fun applicationDiscoveryRejectsMissingEmptyAndInvalidHashes() {
        val server = discoveredServer("application-peer", "b".repeat(64))
        assertEquals("b".repeat(64), server.applicationConnectionTarget()?.genesisHash)
        for (application in listOf(
            JSONObject.NULL,
            JSONObject(),
            JSONObject().put("genesis_hash", ""),
            JSONObject().put("genesis_hash", "invalid"),
        )) {
            server.put("application", application)
            assertNull(server.applicationConnectionTarget())
        }
        server.remove("application")
        assertNull(server.applicationConnectionTarget())
    }

    private fun organizationSetup(
        identity: MemberIdentityView?,
        identityReady: Boolean,
        failure: String? = null,
        setup: MutableState<Boolean> = mutableStateOf(false),
        proposal: MutableState<String> = mutableStateOf(""),
        execute: (JSONObject, (JSONObject) -> Unit) -> Unit = { _, reply -> reply(JSONObject().put("error", true)) },
    ) {
        compose.setContent {
            JluManagerTheme {
                if (setup.value) {
                    MainIdentityPage(
                        configuration = configuration,
                        device = "device-42",
                        busy = false,
                        directory = context.cacheDir,
                        publicIdentity = publicIdentityJson,
                        state = identity?.let { IdentityState().received(it) } ?: IdentityState(),
                        facts = null,
                        identityScope = StartupScope(0, configuration, "device-42"),
                        onConfig = {},
                        fetch = { _, reply -> reply(JSONObject().put("error", true)) },
                        discover = { reply -> reply(JSONObject().put("error", true)) },
                        onBusinessGeneses = {},
                        onFetchFailed = {},
                        executeOps = { input, reply, _, _ -> execute(input, reply) },
                        onIdentityState = { _, _, _ -> },
                        onBack = { setup.value = false },
                        proposal = proposal,
                    )
                } else {
                    StartupConnectionStage(
                        identity = identity,
                        identityReady = identityReady,
                        address = "addr",
                        peer = "peer",
                        genesisHash = "a".repeat(64),
                        busy = false,
                        failure = failure,
                        onAddressChange = {},
                        onPeerChange = {},
                        onGenesisChange = {},
                        onConnect = {},
                        onLink = {},
                        discover = { reply -> reply(JSONObject().put("error", true)) },
                        onRecheckIdentity = {},
                        onOpenIdentitySetup = { setup.value = true },
                    )
                }
            }
        }
    }

    @Test fun identityStageStartsWithOneCleanJoinTask() {
        identityStage()
        node(R.string.startup_identity_join_title).assertExists()
        node(R.string.startup_identity_join_helper).assertExists()
        node(R.string.startup_identity_import_open).assertExists()
        node(R.string.startup_identity_find_org).assertExists()
        node(R.string.startup_identity_share_open).assertExists()
        node(R.string.identity_configuration).assertDoesNotExist()
        node(R.string.identity_fetch).assertDoesNotExist()
        node(R.string.identity_inspect).assertDoesNotExist()
        node(R.string.identity_sync).assertDoesNotExist()
        node(R.string.identity_proposal).assertDoesNotExist()
        assertNoBusinessPages()
    }

    @Test fun importPageKeepsRawConfigurationOnATechnicalPage() {
        identityStage()
        node(R.string.startup_identity_import_open).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_import_label).assertExists()
        node(R.string.identity_configuration).assertDoesNotExist()
        node(R.string.startup_identity_technical_open).scrollToIfScrollable().performClick()
        node(R.string.identity_configuration).scrollToIfScrollable().assertExists()
        node(R.string.identity_save).scrollToIfScrollable().assertExists()
    }

    @Test fun invalidImportDoesNotSaveOrRevealTheBusinessPages() {
        var saved: String? = null
        identityStage(onConfig = { saved = it })
        node(R.string.startup_identity_import_open).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_import_label).scrollToIfScrollable().performTextInput("{\"hello\":\"world\"}")
        node(R.string.startup_identity_import_confirm).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_import_invalid).assertExists()
        compose.runOnIdle { assertNull(saved) }
        assertNoBusinessPages()
    }

    @Test fun configuredIdentityAutoChecksOnceAndRetriesOnDemand() {
        var attempts = 0
        var observedAvailability = "observing"
        var observedState = IdentityState()
        val unboundFacts = JSONObject("""{"members":{"member-42":["device-42"]},"memberships":[],"chairs":{},"permissions":{}}""")
        compose.setContent {
            var identityState by remember { mutableStateOf(IdentityState()) }
            var facts by remember { mutableStateOf<JSONObject?>(null) }
            JluManagerTheme {
                StartupIdentityStage(
                    configuration = configuration,
                    device = "device-42",
                    publicIdentity = publicIdentityJson,
                    identityState = identityState,
                    facts = facts,
                    busy = false,
                    directory = context.cacheDir,
                    onConfig = {},
                    fetch = { _, reply -> reply(JSONObject().put("error", true)) },
                    discover = { reply -> reply(JSONObject().put("error", true)) },
                    onBusinessGeneses = {},
                    onFetchFailed = {},
                    execute = { _, reply ->
                        attempts++
                        if (attempts == 1) reply(JSONObject().put("error", true)) else reply(unboundFacts)
                    },
                    onStateChange = { next, nextFacts ->
                        identityState = next
                        facts = nextFacts
                        observedState = next
                        observedAvailability = next.availability.name
                    },
                    review = passThroughReview,
                )
            }
        }
        fun awaitResult(label: String, ready: () -> Boolean) {
            try {
                compose.waitUntil(5_000, ready)
            } catch (timeout: Throwable) {
                throw AssertionError(
                    "$label timed out: attempts=$attempts availability=$observedAvailability " +
                        "state=$observedState\n${compose.onRoot().printToString()}",
                    timeout,
                )
            }
        }
        compose.waitUntil(5_000) { attempts == 1 }
        // The sync reply updates state after the attempt counter; wait for the rendered result.
        awaitResult("unavailable body") {
            compose.onAllNodesWithText(context.getString(R.string.startup_identity_unavailable_body))
                .fetchSemanticsNodes().isNotEmpty()
        }
        node(R.string.startup_identity_unavailable_body).assertExists()
        node(R.string.startup_identity_retry).assertExists()
        node(R.string.startup_identity_change_data).assertExists()
        node(R.string.identity_sync).assertDoesNotExist()
        node(R.string.identity_inspect).assertDoesNotExist()
        node(R.string.startup_identity_retry).scrollToIfScrollable().performClick()
        compose.waitUntil(5_000) { attempts == 2 }
        awaitResult("unbound body") {
            compose.onAllNodesWithText(context.getString(R.string.startup_identity_unbound_body))
                .fetchSemanticsNodes().isNotEmpty()
        }
        node(R.string.startup_identity_unbound_body).assertExists()
        assertNoBusinessPages()
    }

    @Test fun unboundIdentityOpensTheShareAndEnrollmentPages() {
        val unbound = IdentityState().received(member.copy(clubs = emptySet()))
        identityStage(
            configuration = configuration,
            identityState = unbound,
            execute = { _, reply ->
                reply(JSONObject("""{"members":{"member-42":["device-42"]},"memberships":[],"chairs":{},"permissions":{}}"""))
            },
        )
        node(R.string.startup_identity_unbound_status).assertExists()
        node(R.string.startup_identity_unbound_body).assertExists()
        node(R.string.startup_identity_share_open).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_share_title).assertExists()
        node(R.string.identity_export_copy).assertExists()
        // 3.2.6 shows the full device identifier as a summary before the disclosure explains it.
        node(R.string.device_identity_peer).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
        node(R.string.startup_identity_enrollment_open).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_enrollment_label).assertDoesNotExist()
        node(R.string.identity_import_record).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_enrollment_label).assertExists()
        node(R.string.identity_sign).assertExists()
        node(R.string.identity_submit).assertExists()
        node(R.string.identity_draft).assertDoesNotExist()
        node(R.string.identity_sign_offline).assertDoesNotExist()
        assertNoBusinessPages()
    }

    @Test fun chairCanOpenOrganizationIdentitySetupBeforeTheConnectionWorks() {
        val setup = mutableStateOf(false)
        organizationSetup(
            identity = member.copy(chairOf = setOf("club-a")),
            identityReady = true,
            failure = "offline",
            setup = setup,
        )
        node(R.string.startup_connection_check_title).assertExists()
        node(R.string.startup_connection_failed).assertExists()
        node(R.string.startup_connection_identity_manage).scrollToIfScrollable().assertExists()
        node(R.string.startup_connection_identity_manage).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertTrue(setup.value) }
        node(R.string.identity_title).assertExists()
        node(R.string.startup_connection_check_title).assertDoesNotExist()
        node(R.string.identity_issuance_title).assertDoesNotExist()
        node(R.string.office_workbench_title).assertDoesNotExist()
        node(R.string.office_title).assertDoesNotExist()
        node(R.string.workspace_nav_activities).assertDoesNotExist()
    }

    @Test fun nonAdminMemberCannotOpenOrganizationIdentitySetup() {
        val setup = mutableStateOf(false)
        organizationSetup(identity = member, identityReady = true, setup = setup)
        node(R.string.startup_connection_identity_manage).assertDoesNotExist()
        compose.runOnIdle { assertFalse(setup.value) }
    }

    @Test fun unverifiedChairCannotOpenOrganizationIdentitySetup() {
        val setup = mutableStateOf(false)
        organizationSetup(
            identity = member.copy(chairOf = setOf("club-a")),
            identityReady = false,
            setup = setup,
        )
        node(R.string.startup_connection_identity_manage).assertDoesNotExist()
        compose.runOnIdle { assertFalse(setup.value) }
    }

    @Test fun backFromSetupReturnsToTheConnectionTaskWithTheSignedDraft() {
        val setup = mutableStateOf(false)
        val proposal = mutableStateOf("")
        organizationSetup(
            identity = member.copy(chairOf = setOf("club-a")),
            identityReady = true,
            failure = "offline",
            setup = setup,
            proposal = proposal,
            execute = { input, reply ->
                if (input.optString("operation") == "sign") {
                    reply(JSONObject(input.getJSONObject("record").toString()).put("signed", true))
                } else reply(JSONObject().put("error", true))
            },
        )
        node(R.string.startup_connection_identity_manage).scrollToIfScrollable().performClick()
        node(R.string.identity_actions_title).scrollToIfScrollable().performClick()
        node(R.string.identity_import_record).scrollToIfScrollable().performClick()
        node(R.string.identity_proposal).scrollToIfScrollable().performTextInput(
            """{"action":{"type":"register_service","service":"application"},"signatures":[]}""",
        )
        node(R.string.identity_review_changes).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_proposal).assertDoesNotExist()
        node(R.string.identity_sign).scrollToIfScrollable().performClick()
        compose.waitUntil(5_000) { proposal.value.contains("\"signed\"") }
        compose.runOnIdle { assertTrue(proposal.value.contains("\"signed\": true")) }
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
        node(R.string.startup_connection_check_title).assertExists()
        node(R.string.startup_connection_failed).assertExists()
        compose.runOnIdle { assertTrue(proposal.value.contains("\"signed\": true")) }
        node(R.string.startup_connection_identity_manage).scrollToIfScrollable().performClick()
        node(R.string.identity_actions_title).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_raw_record_show).scrollToIfScrollable().performClick()
        compose.onNodeWithText("\"signed\": true", substring = true).assertExists()
        compose.runOnIdle { assertTrue(proposal.value.contains("\"signed\": true")) }
    }

    @Test fun incompleteTargetStartsAtTheMethodStepWithoutABusinessPicker() {
        compose.setContent {
            JluManagerTheme {
                StartupConnectionStage(
                    identity = member,
                    identityReady = true,
                    address = "",
                    peer = "",
                    genesisHash = "a".repeat(64),
                    busy = false,
                    onAddressChange = {},
                    onPeerChange = {},
                    onGenesisChange = {},
                    onConnect = {},
                    onLink = {},
                    discover = { reply -> reply(JSONObject().put("error", true)) },
                    onRecheckIdentity = {},
                )
            }
        }
        node(R.string.startup_connection_method_title).assertExists()
        node(R.string.startup_connection_method_manual).scrollToIfScrollable().performClick()
        node(R.string.connection_address).assertExists()
        node(R.string.connection_peer).assertExists()
    }

    @Test fun completeTargetStartsAtTheSingleCheckPage() {
        compose.setContent {
            JluManagerTheme {
                StartupConnectionStage(
                    identity = member,
                    identityReady = true,
                    address = "addr",
                    peer = "peer",
                    genesisHash = "a".repeat(64),
                    busy = true,
                    onAddressChange = {},
                    onPeerChange = {},
                    onGenesisChange = {},
                    onConnect = {},
                    onLink = {},
                    discover = { reply -> reply(JSONObject().put("error", true)) },
                    onRecheckIdentity = {},
                )
            }
        }
        node(R.string.startup_connection_check_title).assertExists()
        node(R.string.startup_connection_checking).assertExists()
        node(R.string.connection_address).assertDoesNotExist()
        node(R.string.connection_peer).assertDoesNotExist()
    }

    @Test fun failedConnectionCheckOffersRetryAndChange() {
        compose.setContent {
            JluManagerTheme {
                StartupConnectionStage(
                    identity = member,
                    identityReady = true,
                    address = "addr",
                    peer = "peer",
                    genesisHash = "a".repeat(64),
                    busy = false,
                    failure = "offline",
                    onAddressChange = {},
                    onPeerChange = {},
                    onGenesisChange = {},
                    onConnect = {},
                    onLink = {},
                    discover = { reply -> reply(JSONObject().put("error", true)) },
                    onRecheckIdentity = {},
                )
            }
        }
        node(R.string.startup_connection_failed).assertExists()
        node(R.string.startup_connection_retry).assertExists()
        node(R.string.startup_connection_change).scrollToIfScrollable().assertExists()
    }

    @Test fun businessContentAndRolesStayHiddenWhenIdentityOrConnectionIsNotCurrent() {
        compose.setContent {
            JluManagerTheme {
                StartupGate(
                    session = StartupSession(stage = StartupStage.Business, identity = verified, connectionVerified = true),
                    identity = { Text("identity-stage") },
                    connection = { Text("connection-stage") },
                    business = { Text(context.getString(R.string.office_role_president)) },
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.office_role_president)).assertExists()
        compose.onNodeWithText("identity-stage").assertDoesNotExist()
        compose.onNodeWithText("connection-stage").assertDoesNotExist()
    }

    @Test fun invalidIdentityReturnsToTheIdentityStageAndHidesBusinessRoles() {
        compose.setContent {
            JluManagerTheme {
                StartupGate(
                    session = StartupSession(stage = StartupStage.Business, identity = verified.failed(), connectionVerified = true),
                    identity = { Text("identity-stage") },
                    connection = { Text("connection-stage") },
                    business = { Text(context.getString(R.string.office_role_president)) },
                )
            }
        }
        compose.onNodeWithText("identity-stage").assertExists()
        compose.onNodeWithText("connection-stage").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.office_role_president)).assertDoesNotExist()
    }

    @Test fun verifiedIdentityWithoutAConnectionStopsAtTheConnectionStage() {
        compose.setContent {
            JluManagerTheme {
                StartupGate(
                    session = StartupSession(stage = StartupStage.Identity, identity = verified),
                    identity = { Text("identity-stage") },
                    connection = { Text("connection-stage") },
                    business = { Text(context.getString(R.string.office_workbench_title)) },
                )
            }
        }
        compose.onNodeWithText("connection-stage").assertExists()
        compose.onNodeWithText("identity-stage").assertDoesNotExist()
        node(R.string.office_workbench_title).assertDoesNotExist()
    }
    @Test fun leavingTheOrganizationPageIgnoresItsLateFetchReply() {
        var reply: ((JSONObject) -> Unit)? = null
        compose.setContent {
            JluManagerTheme {
                StartupIdentityStage(
                    configuration = "",
                    device = "device-42",
                    publicIdentity = publicIdentityJson,
                    identityState = IdentityState(),
                    facts = null,
                    busy = false,
                    directory = context.cacheDir,
                    onConfig = {},
                    fetch = { _, answer -> reply = answer },
                    discover = { answer -> answer(JSONObject().put("servers", org.json.JSONArray().put(
                        JSONObject().put("address", "addr-org").put("peer", "peer-org").put("federation", "org")))) },
                    onBusinessGeneses = {},
                    onFetchFailed = {},
                    execute = { _, answer -> answer(JSONObject().put("error", true)) },
                    onStateChange = { _, _ -> },
                    review = passThroughReview,
                )
            }
        }
        node(R.string.startup_identity_find_org).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_find_org_action).scrollToIfScrollable().performClick()
        compose.onNodeWithText("peer-org", substring = true).scrollToIfScrollable().performClick()
        node(R.string.startup_identity_organization_fetch).scrollToIfScrollable().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
        node(R.string.startup_identity_find_org_hint).assertExists()
        val late = JSONObject().put("identity", JSONObject().put("genesis_hash", "a".repeat(64)).put("genesis", JSONObject()))
        compose.runOnIdle { reply?.invoke(late) }
        node(R.string.startup_identity_trust_title).assertDoesNotExist()
        node(R.string.startup_identity_find_org_hint).assertExists()
    }

}

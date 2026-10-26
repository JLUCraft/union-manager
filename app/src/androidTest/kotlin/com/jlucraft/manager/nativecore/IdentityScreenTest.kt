package com.jlucraft.manager.nativecore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun node(id: Int) = compose.onNodeWithText(context.getString(id))
    private val config = JSONObject().put("genesis_hash", "a".repeat(64)).put("genesis", JSONObject())
        .put("peer", "test-peer").put("address", "test-address").toString()

    private fun facts(chairs: JSONObject = JSONObject().put("club-a", "chair-person")): JSONObject = JSONObject()
        .put("members", JSONObject().put("person", JSONArray().put("device")))
        .put("memberships", JSONArray().put(JSONArray().put("person").put("club-a")))
        .put("chairs", chairs)
        .put("permissions", JSONObject())

    private fun verification(facts: JSONObject): IdentityState =
        IdentityState().received(identityMember(facts, "device"))

    private fun ops(
        configuration: String = config,
        state: IdentityState = IdentityState(),
        facts: JSONObject? = null,
        onConfig: (String) -> Unit = {},
        execute: (JSONObject, (JSONObject) -> Unit) -> Unit = { _, _ -> error("must not execute") },
        proposal: String = "",
    ) = IdentityOps(
        config = configuration,
        device = "device",
        directory = context.cacheDir,
        review = { request -> JSONObject(request).getJSONObject("config").toString() },
        execute = execute,
        onConfig = onConfig,
        onStateChange = { _, _ -> },
        onBusinessGeneses = {},
        onFetchFailed = {},
        initialState = state,
        initialFacts = facts,
        proposalState = mutableStateOf(proposal),
    )

    @Composable
    private fun IdentityTestPages(ops: IdentityOps, busy: Boolean = false) {
        var page by remember { mutableStateOf("summary") }
        when (page) {
            "technical" -> IdentityTechnicalPage(ops, busy, { page = "summary" }, { page = "facts" })
            "facts" -> IdentityFactsPage(ops, busy) { page = "summary" }
            "records" -> IdentityRecordsPage(ops, busy, listOf("person")) { page = "summary" }
            else -> IdentitySummaryPage(
                ops = ops,
                state = ops.state,
                busy = busy,
                onBack = {},
                onOpenTechnical = { page = "technical" },
                onOpenFacts = { page = "facts" },
                onOpenRecords = { page = "records" },
            )
        }
    }

    @Test fun summaryKeepsRawSettingsAndRecordsOnTheirOwnPages() {
        val facts = facts()
        compose.setContent { JluManagerTheme { IdentityTestPages(ops(state = verification(facts), facts = facts)) } }
        node(R.string.identity_current).assertExists()
        node(R.string.identity_configuration).assertDoesNotExist()
        node(R.string.identity_proposal).assertDoesNotExist()
        node(R.string.startup_identity_technical_title).scrollToIfScrollable().performClick()
        node(R.string.identity_configuration).assertExists()
        node(R.string.identity_show_facts).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.identity_verified_facts)).scrollToIfScrollable().performClick()
        node(R.string.identity_facts_not_code).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()
        compose.onNodeWithText(context.getString(R.string.identity_actions_title)).scrollToIfScrollable().performClick()
        node(R.string.identity_create_change).assertExists()
        node(R.string.identity_import_record).assertExists()
        node(R.string.identity_proposal).assertDoesNotExist()
        node(R.string.identity_raw_record_show).assertDoesNotExist()
        node(R.string.identity_draft).assertIsNotEnabled()
        node(R.string.identity_sign_offline).assertExists()
    }

    @Test fun unconfiguredIdentityCannotSynchronizeOrInventAMember() {
        compose.setContent { JluManagerTheme { IdentityTestPages(ops(configuration = "")) } }
        node(R.string.identity_empty).assertExists()
        node(R.string.identity_issuance_title).assertDoesNotExist()
        node(R.string.identity_issuance_draft).assertDoesNotExist()
        node(R.string.identity_issuance_unconfigured).assertDoesNotExist()
    }

    @Test fun unavailableSourcePreservesLastVerifiedFacts() {
        var calls = 0
        val current = facts()
        val ops = ops(state = verification(current), facts = current, execute = { _, reply ->
            calls++
            reply(if (calls == 1) current else JSONObject().put("error", "offline"))
        })
        compose.setContent { JluManagerTheme { IdentityTestPages(ops) } }
        node(R.string.startup_connection_recheck_identity).scrollToIfScrollable().performClick()
        node(R.string.identity_current).assertExists()
        node(R.string.startup_connection_recheck_identity).scrollToIfScrollable().performClick()
        node(R.string.identity_state_unavailable).assertExists()
        node(R.string.identity_verified_facts).scrollToIfScrollable().performClick()
        node(R.string.identity_facts_not_code).assertExists()
    }

    @Test fun busyDisablesConfigurationAndSigning() {
        compose.setContent { JluManagerTheme { IdentityTestPages(ops(), busy = true) } }
        node(R.string.account_identity_configuration_help).scrollToIfScrollable().performClick()
        node(R.string.identity_configuration).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.identity_actions_title)).assertDoesNotExist()
    }

    @Test fun advancedConfigurationEditsStayLocalUntilExplicitSave() {
        var saved: String? = null
        val draft = JSONObject(config).put("note", "edited").toString()
        compose.setContent { JluManagerTheme { IdentityTestPages(ops(onConfig = { saved = it })) } }
        node(R.string.account_identity_configuration_help).scrollToIfScrollable().performClick()
        node(R.string.identity_configuration).performTextClearance()
        node(R.string.identity_configuration).performTextInput(draft)
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("edited", substring = true).assertExists()
        node(R.string.identity_save).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals("edited", JSONObject(saved!!).optString("note"))
        }
    }

    @Test fun importedRecordIsReviewedBeforeSigningAndPayloadStaysUnchanged() {
        val record = JSONObject()
            .put("action", JSONObject().put("type", "bind_device").put("member", "person").put("device", "device"))
            .put("signatures", JSONArray())
        var signed: JSONObject? = null
        val ops = ops(execute = { input, reply ->
            signed = input.getJSONObject("record")
            reply(record)
        })
        compose.setContent { JluManagerTheme { IdentityTestPages(ops) } }
        compose.onNodeWithText(context.getString(R.string.identity_actions_title)).scrollToIfScrollable().performClick()
        node(R.string.identity_import_record).scrollToIfScrollable().performClick()
        node(R.string.identity_proposal).scrollToIfScrollable().performTextInput(record.toString())
        node(R.string.identity_review_changes).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_proposal).assertDoesNotExist()
        node(R.string.identity_sign_offline).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertEquals(record.toString(), signed?.toString())
            assertEquals(IdentityAvailability.Unconfigured, ops.state.availability)
        }
    }

    @Test fun proposalSummaryShowsReadableFieldsBeforeSigning() {
        val record = JSONObject().put("action", JSONObject().put("type", "authorize_verifier").put("id", "skin-jlu").put("club", "jlu")
            .put("device", "12D3KooWDevice").put("evidence", JSONArray(listOf("institutional_email", "game_profile"))).put("unknown_field", "hidden"))
            .put("signatures", JSONArray())
        compose.setContent { androidx.compose.material3.MaterialTheme { androidx.compose.foundation.layout.Column { ProposalSummary(record.toString()) } } }
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_action_authorize_verifier).assertExists()
        compose.onNodeWithText("skin-jlu").assertExists()
        compose.onNodeWithText("12D3KooWDevice").assertExists()
        compose.onNodeWithText(context.getString(R.string.identity_evidence_email) + "、" + context.getString(R.string.identity_evidence_profile)).assertExists()
        compose.onNodeWithText("institutional_email", substring = true).assertDoesNotExist()
        compose.onNodeWithText("hidden").assertExists()
        compose.onNodeWithText(context.getString(R.string.identity_signature_count, 0)).assertExists()
    }

    /**
     * Multi-signature succession still needs its chair map: choosing 提出更换社长 must reveal an
     * editable seat list. Filtering the bulk rewrite out of the catalog never removed this editor.
     */
    @Test fun successionActionExposesItsChairEditor() {
        // Open the form on the succession action directly: the popup menu is not part of the test
        // tree reliably, and the regression was the field editor, not the menu.
        compose.setContent {
            JluManagerTheme {
                // The real page scrolls; the test needs the same container or the seat buttons sit
                // outside the viewport and cannot be clicked.
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                    IdentityActionForm(enabled = true, onDraft = {}, initialAction = "propose_succession")
                }
            }
        }
        node(R.string.identity_field_chairs).assertExists()
        node(R.string.identity_field_club).assertExists()
        // One seat row is there; adding a seat adds a second removable row, so the editor is live.
        compose.onNodeWithText(context.getString(R.string.identity_remove_chair)).scrollToIfScrollable().assertExists()
        compose.onNodeWithText(context.getString(R.string.identity_add_chair)).scrollToIfScrollable().performClick()
        compose.onAllNodesWithText(context.getString(R.string.identity_remove_chair)).assertCountEquals(2)
    }

    @Test fun actionCatalogOffersSuccessionButNotBulkChairReplacement() {
        val offered = offeredIdentityForms(JSONArray(UnionNative.management("{\"operation\":\"forms\"}")))
        val actions = offered.map { it.getString("action") }
        // All four succession steps stay offered; only the bulk roster rewrite is gone.
        listOf("propose_succession", "approve_succession", "execute_succession", "cancel_succession")
            .forEach { assertTrue("$it must stay available: $actions", it in actions) }
        assertTrue("device transfer must stay available: $actions", "bind_device" in actions && "revoke_device" in actions)
        assertTrue("the founder roster must not be rewritable from the app: $actions", "replace_chairs" !in actions)
    }

    @Test fun existingRecordShowsSummaryAndKeepsRawEditingExplicit() {
        val facts = facts()
        val record = JSONObject()
            .put("action", JSONObject().put("type", "propose_succession")
                .put("chairs", JSONObject().put("club-a", "new-chair-person")).put("quorum", 2))
            .put("signatures", JSONArray().put(JSONObject().put("device", "device")))
        compose.setContent { JluManagerTheme { IdentityTestPages(ops(state = verification(facts), facts = facts, proposal = record.toString())) } }
        compose.onNodeWithText(context.getString(R.string.identity_actions_title)).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_action_propose_succession).assertExists()
        compose.onNodeWithText("club-a / new-chair-person").assertExists()
        compose.onNodeWithText(context.getString(R.string.identity_signature_count, 1)).assertExists()
        node(R.string.identity_proposal).assertDoesNotExist()
        node(R.string.identity_import_record).assertDoesNotExist()
        node(R.string.identity_raw_record_show).scrollToIfScrollable().performClick()
        node(R.string.identity_proposal).assertExists()
        node(R.string.identity_proposal).scrollToIfScrollable().performTextClearance()
        node(R.string.identity_proposal).performTextInput("{")
        node(R.string.identity_review_changes).scrollToIfScrollable().performClick()
        node(R.string.identity_proposal).assertExists()
        node(R.string.identity_proposal_invalid).assertExists()
        node(R.string.identity_summary_title).assertDoesNotExist()
        node(R.string.identity_proposal).scrollToIfScrollable().performTextClearance()
        node(R.string.identity_proposal).performTextInput(record.toString())
        node(R.string.identity_review_changes).scrollToIfScrollable().performClick()
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_proposal).assertDoesNotExist()
    }

    @Test fun proposalSummaryShowsIdentifierValuesVerbatim() {
        val member = JSONObject()
            .put("action", JSONObject().put("type", "confirm_member")
                .put("member", "manual").put("club", "game_profile").put("evidence", "institutional_email"))
            .put("signatures", JSONArray())
        val chairs = JSONObject()
            .put("action", JSONObject().put("type", "propose_succession")
                .put("chairs", JSONObject().put("club-a", "manual").put("club-b", "game_profile")))
            .put("signatures", JSONArray())
        val dynamicChairs = JSONObject()
            .put("action", JSONObject().put("type", "propose_succession")
                .put("chairs", JSONObject().put("permission", "admin").put("evidence", "manual")))
            .put("signatures", JSONArray())
        compose.setContent { androidx.compose.material3.MaterialTheme { androidx.compose.foundation.layout.Column {
            ProposalSummary(member.toString())
            ProposalSummary(chairs.toString())
            ProposalSummary(dynamicChairs.toString())
        } } }
        compose.onNodeWithText("manual").assertExists()
        compose.onNodeWithText("game_profile").assertExists()
        compose.onNodeWithText("club-a / manual", substring = true).assertExists()
        compose.onNodeWithText("club-b / game_profile", substring = true).assertExists()
        compose.onNodeWithText("permission / admin", substring = true).assertExists()
        compose.onNodeWithText("evidence / manual", substring = true).assertExists()
        node(R.string.identity_issuance_evidence_manual).assertDoesNotExist()
        node(R.string.identity_permission_admin).assertDoesNotExist()
        node(R.string.identity_evidence_profile).assertDoesNotExist()
        node(R.string.identity_evidence_email).assertExists()
    }

    @Test fun proposalSummaryFormatsNestedRecordsWithoutRawJson() {
        val record = JSONObject()
            .put("action", JSONObject().put("type", "propose_succession")
                .put("chairs", JSONObject().put("club-a", "new-chair-person")))
            .put("signatures", JSONArray())
        compose.setContent { androidx.compose.material3.MaterialTheme { androidx.compose.foundation.layout.Column { ProposalSummary(record.toString()) } } }
        compose.onNodeWithText("club-a / new-chair-person").assertExists()
        compose.onNodeWithText("{\"club-a\"", substring = true).assertDoesNotExist()
    }

    @Test fun proposalSummaryIgnoresTextThatIsNotARecord() {
        compose.setContent { androidx.compose.material3.MaterialTheme { androidx.compose.foundation.layout.Column { ProposalSummary("not json") } } }
        node(R.string.identity_summary_title).assertDoesNotExist()
    }
}

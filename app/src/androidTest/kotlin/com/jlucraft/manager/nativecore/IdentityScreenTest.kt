package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
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
    private fun screen(configuration: String, busy: Boolean = false, execute: (JSONObject, (JSONObject) -> Unit) -> Unit = { _, _ -> error("must not execute") }) {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            IdentityCard(configuration, "device", busy, context.cacheDir, {}, execute = execute)
        } } }
    }
    @Test fun unconfiguredIdentityCannotSynchronizeOrInventAMember() {
        screen("")
        node(R.string.identity_empty).assertExists()
        node(R.string.identity_sync).assertIsNotEnabled()
        node(R.string.identity_proposal_show).performScrollTo().performClick()
        node(R.string.identity_submit).assertIsNotEnabled()
    }
    @Test fun unavailableSourcePreservesLastVerifiedFacts() {
        var requests = 0
        screen(config) { _, receive ->
            requests++
            receive(if (requests == 1) JSONObject("""{"members":{"stable-person":["device"]},"memberships":[["stable-person","club"]],"chairs":{},"permissions":{}}""") else JSONObject().put("error", "offline"))
        }
        node(R.string.identity_sync).performScrollTo().performClick()
        compose.onAllNodesWithText(context.getString(R.string.identifier_details)).assertCountEquals(2)
        node(R.string.identity_show_facts).assertExists()
        node(R.string.identity_sync).performScrollTo().performClick()
        node(R.string.identity_unavailable).assertExists()
        compose.onAllNodesWithText(context.getString(R.string.identifier_details)).assertCountEquals(2)
        node(R.string.identity_show_facts).assertExists()
    }
    @Test fun busyDisablesConfigurationAndSigning() {
        screen(config, busy = true)
        node(R.string.identity_show_configuration).performScrollTo().performClick()
        node(R.string.identity_configuration).assertIsNotEnabled()
        node(R.string.identity_sync).assertIsNotEnabled()
        node(R.string.identity_proposal_show).performScrollTo().performClick()
        node(R.string.identity_sign_offline).assertIsNotEnabled()
    }
    @Test fun offlineSignatureDoesNotClaimCurrentAuthority() {
        screen(config) { _, receive -> receive(JSONObject().put("signed", true)) }
        node(R.string.identity_proposal_show).performScrollTo().performClick()
        node(R.string.identity_proposal).performScrollTo().performTextInput("{}")
        node(R.string.identity_sign_offline).performScrollTo().performClick()
        node(R.string.identity_current).assertDoesNotExist()
        node(R.string.identity_empty).assertExists()
    }
    @Test fun proposalSummaryShowsReadableFieldsBeforeSigning() {
        val record = JSONObject().put("action", JSONObject().put("type", "authorize_verifier").put("id", "skin-jlu").put("club", "jlu")
            .put("device", "12D3KooWDevice").put("evidence", JSONArray(listOf("institutional_email", "game_profile"))).put("unknown_field", "hidden"))
            .put("signatures", JSONArray())
        compose.setContent { MaterialTheme { Column { ProposalSummary(record.toString()) } } }
        node(R.string.identity_summary_title).assertExists()
        node(R.string.identity_action_authorize_verifier).assertExists()
        compose.onNodeWithText("skin-jlu").assertExists()
        compose.onNodeWithText("12D3KooWDevice").assertExists()
        compose.onNodeWithText(context.getString(R.string.identity_evidence_email) + "、" + context.getString(R.string.identity_evidence_profile)).assertExists()
        compose.onNodeWithText("institutional_email", substring = true).assertDoesNotExist()
        compose.onNodeWithText("hidden").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.identity_signature_count, 0)).assertExists()
    }
    @Test fun proposalSummaryIgnoresTextThatIsNotARecord() {
        compose.setContent { MaterialTheme { Column { ProposalSummary("not json") } } }
        node(R.string.identity_summary_title).assertDoesNotExist()
    }
    @Test fun rawSettingsAndRecordsAreHiddenUntilRequested() {
        screen(config)
        node(R.string.identity_configuration).assertDoesNotExist()
        node(R.string.identity_proposal).assertDoesNotExist()
        node(R.string.identity_show_configuration).performScrollTo().performClick()
        node(R.string.identity_configuration).assertExists()
        node(R.string.identity_hide_configuration).performScrollTo().performClick()
        node(R.string.identity_configuration).assertDoesNotExist()
        node(R.string.identity_proposal_show).performScrollTo().performClick()
        node(R.string.identity_proposal).assertExists()
    }
    @Test fun firstRunStillShowsTheSettingsFieldSoTheyCanBeImported() {
        screen("")
        node(R.string.identity_configuration).assertExists()
        node(R.string.identity_show_configuration).assertDoesNotExist()
    }
}

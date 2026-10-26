package com.jlucraft.manager.nativecore

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagementScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun node(id: Int) = compose.onNodeWithText(text(id))
    private companion object { const val OVERSIZE_IDENTIFIER_BYTES = 129 }
    private val oversizeIdentifier = "x".repeat(OVERSIZE_IDENTIFIER_BYTES)
    @Test fun serviceRegistrationFormUsesRealNativeBuilder() {
        val secret = UnionNative.generateSecret()
        val peer = try { UnionNative.peerId(secret) } finally { secret.fill(0) }
        var result: JSONObject? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { IdentityActionForm(true) { result = it } } } }
        node(R.string.identity_action_confirm_member).performClick()
        node(R.string.identity_action_register_service).scrollToIfScrollable().performClick()
        node(R.string.identity_field_audience).scrollToIfScrollable().performTextInput(peer)
        node(R.string.identity_field_club).scrollToIfScrollable().performTextInput("test-school")
        node(R.string.identity_service_application).scrollToIfScrollable().performClick()
        node(R.string.identity_draft).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertEquals("register_service", result?.getString("type"))
            assertEquals("application", result?.getString("service"))
            assertEquals(peer, result?.getString("audience"))
            assertEquals("test-school", result?.getString("club"))
        }
    }
    @Test fun generatedCustomServiceUuidRegistrationUsesRealNativeBuilder() {
        val secret = UnionNative.generateSecret()
        val peer = try { UnionNative.peerId(secret) } finally { secret.fill(0) }
        var result: JSONObject? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { IdentityActionForm(true) { result = it } } } }
        node(R.string.identity_action_confirm_member).performClick()
        node(R.string.identity_action_register_service).scrollToIfScrollable().performClick()
        node(R.string.identity_field_audience).scrollToIfScrollable().performTextInput(peer)
        node(R.string.identity_field_club).scrollToIfScrollable().performTextInput("test-school")
        node(R.string.identity_generate_id).scrollToIfScrollable().performClick()
        node(R.string.identity_draft).scrollToIfScrollable().performClick()
        var generated: String? = null
        compose.runOnIdle {
            assertEquals("register_service", result?.getString("type"))
            generated = result?.getString("service")
            assertNotNull(generated)
            java.util.UUID.fromString(generated)
            assertEquals(peer, result?.getString("audience"))
            assertEquals("test-school", result?.getString("club"))
        }
        compose.onNodeWithText(generated!!).assertExists()
    }
    @Test fun grantPermissionAcceptsCustomServiceWithUseAndManage() {
        val customService = org.json.JSONTokener(UnionNative.management("{\"operation\":\"new_service_id\"}")).nextValue() as String
        val results = mutableListOf<JSONObject>()
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { IdentityActionForm(true, listOf("alice")) { results += it } } } }
        node(R.string.identity_action_confirm_member).performClick()
        node(R.string.identity_action_grant_permission).scrollToIfScrollable().performClick()
        node(R.string.identity_generate_id).scrollToIfScrollable().performClick()
        compose.onNodeWithContentDescription(text(R.string.identity_field_member)).scrollToIfScrollable().performClick()
        compose.onNodeWithText("alice").performClick()
        node(R.string.identity_field_service).scrollToIfScrollable().performTextInput(customService)
        node(R.string.identity_field_permission).scrollToIfScrollable().performTextInput("use")
        node(R.string.identity_draft).scrollToIfScrollable().performClick()
        node(R.string.identity_generate_id).scrollToIfScrollable().performClick()
        node(R.string.identity_field_permission).scrollToIfScrollable().performTextClearance()
        node(R.string.identity_field_permission).performTextInput("manage")
        node(R.string.identity_draft).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertEquals(2, results.size)
            assertEquals(listOf("use", "manage"), results.map { it.getString("permission") })
            assertEquals(2, results.map { it.getString("id") }.toSet().size)
            results.forEach { result ->
                assertEquals("grant_permission", result.getString("type"))
                assertEquals("alice", result.getString("member"))
                assertEquals(customService, result.getString("service"))
                java.util.UUID.fromString(result.getString("id"))
            }
        }
    }
    @Test fun invalidPermissionIdentifierShowsFormErrorAndDoesNotDispatch() {
        val customService = org.json.JSONTokener(UnionNative.management("{\"operation\":\"new_service_id\"}")).nextValue() as String
        var result: JSONObject? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { IdentityActionForm(true, listOf("alice")) { result = it } } } }
        node(R.string.identity_action_confirm_member).performClick()
        node(R.string.identity_action_grant_permission).scrollToIfScrollable().performClick()
        node(R.string.identity_generate_id).scrollToIfScrollable().performClick()
        compose.onNodeWithContentDescription(text(R.string.identity_field_member)).scrollToIfScrollable().performClick()
        compose.onNodeWithText("alice").performClick()
        node(R.string.identity_field_service).scrollToIfScrollable().performTextInput(customService)
        node(R.string.identity_field_permission).scrollToIfScrollable().performTextInput(oversizeIdentifier)
        node(R.string.identity_draft).scrollToIfScrollable().performClick()
        node(R.string.identity_form_invalid).assertExists()
        compose.runOnIdle { assertNull(result) }
    }
    @Test fun invalidCustomServiceIdentifierShowsFormErrorAndDoesNotDispatch() {
        val secret = UnionNative.generateSecret()
        val peer = try { UnionNative.peerId(secret) } finally { secret.fill(0) }
        var result: JSONObject? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { IdentityActionForm(true) { result = it } } } }
        node(R.string.identity_action_confirm_member).performClick()
        node(R.string.identity_action_register_service).scrollToIfScrollable().performClick()
        node(R.string.identity_field_audience).scrollToIfScrollable().performTextInput(peer)
        node(R.string.identity_field_club).scrollToIfScrollable().performTextInput("test-school")
        node(R.string.identity_field_service).scrollToIfScrollable().performTextInput(oversizeIdentifier)
        node(R.string.identity_draft).scrollToIfScrollable().performClick()
        node(R.string.identity_form_invalid).assertExists()
        compose.runOnIdle { assertNull(result) }
    }
    @Test fun consensusReplicasUseNativeValidationWithoutRoleEditor() {
        val secret = UnionNative.generateSecret()
        val peer = try { UnionNative.peerId(secret) } finally { secret.fill(0) }
        var action: JSONObject? = null
        val state = JSONObject().put("admins", JSONArray().put("test-device"))
            .put("consensus_members", JSONObject().put("1", JSONObject().put("peer", peer)
                .put("addresses", JSONArray().put("/ip4/127.0.0.1/tcp/1/p2p/$peer"))))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BusinessAdminCard(state, "test-device", false) { action = it } } } }
        node(R.string.business_admin_open).performClick()
        // Member roles have one authority page now: the business card no longer edits them.
        node(R.string.identity_field_member).assertDoesNotExist()
        node(R.string.business_replicas_load).scrollToIfScrollable().performClick()
        node(R.string.business_replicas_preview).scrollToIfScrollable().performClick()
        node(R.string.business_submit).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertNull(action) }
        compose.onAllNodesWithText(text(R.string.business_submit)).onLast().performClick()
        compose.runOnIdle {
            assertEquals("set_consensus_members", action?.getString("type"))
            assertEquals(peer, action?.getJSONObject("members")?.getJSONObject("1")?.getString("peer"))
        }
    }
    @Test fun auditAnchorRequiresExplicitConfirmationBeforeDispatch() {
        var action: JSONObject? = null
        val state = JSONObject().put("admins", JSONArray().put("test-device"))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { AuditAnchorCard(state, "test-device", false) { action = it } } } }
        node(R.string.audit_anchor_id).performTextInput("audit-test")
        node(R.string.audit_anchor_digest).performTextInput("a".repeat(64))
        node(R.string.audit_anchor_submit).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertNull(action) }
        compose.onAllNodesWithText(text(R.string.audit_anchor_submit)).onLast().performClick()
        compose.runOnIdle {
            assertEquals("anchor_digest", action?.getString("type"))
            assertEquals("a".repeat(64), action?.getString("digest"))
        }
    }
}

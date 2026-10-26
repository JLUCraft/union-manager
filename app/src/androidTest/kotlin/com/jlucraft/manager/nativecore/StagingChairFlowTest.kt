package com.jlucraft.manager.nativecore

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A chair authorizes a verifier through the real identity pages against a live identity writer.
 * The disposable fixture directory (`stagingFixture`) holds `chair.key`, `identity-config.json`
 * and `verifier.json` (id, club, device); the script mode `staging` supplies it.
 */
@RequiresFixture
@RunWith(AndroidJUnit4::class)
class StagingChairFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)
    private fun node(id: Int) = compose.onNodeWithText(label(id))

    /** Wait for a UI condition; on timeout fail with the visible semantics tree. */
    private fun await(what: String, millis: Long, condition: () -> Boolean) {
        try { compose.waitUntil(millis, condition) }
        catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $what\n" + compose.onRoot(useUnmergedTree = true).printToString(400))
        }
    }

    @Test fun chairAuthorizesAVerifierThroughTheIdentityPages() {
        val fixture = File(InstrumentationRegistry.getArguments().getString("stagingFixture") ?: error("Set stagingFixture"))
        val secret = File(fixture, "chair.key").readBytes()
        val config = File(fixture, "identity-config.json").readText()
        val verifier = JSONObject(File(fixture, "verifier.json").readText())
        try {
            compose.setContent {
                MaterialTheme {
                    IdentityFlowHost(
                        mode = IdentityFlowMode.Account,
                        config = config,
                        device = UnionNative.peerId(secret),
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
                        execute = { input, receive ->
                            Thread {
                                val result = try { JSONObject(UnionNative.executeIdentity(secret, input.toString())) }
                                catch (e: Exception) { JSONObject().put("error", e.message ?: "failed") }
                                compose.runOnUiThread { receive(result) }
                            }.start()
                        },
                        onStateChange = { _, _ -> },
                        onBack = {},
                    )
                }
            }

            // The chair's device is a chair of the school's club in the compiled genesis.
            node(R.string.startup_connection_recheck_identity).scrollToIfScrollable().performClick()
            await("the identity to be current", 60_000) { compose.onAllNodesWithText(label(R.string.identity_current)).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodes(hasText(verifier.getString("club"), substring = true)).fetchSemanticsNodes().isNotEmpty())

            captureScreen("staging-01-identity-synced")
            compose.onNodeWithText(label(R.string.identity_actions_title)).scrollToIfScrollable().performClick()

            // Pick the action and fill the form exactly as an operator would.
            val first = JSONArray(UnionNative.management("{\"operation\":\"forms\"}")).getJSONObject(0).getString("action")
            compose.onNode(hasText(label(identityActionLabel(first))) and hasClickAction()).scrollToIfScrollable().performClick()
            compose.onNode(hasText(label(R.string.identity_action_authorize_verifier))).performClick()
            node(R.string.identity_field_id).scrollToIfScrollable().performTextInput(verifier.getString("id"))
            node(R.string.identity_field_club).scrollToIfScrollable().performTextInput(verifier.getString("club"))
            node(R.string.identity_field_device).scrollToIfScrollable().performTextInput(verifier.getString("device"))
            node(R.string.identity_evidence_email).scrollToIfScrollable().performClick()
            node(R.string.identity_evidence_profile).scrollToIfScrollable().performClick()
            captureScreen("staging-02-form-filled")
            compose.onAllNodesWithText(label(R.string.identity_draft))[0].scrollToIfScrollable().performClick()

            // The reviewed summary is the only thing shown by default: no raw JSON is on screen.
            await("the drafted summary", 30_000) { compose.onAllNodesWithText(label(R.string.identity_summary_title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText(verifier.getString("id")).assertCountEquals(2)
            compose.onAllNodesWithText(verifier.getString("device")).assertCountEquals(2)
            compose.onNode(hasText(label(R.string.identity_evidence_email), substring = true) and hasText(label(R.string.identity_evidence_profile), substring = true)).assertExists()
            compose.onNodeWithText("institutional_email", substring = true).assertDoesNotExist()
            compose.onNodeWithText(label(R.string.identity_signature_count).replace("%1\$d", "0")).assertExists()

            captureScreen("staging-03-drafted")
            node(R.string.identity_sign).scrollToIfScrollable().performClick()
            await("the signature", 60_000) { compose.onAllNodesWithText(label(R.string.identity_signature_count).replace("%1\$d", "1")).fetchSemanticsNodes().isNotEmpty() }

            captureScreen("staging-04-signed")
            node(R.string.identity_submit).scrollToIfScrollable().performClick()
            compose.waitForIdle()
            captureScreen("staging-05-confirm-submit")
            compose.onNode(hasText(label(R.string.identity_submit)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

            // A fresh synchronization must still succeed; the host then reads the log independently.
            Thread.sleep(3_000)
            compose.onNodeWithContentDescription(label(R.string.navigation_back)).performClick()
            node(R.string.startup_connection_recheck_identity).scrollToIfScrollable().performClick()
            await("the identity to be current", 60_000) { compose.onAllNodesWithText(label(R.string.identity_current)).fetchSemanticsNodes().isNotEmpty() }
            captureScreen("staging-06-after-submit")
            assertTrue(compose.onAllNodesWithText(label(R.string.identity_state_unavailable)).fetchSemanticsNodes().isEmpty())
        } finally { secret.fill(0) }
    }
}

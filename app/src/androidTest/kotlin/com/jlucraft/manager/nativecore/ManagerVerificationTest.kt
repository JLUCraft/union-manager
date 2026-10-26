package com.jlucraft.manager.nativecore

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verification-only cases for the device checks that screenshots cannot settle:
 * the keyboard inset, the wide step outline's current row, and the accessibility tree of the
 * roster rows and the consensus delta list.
 *
 * Nothing here changes product behaviour; the assertions read the same semantics a screen reader
 * and `uiautomator dump` consume.
 */
@RunWith(AndroidJUnit4::class)
class ManagerVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun wideWindow() = compose.activity.resources.configuration.screenWidthDp >= 840

    private fun imeBottomPx(): Int {
        val decor = compose.activity.window.decorView
        return ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
    }

    /** Serialises the live accessibility tree; this is what TalkBack and `uiautomator dump` read. */
    private fun dumpAccessibilityTree(name: String): String {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return ""
        val out = StringBuilder()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            out.append("  ".repeat(depth)).append(node.className)
            node.text?.takeIf { it.isNotBlank() }?.let { out.append(" text=\"").append(it).append('"') }
            node.contentDescription?.takeIf { it.isNotBlank() }
                ?.let { out.append(" desc=\"").append(it).append('"') }
            if (node.isClickable) out.append(" clickable")
            out.append('\n')
            for (index in 0 until node.childCount) node.getChild(index)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        runCatching {
            File("/data/local/tmp", "jlucraft-a11y-$name.txt").writeText(out.toString())
        }
        return out.toString()
    }

    @Test fun imeKeepsThePinnedActionBarAboveTheKeyboard() {
        val fieldTag = "ime-field"
        val barTag = "ime-pinned-bar"
        val focus = FocusRequester()
        compose.setContent {
            JluManagerTheme {
                // The real shell and the real pinned bar: the shell is what applies imePadding.
                WorkspaceNavigation(WorkspaceDestination.Home, false, {}) {
                    TaskPage(
                        title = text(R.string.workflow_title),
                        busy = false,
                        bottomBar = {
                            BottomActionBar {
                                ActionButton(R.string.workflow_next, true, modifier = Modifier.testTag(barTag)) {}
                            }
                        },
                    ) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            modifier = Modifier.focusRequester(focus).testTag(fieldTag),
                        )
                        Text(text(R.string.workflow_help))
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    }
                }
            }
        }
        compose.waitForIdle()
        // performTextInput injects text without raising the keyboard; a click focuses the field and
        // lets the platform show the IME for real.
        compose.onNodeWithTag(fieldTag).performClick()
        var waited = 0
        var observedIme = 0
        var observedHeight = 0
        while (waited < 15_000) {
            compose.waitForIdle()
            observedIme = imeBottomPx()
            observedHeight = compose.activity.window.decorView.height
            if (observedIme > 0) break
            Thread.sleep(250)
            waited += 250
        }
        assumeTrue(
            "no soft keyboard appeared within 15s (imeBottom=$observedIme, decorHeight=$observedHeight); " +
                "a hardware keyboard such as the book-cover keyboard suppresses the IME, " +
                "so enable show_ime_with_hard_keyboard to run this check",
            observedIme > 0,
        )

        val imeBottom = imeBottomPx()
        val decorHeight = compose.activity.window.decorView.height
        val imeTop = decorHeight - imeBottom
        val barBottom = compose.onNodeWithTag(barTag).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "pinned bar bottom=$barBottom must sit above the IME top=$imeTop (ime=$imeBottom, window=$decorHeight)",
            barBottom <= imeTop + 2f,
        )
        // The title bar must stay on screen while the keyboard is up.
        compose.onNodeWithText(text(R.string.workflow_title)).assertIsDisplayed()
    }

    @Test fun eventWizardOutlineMarksTheDraftAndReviewSteps() {
        assumeTrue("the step outline only exists on an expanded window", wideWindow())
        val identity = "device-fixture"

        // Drafting: the wizard starts at step 2 (who participates).
        compose.setContent {
            JluManagerTheme {
                OfficeWorkflowEventPage(
                    state = workflowState(identity, plan = null), kind = "meeting", eventId = "event-1", busy = false,
                    identity = identity, onOpenCompetition = {},
                    send = FormSender { _, _, _ -> }, onBack = {},
                )
            }
        }
        compose.waitForIdle()
        assertEquals(
            "drafting should highlight step 2",
            text(R.string.workflow_step_schools),
            currentOutlineRow(),
        )

    }

    @Test fun eventWizardOutlineMarksTheReviewStep() {
        assumeTrue("the step outline only exists on an expanded window", wideWindow())
        val identity = "device-fixture"
        // Reviewing: the plan exists and is not finalized, so the wizard sits on step 4.
        compose.setContent {
            JluManagerTheme {
                OfficeWorkflowEventPage(
                    state = workflowState(
                        identity,
                        plan = JSONObject()
                            .put("kind", "meeting")
                            .put("schools", JSONObject())
                            .put("candidates", org.json.JSONArray())
                            .put("checkin_opens_ms", 1_800_000_000_000L)
                            .put("checkin_closes_ms", 1_800_000_600_000L)
                            .put("ends_ms", 1_800_003_600_000L),
                    ),
                    kind = "meeting", eventId = "event-1", busy = false,
                    identity = identity, onOpenCompetition = {},
                    send = FormSender { _, _, _ -> }, onBack = {},
                )
            }
        }
        compose.waitForIdle()
        assertEquals(
            "reviewing should highlight step 4",
            text(R.string.workflow_step_material),
            currentOutlineRow(),
        )
    }

    @Test fun rosterRowsAreReadable() {
        compose.setContent {
            JluManagerTheme {
                Column {
                    RosterRow(label = "member-a", help = text(R.string.office_role_manager), onClick = {})
                    RosterRow(label = "member-b", help = text(R.string.office_role_member))
                }
            }
        }
        compose.waitForIdle()
        // A clickable roster row keeps the member id as its accessible name and stays actionable.
        compose.onNodeWithText("member-a").assertIsDisplayed()
        compose.onNodeWithText("member-a").assertHasClickAction()
        // A read-only row is still plain readable text, not an empty card.
        compose.onNodeWithText("member-b").assertIsDisplayed()
        val tree = dumpAccessibilityTree("roster")
        assertTrue(
            "roster dump should contain both member ids: $tree",
            tree.contains("member-a") && tree.contains("member-b"),
        )
    }

    @Test fun consensusDeltaListNamesEveryChange() {
        compose.setContent {
            JluManagerTheme {
                AccountAdminPage(consensusState(), "device-fixture", false, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.business_admin_open)).scrollToIfScrollable().performClick()
        compose.onNodeWithText(text(R.string.business_replica_add)).scrollToIfScrollable().performClick()
        // A preview only builds from a complete row, so the added server needs an id, a peer and a
        // dialable address exactly like the editor demands.
        val addedPeer = "12D3KooWJ9Qm5n8Xf7T3y2Vb1Kp4Lh6Rd8Ws5Ze3Ng7Ci2Ao4Mu6Bq"
        compose.onNodeWithText(text(R.string.business_replica_id)).scrollToIfScrollable().performTextInput("9")
        compose.onNodeWithText(text(R.string.business_replica_peer)).scrollToIfScrollable().performTextInput(addedPeer)
        compose.onNodeWithText(text(R.string.business_replica_addresses)).scrollToIfScrollable()
            .performTextInput("/ip4/127.0.0.1/udp/25565/quic-v1/p2p/$addedPeer")
        compose.onNodeWithText(text(R.string.business_replicas_preview)).scrollToIfScrollable().performClick()
        compose.waitForIdle()
        // The delta list only exists after the native consensus builder returns a preview, which
        // needs a configured identity; without one this check cannot run, so skip with the reason
        // instead of reporting a false failure.
        assumeTrue(
            "consensus preview was not built (needs a configured identity), so the delta list is unreachable",
            compose.onAllNodesWithText(text(R.string.business_preview_title)).fetchSemanticsNodes().isNotEmpty(),
        )
        // Each change reads as its own line: the change kind with a count, then the server id.
        compose.onNodeWithText(text(R.string.business_change_added, 1)).scrollToIfScrollable().assertExists()
        compose.onNodeWithText("9").scrollToIfScrollable().assertExists()
        dumpAccessibilityTree("consensus-delta")
    }

    /** The title printed on the outline row currently marked as the active step. */
    private fun currentOutlineRow(): String {
        val node = compose.onNodeWithTag(STEP_OUTLINE_CURRENT_TAG, useUnmergedTree = true).fetchSemanticsNode()
        return node.children
            .mapNotNull { child -> child.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") }
            .firstOrNull { candidate -> candidate.isNotBlank() && candidate.toIntOrNull() == null }
            .orEmpty()
    }

    private fun workflowState(identity: String, plan: JSONObject?) = JSONObject()
        .put(
            "events",
            JSONObject().put(
                "event-1",
                JSONObject().put("phase", "draft").put("title", "线下例会").put("organizer", identity),
            ),
        )
        .put("members", JSONObject().put("member-li", JSONObject().put("club", "computer-club")))
        .put("admins", org.json.JSONArray().put(identity))
        .put("leagues", JSONObject())
        .put(
            "organization",
            JSONObject()
                .put("automation", JSONObject().put("worker", identity))
                .put("schools", JSONObject().put("computer-club", JSONObject().put("name", "计算机社")))
                .put("plans", JSONObject().also { if (plan != null) it.put("event-1", plan) })
                .put("resources", JSONObject())
                .put("notices", JSONObject())
                .put("reports", JSONObject()),
        )

    private fun consensusState(): JSONObject {
        val peer = "12D3KooWQz7Tf3sVp6Nb1Yh4Kd8Rm2Xc5Lw9Gt3Jq7Zn1Bv6Ms4Hd8Pe"
        val replica = JSONObject()
            .put("peer", peer)
            .put("addresses", org.json.JSONArray().put("/ip4/127.0.0.1/udp/25565/quic-v1"))
        return JSONObject()
            .put("admins", org.json.JSONArray().put("device-fixture"))
            .put("consensus_members", JSONObject().put("1", replica))
            .put("organization", JSONObject())
    }
}

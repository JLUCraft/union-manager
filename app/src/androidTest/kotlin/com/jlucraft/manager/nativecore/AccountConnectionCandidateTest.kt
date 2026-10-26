package com.jlucraft.manager.nativecore

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The account connection editor owns a candidate; only an explicit check applies it. */
@RunWith(AndroidJUnit4::class)
class AccountConnectionCandidateTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun node(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun back() =
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()

    private val current = ConnectionTarget("10.0.0.8:29637", "peer-old", "a".repeat(64))
    private val candidate = ConnectionTarget("10.0.0.9:29637", "peer-new", "c".repeat(64))

    private fun openManualEditor() {
        node(R.string.account_connection_change).performClick()
        node(R.string.startup_connection_check_title).assertExists()
        node(R.string.startup_connection_change).scrollToIfScrollable().performClick()
        node(R.string.startup_connection_method_manual).scrollToIfScrollable().performClick()
    }

    private fun typeCandidate() {
        node(R.string.connection_address).performTextReplacement(candidate.address)
        node(R.string.connection_peer).performTextReplacement(candidate.peer)
        node(R.string.genesis_hash_label).performTextReplacement(candidate.genesisHash)
    }

    private fun assertCandidateFields() {
        compose.onNodeWithText(candidate.address).assertExists()
        compose.onNodeWithText(candidate.peer).assertExists()
        compose.onNodeWithText(candidate.genesisHash).assertExists()
    }

    @Test fun typingAndBackKeepThePageAndOnlyAnExplicitCheckAppliesOnce() {
        val applied = mutableListOf<ConnectionTarget>()
        compose.setContent {
            JluManagerTheme {
                AccountConnectionPage(
                    state = null, identity = "device-42", member = null, identityReady = true,
                    address = current.address, peer = current.peer, genesisHash = current.genesisHash,
                    connected = false, busy = false, failure = null,
                    onConnectTarget = { applied += it }, onLink = {},
                    discover = { reply -> reply(JSONObject().put("error", true)) }, onBack = {},
                )
            }
        }

        openManualEditor()
        typeCandidate()
        compose.runOnIdle { assertTrue(applied.isEmpty()) }

        // Back to the method page: the edited candidate is a draft, not an applied target.
        back()
        node(R.string.startup_connection_method_title).assertExists()
        compose.runOnIdle { assertTrue(applied.isEmpty()) }

        // The page and all candidate fields survive the round trip.
        node(R.string.startup_connection_method_manual).scrollToIfScrollable().performClick()
        assertCandidateFields()

        node(R.string.startup_connection_check).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertEquals(listOf(candidate), applied) }
        node(R.string.startup_connection_check_title).assertExists()
    }

    @Test fun candidateSurvivesLockAndRemainsAfterAFailedCheck() {
        var locked by mutableStateOf(false)
        var failure by mutableStateOf<String?>(null)
        val applied = mutableListOf<ConnectionTarget>()
        compose.setContent {
            JluManagerTheme {
                val holder = rememberSaveableStateHolder()
                if (locked) Text("locked-screen")
                else holder.SaveableStateProvider(accountPageScope("device-42", "config")) {
                    AccountConnectionPage(
                        state = null, identity = "device-42", member = null, identityReady = true,
                        address = current.address, peer = current.peer, genesisHash = current.genesisHash,
                        connected = false, busy = false, failure = failure,
                        onConnectTarget = { target ->
                            applied += target
                            failure = "offline"
                        },
                        onLink = {},
                        discover = { reply -> reply(JSONObject().put("error", true)) }, onBack = {},
                    )
                }
            }
        }

        openManualEditor()
        typeCandidate()
        compose.runOnIdle { locked = true }
        compose.onNodeWithText("locked-screen").assertExists()
        compose.runOnIdle { locked = false }

        // Same identity reentry restores the page, its step and the typed candidate.
        assertCandidateFields()

        node(R.string.startup_connection_check).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertEquals(listOf(candidate), applied) }
        node(R.string.startup_connection_failed).assertExists()

        // A failed request preserves the new target for an explicit retry or edit.
        node(R.string.startup_connection_change).scrollToIfScrollable().performClick()
        node(R.string.startup_connection_method_manual).scrollToIfScrollable().performClick()
        assertCandidateFields()
        compose.runOnIdle { assertEquals(listOf(candidate), applied) }
    }
}

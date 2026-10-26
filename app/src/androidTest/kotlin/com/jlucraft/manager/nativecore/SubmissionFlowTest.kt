package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Behaviour tests for pages that may only close after the server accepted their exact write. */
@RunWith(AndroidJUnit4::class)
class SubmissionFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun node(id: Int) = compose.onNodeWithText(text(id))

    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val tomorrow: LocalDateTime = LocalDateTime.now().plusDays(1)

    private fun fillDraftTimes() {
        listOf(
            R.string.office_opens to tomorrow,
            R.string.office_closes to tomorrow.plusHours(1),
            R.string.office_starts to tomorrow.plusHours(2),
        ).forEach { (label, time) ->
            node(label).scrollToIfScrollable().performTextInput(time.format(format))
        }
    }

    @Test fun draftSuccessClosesOnlyAfterTheAcceptedSnapshot() {
        var action: JSONObject? = null
        var committed: ((JSONObject) -> Unit)? = null
        var created = 0
        var back = 0
        compose.setContent {
            JluManagerTheme {
                OfficeDraftCreateFlow(
                    state = JSONObject(), busy = false, canCreate = true, onBack = { back++ },
                    onCreated = { created++ },
                    send = FormSender { sent, commit, _ -> action = sent; committed = commit },
                )
            }
        }
        node(R.string.office_name).performTextInput("春季联赛")
        node(R.string.office_description).performTextInput("周末校园活动")
        node(R.string.office_next).performClick()
        fillDraftTimes()
        node(R.string.office_next).scrollToIfScrollable().performClick()
        node(R.string.office_save_request).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertEquals(0, back) }
        val id = checkNotNull(action).getString("id")
        compose.runOnIdle { committed?.invoke(JSONObject().put("events", JSONObject().put(id, JSONObject()))) }
        compose.runOnIdle {
            assertEquals(1, created)
            assertEquals(0, back)
        }
    }

    @Test fun draftCompletionReportsExactAcceptedIdWithoutManualBack() {
        var action: JSONObject? = null
        var committed: ((JSONObject) -> Unit)? = null
        val created = mutableListOf<String>()
        var back = 0
        compose.setContent {
            JluManagerTheme {
                OfficeDraftCreateFlow(
                    state = JSONObject(), busy = false, canCreate = true, onBack = { back++ },
                    onCreated = { created += it },
                    send = FormSender { sent, commit, _ -> action = sent; committed = commit },
                )
            }
        }
        node(R.string.office_name).performTextInput("春季联赛")
        node(R.string.office_description).performTextInput("周末校园活动")
        node(R.string.office_next).performClick()
        fillDraftTimes()
        node(R.string.office_next).scrollToIfScrollable().performClick()
        node(R.string.office_save_request).scrollToIfScrollable().performClick()
        val id = checkNotNull(action).getString("id")
        compose.runOnIdle {
            committed?.invoke(
                JSONObject().put(
                    "events",
                    JSONObject().put("other-event", JSONObject()).put(id, JSONObject()),
                ),
            )
        }
        compose.runOnIdle {
            assertEquals(listOf(id), created)
            assertEquals(0, back)
        }
    }

    @Test fun draftFailureKeepsEveryFieldOnTheSameStep() {
        var back = 0
        compose.setContent {
            JluManagerTheme {
                OfficeDraftCreateFlow(
                    state = JSONObject(), busy = false, canCreate = true, onBack = { back++ }, onCreated = {},
                    send = FormSender { _, _, failed -> failed?.invoke("服务器拒绝了这次创建") },
                )
            }
        }
        node(R.string.office_name).performTextInput("春季联赛")
        node(R.string.office_description).performTextInput("周末校园活动")
        node(R.string.office_next).performClick()
        fillDraftTimes()
        node(R.string.office_next).scrollToIfScrollable().performClick()
        node(R.string.office_save_request).scrollToIfScrollable().performClick()
        node(R.string.office_save_request).assertExists()
        compose.onNodeWithText("服务器拒绝了这次创建").assertExists()
        compose.runOnIdle { assertEquals(0, back) }
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText("春季联赛").assertExists()
        compose.onNodeWithText("周末校园活动").assertExists()
    }

    @Test fun withdrawnCreationPermissionRemovesTheWritePages() {
        var canCreate by mutableStateOf(true)
        compose.setContent {
            JluManagerTheme {
                OfficeDraftCreateFlow(
                    state = JSONObject(), busy = false, canCreate = canCreate, onBack = {}, onCreated = {},
                    send = FormSender { _, _, _ -> },
                )
            }
        }
        node(R.string.office_name).performTextInput("春季联赛")
        node(R.string.office_description).performTextInput("周末校园活动")
        node(R.string.office_next).performClick()
        fillDraftTimes()
        node(R.string.office_next).scrollToIfScrollable().performClick()
        compose.runOnIdle { canCreate = false }
        node(R.string.office_save_request).assertDoesNotExist()
        node(R.string.office_create_restricted).assertExists()
    }

    @Test fun amendedDraftStaysOpenWhenTheServerRejectsIt() {
        val state = JSONObject(
            """
            {
              "members": {"president": {"device": "device", "club": "a", "roles": ["president"]}},
              "events": {"event": {"title": "Draft", "creator": "president", "organizer": "a", "phase": "draft", "epoch": 2, "brief_hash": "missing"}}
            }
            """,
        )
        val event = state.getJSONObject("events").getJSONObject("event")
        var saved = 0
        var committed: ((JSONObject) -> Unit)? = null
        compose.setContent {
            JluManagerTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OfficeRequestDetails(
                        state, "event", event, false, editable = true, send = { _ -> },
                        onSaved = { saved++ },
                        submit = FormSender { _, commit, failed -> committed = commit; failed?.invoke("保存被拒绝") },
                    )
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.office_description)).fetchSemanticsNodes().isNotEmpty()
        }
        node(R.string.office_description).performTextInput("新的说明")
        fillDraftTimes()
        node(R.string.office_save_revision).scrollToIfScrollable().performClick()
        node(R.string.office_description).assertExists()
        node(R.string.office_save_revision).assertExists()
        compose.onNodeWithText("保存被拒绝").assertExists()
        compose.runOnIdle { assertEquals(0, saved) }
        val amended = JSONObject(event.toString()).put("epoch", 3)
        compose.runOnIdle { committed?.invoke(JSONObject().put("events", JSONObject().put("event", amended))) }
        compose.runOnIdle { assertEquals(1, saved) }
    }

    @Test fun leagueCreateKeepsTheTypedValuesAndWaitsForTheLeague() {
        var league by mutableStateOf("")
        var seats by mutableStateOf("4")
        var cross by mutableStateOf(true)
        var host by mutableStateOf("")
        var scorer by mutableStateOf("")
        var created = 0
        var action: JSONObject? = null
        var committed: ((JSONObject) -> Unit)? = null
        var failed: ((String?) -> Unit)? = null
        compose.setContent {
            JluManagerTheme {
                LeagueCreateFlow(
                    league = league, seats = seats, crossClub = cross, host = host, scorer = scorer,
                    connected = true, busy = false, seatsValid = true, nameTaken = false,
                    identityOptions = emptyList(),
                    onLeagueChange = { league = it }, onSeatsChange = { seats = it },
                    onCrossClubChange = { cross = it }, onHostChange = { host = it }, onScorerChange = { scorer = it },
                    send = FormSender { sent, commit, fail -> action = sent; committed = commit; failed = fail },
                    onCreated = { created++ }, onBack = {},
                )
            }
        }
        node(R.string.governance_league_name).performTextInput("spring-cup")
        node(R.string.office_next).performClick()
        node(R.string.league_host_identity).scrollToIfScrollable().performTextInput("a".repeat(44))
        node(R.string.league_scorer_identity).scrollToIfScrollable().performTextInput("b".repeat(44))
        node(R.string.office_next).scrollToIfScrollable().performClick()
        compose.onNodeWithText("spring-cup").assertExists()
        node(R.string.league_create_confirm_action).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertEquals(0, created)
            assertEquals("spring-cup", checkNotNull(action).getString("league"))
        }
        compose.runOnIdle { failed?.invoke("创建被拒绝") }
        compose.onNodeWithText("创建被拒绝").assertExists()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText("spring-cup").assertExists()
        compose.runOnIdle { assertEquals(0, created) }
        node(R.string.office_next).performClick()
        node(R.string.office_next).scrollToIfScrollable().performClick()
        node(R.string.league_create_confirm_action).scrollToIfScrollable().performClick()
        val snapshot = JSONObject().put("leagues", JSONObject().put("spring-cup", JSONObject()))
        compose.runOnIdle { committed?.invoke(snapshot) }
        compose.runOnIdle { assertEquals(1, created) }
    }

    private val setupState = JSONObject(
        """
        {
          "members": {"manager": {"device": "device", "club": "a", "roles": ["manager"]}},
          "organization": {"schools": {"a": {"kind": "official"}}, "resources": {}}
        }
        """,
    )

    @Test fun resourceStepsKeepValuesAndWaitForTheSavedResource() {
        var action: JSONObject? = null
        var committed: ((JSONObject) -> Unit)? = null
        var failed: ((String?) -> Unit)? = null
        var back = 0
        compose.setContent {
            JluManagerTheme {
                WorkflowResourcePage(
                    state = setupState, identity = "device", busy = false,
                    send = FormSender { sent, commit, fail -> action = sent; committed = commit; failed = fail },
                    onBack = { back++ },
                )
            }
        }
        node(R.string.workflow_resource_id).performTextInput("league-service")
        node(R.string.workflow_resource_title_field).performTextInput("春季联赛服务")
        node(R.string.workflow_next).performClick()
        node(R.string.workflow_resource_address).performTextInput("10.0.0.8:29637")
        node(R.string.workflow_resource_peer).performTextInput("peer-service")
        node(R.string.workflow_next).performClick()
        node(R.string.workflow_resource_host).performTextInput("d".repeat(44))
        node(R.string.workflow_resource_scorer).performTextInput("e".repeat(44))
        compose.onNodeWithContentDescription(text(R.string.workflow_resource_seats)).performTextInput("4")
        node(R.string.workflow_resource_operations).performTextInput("ops-service")
        node(R.string.workflow_resource_instance).performTextInput("instance-1")
        node(R.string.workflow_next).scrollToIfScrollable().performClick()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText("10.0.0.8:29637").assertExists()
        node(R.string.workflow_next).scrollToIfScrollable().performClick()
        node(R.string.workflow_next).scrollToIfScrollable().performClick()
        node(R.string.workflow_resource_save).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertEquals(0, back)
            assertFalse(checkNotNull(action).getJSONObject("resource").has("genesis"))
        }
        compose.runOnIdle { failed?.invoke("资源保存失败") }
        compose.onNodeWithText("资源保存失败").assertExists()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText("10.0.0.8:29637").assertExists()
        node(R.string.workflow_next).scrollToIfScrollable().performClick()
        node(R.string.workflow_next).scrollToIfScrollable().performClick()
        node(R.string.workflow_resource_save).scrollToIfScrollable().performClick()
        val snapshot = JSONObject().put("organization", JSONObject().put("resources", JSONObject().put("league-service", JSONObject())))
        compose.runOnIdle { committed?.invoke(snapshot) }
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test fun independentClubNeverSeesTheAnnualRequirementPage() {
        compose.setContent {
            JluManagerTheme {
                WorkflowSchoolPage(setupState, "device", false, FormSender { _, _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithText("a").scrollToIfScrollable().performClick()
        node(R.string.office_next).performClick()
        node(R.string.workflow_mua_requirements).assertExists()
        node(R.string.workflow_annual_requirements).assertDoesNotExist()
        node(R.string.workflow_school_save).assertExists()
        node(R.string.office_next).assertDoesNotExist()
    }

    @Test fun officialClubSetsAnnualRequirementsOnItsOwnPage() {
        compose.setContent {
            JluManagerTheme {
                WorkflowSchoolPage(setupState, "device", false, FormSender { _, _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithText("a").scrollToIfScrollable().performClick()
        node(R.string.workflow_official).performClick()
        node(R.string.office_next).performClick()
        node(R.string.workflow_annual_requirements).assertDoesNotExist()
        node(R.string.workflow_mua_requirements).performTextInput("cid-mua")
        node(R.string.workflow_mua_recipient).performTextInput("团委")
        node(R.string.office_next).scrollToIfScrollable().performClick()
        node(R.string.workflow_annual_requirements).assertExists()
        node(R.string.workflow_annual_recipient).assertExists()
        node(R.string.workflow_school_save).assertExists()
    }

    @Test fun lockGateKeepsThePublicDraftFormAndItsStep() {
        var locked by mutableStateOf(false)
        compose.setContent {
            JluManagerTheme {
                val holder = rememberSaveableStateHolder()
                if (locked) Text("locked-screen")
                else holder.SaveableStateProvider("league:device:config") {
                    OfficeDraftCreateFlow(
                        state = JSONObject(), busy = false, canCreate = true, onBack = {}, onCreated = {},
                        send = FormSender { _, _, _ -> },
                    )
                }
            }
        }
        node(R.string.office_name).performTextInput("春季联赛")
        node(R.string.office_description).performTextInput("周末校园活动")
        node(R.string.office_next).performClick()
        fillDraftTimes()
        compose.runOnIdle { locked = true }
        compose.onNodeWithText("locked-screen").assertExists()
        compose.runOnIdle { locked = false }
        node(R.string.office_closes).assertExists()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText("春季联赛").assertExists()
        compose.onNodeWithText("周末校园活动").assertExists()
    }

    @Test fun draftScopePinsTheWholeTargetAndConfiguration() {
        val league = ConnectionTarget("10.0.0.8:29637", "peer-a", "c".repeat(64))
        val base = businessDraftScope("device-a", "config", league)
        assertEquals(base, businessDraftScope("device-a", "config", league))
        assertFalse(base == businessDraftScope("device-b", "config", league))
        assertFalse(base == businessDraftScope("device-a", "other-config", league))
        assertFalse(base == businessDraftScope("device-a", "config", league.copy(address = "10.0.0.9:29637")))
        assertFalse(base == businessDraftScope("device-a", "config", league.copy(peer = "peer-b")))
        assertFalse(base == businessDraftScope("device-a", "config", league.copy(genesisHash = "d".repeat(64))))
        // Two configurations may share a String.hashCode but must never share a draft.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(
            businessDraftScope("device-a", "Aa", league),
            businessDraftScope("device-a", "BB", league),
        )
        assertEquals(identityProposalScope("device-a", "config"), identityProposalScope("device-a", "config"))
        assertTrue(identityProposalScope("device-a", "config") != identityProposalScope("device-b", "config"))
        assertNotEquals(identityProposalScope("device-a", "Aa"), identityProposalScope("device-a", "BB"))
        assertNotNull(base)
    }
    @Test fun setupMenuReachesTheSchoolAndResourcePages() {
        var school = 0
        var resource = 0
        compose.setContent {
            JluManagerTheme {
                OfficeWorkflowSetupHome(
                    state = setupState, identity = "device", busy = false,
                    onOpenAutomation = {}, onOpenSchool = { school++ }, onOpenResource = { resource++ }, onBack = {},
                )
            }
        }
        node(R.string.workflow_school_title).scrollToIfScrollable().performClick()
        node(R.string.workflow_resource_title).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertEquals(1, school)
            assertEquals(1, resource)
        }
    }

}

package com.jlucraft.manager.nativecore

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonnelScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun node(id: Int) = compose.onNodeWithText(text(id))

    private fun personnelState(admins: String = """["device"]""", members: String = MEMBERS): JSONObject =
        JSONObject("""{"admins": $admins, "members": $members}""")

    private fun officeState(): JSONObject = JSONObject(
        """
        {
          "admins": ["admin-device"],
          "members": {
            "president": {"device": "president-device", "club": "a", "roles": ["president"]},
            "manager": {"device": "manager-device", "club": "a", "roles": ["manager"]},
            "server-admin": {"device": "admin-device", "club": "a", "roles": []}
          },
          "events": {}
        }
        """.trimIndent(),
    )

    /** The accepted snapshot holds the action's business roles plus identity-derived seats. */
    private fun acceptedSnapshot(state: JSONObject, action: JSONObject, facts: JSONObject? = null): JSONObject {
        val member = action.getString("member")
        val roles = rolesOf(action) + personnelIdentityRoles(facts, state, member)
        return JSONObject(state.toString()).apply {
            getJSONObject("members").getJSONObject(member).put("roles", JSONArray(roles.sorted()))
        }
    }

    private fun rolesOf(action: JSONObject): Set<String> =
        action.getJSONArray("roles").let { values -> (0 until values.length()).map { values.getString(it) }.toSet() }

    /** [state] is read inside composition so a mutable snapshot recomposes the host. */
    private fun screen(state: () -> JSONObject, send: FormSender, identity: String = "device", facts: JSONObject? = null) {
        compose.setContent {
            JluManagerTheme { PersonnelFlowHost(state(), identity, false, send, onBack = {}, facts = facts) }
        }
    }

    private fun openBob() {
        compose.onNodeWithTag(personnelMemberTag("bob")).scrollToIfScrollable().performClick()
        compose.onNodeWithText(text(R.string.personnel_edit_title, "bob")).assertExists()
    }

    private fun nextToReview() {
        node(R.string.office_next).scrollToIfScrollable().performClick()
        node(R.string.personnel_review_title).assertExists()
    }

    private fun confirmSubmit() {
        node(R.string.personnel_submit).scrollToIfScrollable().performClick()
        compose.onNode(
            hasText(text(R.string.personnel_submit)) and hasAnyAncestor(isDialog()) and hasClickAction(),
        ).performClick()
    }

    /**
     * Personnel offers the way into the identity record actions; the actions themselves are the
     * shared [IdentityActionForm] page, so there is no second copy of the flow.
     */
    @Test fun personnelEntryOpensTheIdentityRecordActions() {
        var opened = 0
        compose.setContent {
            JluManagerTheme {
                PersonnelFlowHost(
                    state = personnelState(),
                    identity = "device",
                    busy = false,
                    send = FormSender { _, _, _ -> },
                    onBack = {},
                    onOpenTransfer = { opened++ },
                )
            }
        }
        compose.onNodeWithTag(PERSONNEL_TRANSFER_TAG).scrollToIfScrollable().assertExists()
        node(R.string.personnel_transfer_open).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun identityHostOpensStraightOnTheRecordActions() {
        compose.setContent {
            JluManagerTheme {
                IdentityFlowHost(
                    mode = IdentityFlowMode.Account,
                    config = "",
                    device = "device",
                    busy = false,
                    directory = context.cacheDir,
                    publicIdentity = "",
                    state = IdentityState(),
                    facts = null,
                    onConfig = {},
                    fetch = { _, _ -> },
                    discover = { _ -> },
                    onBusinessGeneses = {},
                    onFetchFailed = {},
                    execute = { _, reply -> reply(JSONObject().put("error", true)) },
                    onStateChange = { _, _ -> },
                    onBack = {},
                    openRecordsInitially = true,
                )
            }
        }
        // The record action page opens directly, without walking through the identity summary.
        node(R.string.identity_actions_title).assertExists()
        node(R.string.identity_summary_title).assertDoesNotExist()
    }

    @Test fun rosterEditReviewCompletesOnlyForAnExactAcceptedSnapshot() {
        var current by mutableStateOf(personnelState())
        var sent: JSONObject? = null
        var attempts = 0
        val send = FormSender { action, committed, _ ->
            sent = action
            attempts++
            // The first accepted-looking reply is a stale snapshot without the write: it must not
            // close the review page.
            if (attempts == 1) committed?.invoke(JSONObject(current.toString()))
            else committed?.invoke(acceptedSnapshot(current, action).also { current = it })
        }
        screen({ current }, send)
        openBob()
        // Current roles arrive selected: changing one role never discards the untouched ones.
        compose.onNodeWithTag(personnelRoleTag("manager")).assertIsSelected()
        compose.onNodeWithTag(personnelRoleTag("reviewer")).assertIsSelected()
        compose.onNodeWithTag(personnelRoleTag("admin")).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(personnelRoleTag("reviewer")).scrollToIfScrollable().performClick()
        nextToReview()
        assertTrue(compose.onAllNodesWithText(text(R.string.office_role_admin), substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText(text(R.string.office_role_manager), substring = true).fetchSemanticsNodes().isNotEmpty())
        // The review page keeps the previous roles visible on purpose, so reviewer still appears in
        // the 修改前 line; the assertion is that the 修改后 line no longer lists it.
        compose.onNodeWithText(text(R.string.compare_before, ""), substring = true).assertExists()
        val afterRoles = compose.onAllNodesWithText(text(R.string.compare_after, ""), substring = true)
            .fetchSemanticsNodes()
            .joinToString(" ") { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") }
        assertTrue(afterRoles.isNotEmpty())
        assertTrue(!afterRoles.contains("reviewer"))

        // A snapshot that does not prove these exact roles leaves the review page and its draft.
        confirmSubmit()
        compose.runOnIdle {
            assertEquals("set_member_roles", sent?.getString("type"))
            assertEquals(setOf("manager", "admin"), sent?.let(::rolesOf))
            assertEquals(1, attempts)
        }
        node(R.string.personnel_review_title).assertExists()
        compose.onNodeWithTag(personnelMemberTag("bob")).assertDoesNotExist()

        // The exact accepted snapshot returns to the roster, which now shows the new roles.
        confirmSubmit()
        compose.runOnIdle { assertEquals(2, attempts) }
        node(R.string.personnel_review_title).assertDoesNotExist()
        compose.onNodeWithTag(personnelMemberTag("bob")).scrollToIfScrollable().assertExists()
        compose.onNodeWithText("reviewer", substring = true).assertDoesNotExist()
    }

    @Test fun identityDerivedChairSeatIsReadOnlyButStillSubmittedExactly() {
        val state = officeState()
        val facts = JSONObject("""{"chairs":{"a":"president"}}""")
        var sent: JSONObject? = null
        var accepted: JSONObject? = null
        val send = FormSender { action, committed, _ ->
            sent = action
            val snapshot = acceptedSnapshot(state, action, facts)
            accepted = snapshot
            committed?.invoke(snapshot)
        }
        screen({ state }, send, identity = "admin-device", facts = facts)
        compose.onNodeWithTag(personnelMemberTag("president")).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(personnelRoleTag("president"))
            .assertIsSelected()
            .assertIsNotEnabled()
        // The derived seat stays selected; a business role can still be added on top of it.
        compose.onNodeWithTag(personnelRoleTag("manager")).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(personnelRoleTag("president")).assert(hasText(text(R.string.office_role_president)))
        nextToReview()
        confirmSubmit()
        compose.runOnIdle {
            // The chair seat is shown and accepted, but only business roles reach build_roles.
            assertEquals(setOf("manager"), sent?.let(::rolesOf))
            assertTrue(personnelRoles(accepted, "president").contains("president"))
        }
        node(R.string.personnel_review_title).assertDoesNotExist()
        compose.onNodeWithTag(personnelMemberTag("president")).scrollToIfScrollable().assertExists()
    }

    @Test fun chairDisplayFromFactsNeverPersistsPresidentAsABusinessRole() {
        val state = officeState().apply {
            getJSONObject("members").getJSONObject("president")
                .put("roles", JSONArray(listOf("president", "reviewer")))
        }
        val facts = JSONObject("""{"chairs":{"a":"president"}}""")
        var sent: JSONObject? = null
        var accepted: JSONObject? = null
        val send = FormSender { action, committed, _ ->
            sent = action
            val snapshot = acceptedSnapshot(state, action, facts)
            accepted = snapshot
            committed?.invoke(snapshot)
        }
        screen({ state }, send, identity = "admin-device", facts = facts)
        compose.onNodeWithTag(personnelMemberTag("president")).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(personnelRoleTag("president")).assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag(personnelRoleTag("reviewer")).assertIsSelected()
        nextToReview()
        confirmSubmit()
        compose.runOnIdle {
            // An untouched chair still writes only its observed business role.
            assertEquals(setOf("reviewer"), sent?.let(::rolesOf))
            assertTrue(personnelRoles(accepted, "president").contains("president"))
        }
        node(R.string.personnel_review_title).assertDoesNotExist()
    }

    @Test fun removingTheMemberKeepsTheDraftAndRejectsAnEmptyRoleMatch() {
        assertTrue(!personnelRolesAccepted(JSONObject("""{"members":{}}"""), "bob", emptySet()))
        var current by mutableStateOf(personnelState())
        val withoutBob = JSONObject(current.toString()).apply { getJSONObject("members").remove("bob") }
        val send = FormSender { _, committed, _ ->
            current = withoutBob
            committed?.invoke(withoutBob)
        }
        screen({ current }, send)
        openBob()
        // Clear every business role; the draft now asks for an empty role set.
        compose.onNodeWithTag(personnelRoleTag("manager")).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(personnelRoleTag("reviewer")).scrollToIfScrollable().performClick()
        nextToReview()
        confirmSubmit()
        // A snapshot without the target cannot prove an accepted write, even for empty roles.
        node(R.string.personnel_review_title).assertExists()
        node(R.string.personnel_member_missing).assertExists()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText(text(R.string.personnel_edit_title, "bob")).assertExists()
        compose.onNodeWithTag(personnelRoleTag("manager")).assertExists()
        compose.onNodeWithTag(personnelRoleTag("reviewer")).assertExists()
    }

    @Test fun failedSubmissionKeepsTheDraftAndRetriesTheSameRoles() {
        var current by mutableStateOf(personnelState())
        var attempts = 0
        val send = FormSender { action, committed, failed ->
            attempts++
            if (attempts == 1) failed?.invoke("服务器暂时不可用")
            else committed?.invoke(acceptedSnapshot(current, action).also { current = it })
        }
        screen({ current }, send)
        openBob()
        compose.onNodeWithTag(personnelRoleTag("admin")).scrollToIfScrollable().performClick()
        nextToReview()
        confirmSubmit()
        compose.onNodeWithText("服务器暂时不可用").assertExists()
        node(R.string.personnel_review_title).assertExists()

        // The draft survives the failure: going back shows the same selection, not a reset form.
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText(text(R.string.personnel_edit_title, "bob")).assertExists()
        compose.onNodeWithTag(personnelRoleTag("admin")).assertIsSelected()
        compose.onNodeWithTag(personnelRoleTag("manager")).assertIsSelected()
        compose.onNodeWithTag(personnelRoleTag("reviewer")).assertIsSelected()

        nextToReview()
        confirmSubmit()
        compose.runOnIdle { assertEquals(2, attempts) }
        compose.onNodeWithTag(personnelMemberTag("bob")).scrollToIfScrollable().assertExists()
        node(R.string.personnel_review_title).assertDoesNotExist()
    }

    @Test fun losingTheServerAdminSlotRemovesEveryPersonnelForm() {
        var current by mutableStateOf(personnelState())
        screen({ current }, FormSender { _, _, _ -> })
        openBob()
        compose.onNodeWithTag(personnelRoleTag("manager")).assertIsSelected()
        node(R.string.office_next).assertExists()

        compose.runOnIdle { current = personnelState(admins = "[]") }
        node(R.string.personnel_restricted).assertExists()
        compose.onNodeWithTag(personnelRoleTag("manager")).assertDoesNotExist()
        node(R.string.office_next).assertDoesNotExist()
        node(R.string.personnel_submit).assertDoesNotExist()
    }

    @Test fun emptyRosterShowsAnEmptyStateInsteadOfInventedMembers() {
        screen({ personnelState(members = "{}") }, FormSender { _, _, _ -> })
        node(R.string.personnel_roster_empty).assertExists()
        compose.onNodeWithTag(personnelMemberTag("bob")).assertDoesNotExist()
    }

    @Test fun accountPersonnelEntryOpensTheRealRosterForAdmins() {
        val state = personnelState()
        compose.setContent {
            JluManagerTheme {
                val stack = rememberNavBackStack(AccountRoot)
                NavDisplay(
                    backStack = stack,
                    onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                    entryProvider = entryProvider {
                        entry<AccountRoot> {
                            AccountHomeNav(
                                state = state, busy = false, identity = "device", configured = true, pushReady = null,
                                onOpenIdentity = {}, onOpenIssuance = {}, onOpenPersonnel = { stack.add(AccountPersonnel) },
                                onOpenConnection = {}, onOpenDevice = {}, onOpenAdmin = {}, onOpenAudit = {},
                                onOpenHelp = {},
                            )
                        }
                        entry<AccountPersonnel> {
                            PersonnelFlowHost(state, "device", false, FormSender { _, _, _ -> }, onBack = {
                                if (stack.size > 1) stack.removeAt(stack.lastIndex)
                            })
                        }
                    },
                )
            }
        }
        node(R.string.personnel_title).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(PERSONNEL_TRANSFER_TAG).scrollToIfScrollable().assertExists()
        compose.onNodeWithTag(personnelMemberTag("alice")).scrollToIfScrollable().assertExists()
        compose.onNodeWithTag(personnelMemberTag("bob")).scrollToIfScrollable().assertExists()
    }

    @Test fun accountPersonnelEntryStaysClosedWithoutTheServerAdminSlot() {
        val state = personnelState(admins = "[]")
        var opened = 0
        compose.setContent {
            JluManagerTheme {
                AccountHomeNav(
                    state = state, busy = false, identity = "device", configured = true, pushReady = null,
                    onOpenIdentity = {}, onOpenIssuance = {}, onOpenPersonnel = { opened++ },
                    onOpenConnection = {}, onOpenDevice = {}, onOpenAdmin = {}, onOpenAudit = {},
                    onOpenHelp = {},
                )
            }
        }
        node(R.string.personnel_title).scrollToIfScrollable().assertIsNotEnabled()
        node(R.string.personnel_restricted).assertExists()
        compose.runOnIdle { assertEquals(0, opened) }
    }

    @Test fun officePresidentRoleSeesIssuanceButNotPersonnel() {
        val state = officeState()
        compose.setContent {
            MaterialTheme {
                WorkspaceDashboard(
                    state = state, identity = "president-device", busy = false,
                    onCreateActivity = {}, onMembershipIssuance = {}, onPersonnel = {},
                    onOpenActivities = {}, onOpenApprovals = {}, onOpenReports = {}, onOpenResources = {},
                )
            }
        }
        compose.onNodeWithText(
            text(R.string.office_role_identity, "president", text(R.string.office_role_president)),
        ).assertExists()
        node(R.string.office_tasks_title_president).assertExists()
        node(R.string.office_tasks_title_admin).assertDoesNotExist()
        compose.onNodeWithTag(OFFICE_TASK_ISSUANCE_TAG).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_PERSONNEL_TAG).assertDoesNotExist()
        node(R.string.identity_issuance_title).assertDoesNotExist()
        node(R.string.personnel_title).assertDoesNotExist()
    }

    @Test fun dashboardNamesTheServerAdministratorRole() {
        val state = officeState()
        compose.setContent {
            MaterialTheme {
                WorkspaceDashboard(
                    state = state, identity = "admin-device", busy = false,
                    onCreateActivity = {}, onMembershipIssuance = {}, onPersonnel = {},
                    onOpenActivities = {}, onOpenApprovals = {}, onOpenReports = {}, onOpenResources = {},
                )
            }
        }
        node(R.string.office_tasks_title_admin).assertExists()
        node(R.string.office_tasks_title_president).assertDoesNotExist()
        compose.onNodeWithTag(OFFICE_TASK_PERSONNEL_TAG).scrollToIfScrollable().assertExists()
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).assertDoesNotExist()
    }

    @Test fun officeManagerRoleSeesNeitherChairTaskEntry() {
        val state = officeState()
        compose.setContent {
            MaterialTheme {
                WorkspaceDashboard(
                    state = state, identity = "manager-device", busy = false,
                    onCreateActivity = {}, onMembershipIssuance = {}, onPersonnel = {},
                    onOpenActivities = {}, onOpenApprovals = {}, onOpenReports = {}, onOpenResources = {},
                )
            }
        }
        compose.onNodeWithText(
            text(R.string.office_role_identity, "manager", text(R.string.office_role_manager)),
        ).assertExists()
        node(R.string.office_tasks_title_manager).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_ISSUANCE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(OFFICE_TASK_PERSONNEL_TAG).assertDoesNotExist()
    }

    @Test fun presidentServerAdminOpensBothOfficeTasks() {
        val state = officeState().put("admins", JSONArray(listOf("president-device")))
        var issuance = 0
        var personnel = 0
        compose.setContent {
            MaterialTheme {
                WorkspaceDashboard(
                    state = state, identity = "president-device", busy = false,
                    onCreateActivity = {}, onMembershipIssuance = { issuance++ }, onPersonnel = { personnel++ },
                    onOpenActivities = {}, onOpenApprovals = {}, onOpenReports = {}, onOpenResources = {},
                )
            }
        }
        compose.onNodeWithTag(OFFICE_TASK_ISSUANCE_TAG).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(OFFICE_TASK_PERSONNEL_TAG).scrollToIfScrollable().performClick()
        compose.runOnIdle {
            assertEquals(1, issuance)
            assertEquals(1, personnel)
        }
    }

    @Test fun officePersonnelEntryOpensTheRealRosterFromTheTaskDesk() {
        val state = officeState()
        compose.setContent {
            JluManagerTheme {
                val stack = rememberNavBackStack(WorkspaceRoot)
                NavDisplay(
                    backStack = stack,
                    onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                    entryProvider = entryProvider {
                        entry<WorkspaceRoot> {
                            WorkspaceDashboard(
                                state = state, identity = "admin-device", busy = false,
                                onCreateActivity = {}, onMembershipIssuance = {},
                                onPersonnel = { stack.add(AccountPersonnel) },
                                onOpenActivities = {}, onOpenApprovals = {}, onOpenReports = {}, onOpenResources = {},
                            )
                        }
                        entry<AccountPersonnel> {
                            PersonnelFlowHost(state, "admin-device", false, FormSender { _, _, _ -> }, onBack = {
                                if (stack.size > 1) stack.removeAt(stack.lastIndex)
                            })
                        }
                    },
                )
            }
        }
        compose.onNodeWithTag(OFFICE_TASK_PERSONNEL_TAG).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(PERSONNEL_TRANSFER_TAG).scrollToIfScrollable().assertExists()
        compose.onNodeWithTag(personnelMemberTag("president")).scrollToIfScrollable().assertExists()
    }

}

private const val MEMBERS = """
{
  "alice": {"device": "alice-device", "club": "a", "roles": ["president"]},
  "bob": {"device": "bob-device", "club": "a", "roles": ["manager", "reviewer"]},
  "carol": {"device": "carol-device", "club": "b", "roles": []}
}
"""

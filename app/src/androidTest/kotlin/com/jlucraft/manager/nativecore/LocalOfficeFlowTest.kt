package com.jlucraft.manager.nativecore

import android.os.ParcelFileDescriptor

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in integration recording against a disposable local node; never injects production data. */
@RequiresFixture
@RunWith(AndroidJUnit4::class)
class LocalOfficeFlowTest {
    companion object {
        @JvmStatic @BeforeClass fun requireDisposableFixture() {
            // Gate before Compose's coroutine test rule: a missing opt-in fixture is a skip,
            // not a failed coroutine test or an excuse to synthesize production UI data.
            assumeTrue(InstrumentationRegistry.getArguments().getString("localOfficeDemo") == "true")
        }
    }
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun text(id: Int) = instrumentation.targetContext.getString(id)
    private fun shellFile(name: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/jlucraft-demo-$name")
    ).use { it.readBytes() }

    @Test fun applicationSubmissionApprovalAndSignup() {
        val config = JSONObject(String(shellFile("config.json"), Charsets.UTF_8))
        val secret = shellFile("device.key")
        val directory = File(instrumentation.targetContext.cacheDir, "local-office-demo")
        try {
            val node = config.getJSONObject("nodes").getJSONObject("organization")
            val input = JSONObject().put("address", node.getString("address"))
                .put("peer", node.getString("peer")).put("genesis_hash", node.getString("genesis"))
                .put("identity", identityConfig(config.getJSONObject("identity").toString(), directory))
            fun execute(action: JSONObject? = null): JSONObject {
                val request = JSONObject(input.toString())
                action?.let { request.put("action", it) }
                val result = JSONObject(UnionNative.execute(secret, request.toString()))
                check(result.isNull("error")) { result.optString("error_code") + ": " + result.optString("error") }
                return result.getJSONObject("state").apply {
                    val view = result.getJSONObject("identity_view")
                    put("members", view.getJSONObject("members")); put("admins", view.getJSONArray("admins"))
                }
            }
            val initial = execute()
            var current by mutableStateOf(initial)
            var pending by mutableStateOf(false)
            var failure: Throwable? = null
            val device = UnionNative.peerId(secret)
            val member = checkNotNull(officeMemberId(initial, device))
            val event = initial.getJSONObject("events").keys().asSequence().first {
                initial.getJSONObject("events").getJSONObject(it).getString("phase") == "open"
            }
            var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
            var focus: androidx.compose.ui.focus.FocusManager? = null
            var view: android.view.View? = null
            fun closeKeyboard() {
                compose.runOnIdle { focus?.clearFocus(); keyboard?.hide() }
                compose.waitUntil(5000) {
                    view?.let { !androidx.core.view.ViewCompat.getRootWindowInsets(it)!!.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) } == true
                }
            }
            compose.setContent { JluManagerTheme { Surface {
                keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                focus = androidx.compose.ui.platform.LocalFocusManager.current
                view = androidx.compose.ui.platform.LocalView.current
                val scope = rememberCoroutineScope()
                var destination by remember { mutableStateOf(WorkspaceDestination.Activities) }
                var page by remember { mutableStateOf("list") }
                var openEvent by remember { mutableStateOf(event) }
                var openFlow by remember { mutableStateOf("") }
                val sendAction: (JSONObject) -> Unit = { action ->
                    pending = true
                    failure = null
                    scope.launch {
                        try { current = withContext(Dispatchers.IO) { execute(action) } }
                        catch (error: Throwable) { failure = error }
                        finally { pending = false }
                    }
                }
                val formSend = FormSender { action, committed, failed ->
                    pending = true
                    failure = null
                    scope.launch {
                        try {
                            val next = withContext(Dispatchers.IO) { execute(action) }
                            current = next
                            committed?.invoke(next)
                        } catch (error: Throwable) {
                            failure = error
                            failed?.invoke(error.message)
                        } finally { pending = false }
                    }
                }
                WorkspaceNavigation(destination, pending, { destination = it; page = "list" }) {
                    when (page) {
                        "draft" -> OfficeDraftCreateFlow(current, pending, onBack = { page = "list" }, onCreated = { page = "list" }, send = formSend)
                        "detail" -> TaskPage(
                            current.optJSONObject("events")?.optJSONObject(openEvent)?.optString("title") ?: text(R.string.office_title),
                            pending,
                            { page = "list" },
                        ) {
                            OfficeEventDetailScreen(current, pending, device, openEvent, send = sendAction)
                        }
                        "flow" -> TaskPage(text(R.string.office_flows), pending, { page = "list" }) {
                            OfficeFlowDetailScreen(current, pending, device, openFlow, onDecision = { page = "decision" }, send = sendAction)
                        }
                        "decision" -> OfficeFlowDecisionScreen(current, pending, device, openFlow, { page = "flow" }, sendAction)
                        else -> when (destination) {
                            WorkspaceDestination.Home -> WorkspaceDashboard(
                                current, device, pending,
                                onCreateActivity = { page = "draft" },
                                onMembershipIssuance = {}, onPersonnel = {},
                                onOpenActivities = { destination = WorkspaceDestination.Activities },
                                onOpenApprovals = { destination = WorkspaceDestination.Tasks },
                                onOpenReports = {}, onOpenResources = {},
                            )
                            WorkspaceDestination.Activities -> ActivityCatalog(
                                current, device, pending,
                                onCreate = { page = "draft" },
                                onOpenEvent = { id -> openEvent = id; page = "detail" },
                                onOpenCompetition = {},
                            )
                            WorkspaceDestination.Tasks -> TaskInbox(current, device, pending) { flow ->
                                openFlow = flow
                                page = "flow"
                            }
                            WorkspaceDestination.Settings -> Text(text(R.string.account_title))
                        }
                    }
                }
            } } }
            compose.onNodeWithText(initial.getJSONObject("events").getJSONObject(event).getString("title")).performClick()
            compose.onNodeWithText(text(R.string.office_signup)).scrollToIfScrollable()
            compose.waitForIdle(); captureScreen("android-live-events-01-before")
            compose.onNodeWithText(text(R.string.office_cancel_signup)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.office_signup)).performClick()
            compose.waitUntil(30000) { !pending && current.optJSONObject("signups")?.optJSONArray(event)?.toString()?.contains(member) == true }
            failure?.let { throw it }
            compose.onNodeWithText(text(R.string.office_signup)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.office_cancel_signup)).scrollToIfScrollable()
            compose.waitForIdle(); captureScreen("android-live-events-02-joined")
            compose.onNodeWithText(text(R.string.office_cancel_signup)).performClick()
            compose.waitUntil(30000) { !pending && current.optJSONObject("signups")?.optJSONArray(event)?.length() == 0 }
            failure?.let { throw it }
            compose.onNodeWithText(text(R.string.office_signup)).assertExists()
            compose.waitForIdle(); captureScreen("android-live-events-03-cancelled")
            compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()

            compose.onNodeWithText(text(R.string.office_create)).scrollToIfScrollable().performClick()
            val applicationTitle = "社团共建申请 ${System.currentTimeMillis()}"
            compose.onNodeWithText(text(R.string.office_name)).scrollToIfScrollable().performTextInput(applicationTitle)
            closeKeyboard()
            compose.onNodeWithText(text(R.string.office_description)).scrollToIfScrollable().performTextInput("周末组织校园共建，申请社团审核。无需附件即可提交。")
            compose.onNodeWithText(text(R.string.office_next)).scrollToIfScrollable().performClick()
            val format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val tomorrow = java.time.LocalDateTime.now().plusDays(1)
            listOf(R.string.office_opens to tomorrow, R.string.office_closes to tomorrow.plusHours(1), R.string.office_starts to tomorrow.plusHours(2)).forEach { (label, time) ->
                compose.onNodeWithText(text(label)).scrollToIfScrollable().performTextInput(time.format(format))
            }
            closeKeyboard()
            compose.waitForIdle(); captureScreen("android-requests-01-compose")
            compose.onNodeWithText(text(R.string.office_next)).scrollToIfScrollable().performClick()
            compose.onNodeWithText(text(R.string.office_save_request)).scrollToIfScrollable().performClick()
            compose.waitUntil(60000) { !pending }; failure?.let { throw it }
            val application = current.getJSONObject("events").keys().asSequence().first { current.getJSONObject("events").getJSONObject(it).getString("title") == applicationTitle }
            compose.onNodeWithText(applicationTitle).scrollToIfScrollable()
            compose.waitForIdle(); captureScreen("android-requests-02-saved")
            compose.onNodeWithText(applicationTitle).performClick()
            compose.onNodeWithText(text(R.string.office_submit)).scrollToIfScrollable().performClick()
            compose.waitUntil(60000) { !pending }; failure?.let { throw it }
            compose.onNodeWithText(text(R.string.office_start_presidents)).scrollToIfScrollable().performClick()
            compose.waitUntil(60000) { !pending }; failure?.let { throw it }
            val flows = current.getJSONObject("flows")
            val flow = flows.keys().asSequence().first { flows.getJSONObject(it).getJSONObject("payload").optString("id") == application }
            compose.waitForIdle(); captureScreen("android-requests-03-submitted")
            compose.onNodeWithText(text(R.string.workspace_nav_tasks)).performClick()
            compose.onNodeWithText(applicationTitle).scrollToIfScrollable().performClick()
            compose.onNodeWithText(text(R.string.office_approval_decision)).scrollToIfScrollable().performClick()
            compose.onNodeWithText(text(R.string.office_comment)).scrollToIfScrollable().performTextInput("同意开展校园共建，请按计划组织。")
            closeKeyboard()
            compose.waitForIdle(); captureScreen("android-approvals-01-comment")
            compose.onNodeWithText(text(R.string.office_approve)).scrollToIfScrollable().performClick()
            compose.waitUntil(60000) { !pending }; failure?.let { throw it }
            assertEquals("approved", current.getJSONObject("flows").getJSONObject(flow).getString("status"))
            compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
            compose.onNodeWithText(text(R.string.office_execute)).scrollToIfScrollable()
            compose.waitForIdle(); captureScreen("android-approvals-02-approved")
            compose.onNodeWithText(text(R.string.office_execute)).performClick()
            compose.waitUntil(60000) { !pending }; failure?.let { throw it }
            assertEquals("executed", current.getJSONObject("flows").getJSONObject(flow).getString("status"))
            assertEquals("approved", current.getJSONObject("events").getJSONObject(application).getString("phase"))
            compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
            compose.onNodeWithText(text(R.string.office_filter_flow_related)).scrollToIfScrollable().performClick()
            compose.onNodeWithText(text(R.string.office_flow_executed)).scrollToIfScrollable().assertExists()
            compose.waitForIdle(); captureScreen("android-approvals-03-executed")

        } finally { secret.fill(0); directory.deleteRecursively() }
    }
}

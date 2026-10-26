package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.util.UUID

/** The draft wizard's steps in order, shared by the wide outline and the progress bar. */
private val DRAFT_STEPS = listOf(
    R.string.office_draft_step_basics,
    R.string.office_draft_step_materials,
    R.string.office_draft_step_confirm,
)

@Serializable internal data object OfficeDraftStepBasics : NavKey
@Serializable internal data object OfficeDraftStepMaterials : NavKey
@Serializable internal data object OfficeDraftStepConfirm : NavKey

private val officeDraftFormSaver = androidx.compose.runtime.saveable.listSaver<OfficeDraftForm, String>(
    save = { listOf(it.id, it.title, it.description, it.attachment, it.opens, it.closes, it.starts) },
    restore = { values ->
        OfficeDraftForm(values[0]).apply {
            title = values[1]
            description = values[2]
            attachment = values[3]
            opens = values[4]
            closes = values[5]
            starts = values[6]
        }
    },
)

/** Draft fields that must survive step transitions, back navigation and rotation. */
internal class OfficeDraftForm(val id: String = UUID.randomUUID().toString()) {
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var attachment by mutableStateOf("")
    var opens by mutableStateOf("")
    var closes by mutableStateOf("")
    var starts by mutableStateOf("")

    val openTime: Long? get() = parseLocalDateTime(opens)
    val closeTime: Long? get() = parseLocalDateTime(closes)
    val startTime: Long? get() = parseLocalDateTime(starts)
    val timesValid: Boolean
        get() {
            val open = openTime ?: return false
            val close = closeTime ?: return false
            val start = startTime ?: return false
            return open >= 0 && open < close && close <= start
        }

    fun request(): JSONObject = JSONObject()
        .put("type", "local.events.create")
        .put("id", id)
        .put("title", title.trim())
        .put("description", description)
        .put("attachment", attachment.ifBlank { null } ?: JSONObject.NULL)
        .put(
            "window",
            JSONObject().put("opens_ms", requireNotNull(openTime)).put("closes_ms", requireNotNull(closeTime)).put("starts_ms", requireNotNull(startTime)),
        )
}

/**
 * New activity draft in three pages: what it is, when it runs and which material it adds, then
 * one confirmation. The list page only opens this flow; it never renders the form itself.
 */
@Composable
internal fun OfficeDraftCreateFlow(
    state: JSONObject?,
    busy: Boolean,
    canCreate: Boolean = true,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    send: FormSender,
) {
    val form = androidx.compose.runtime.saveable.rememberSaveable(saver = officeDraftFormSaver) { OfficeDraftForm() }
    val submission = rememberFormSubmission()
    val backStack = rememberNavBackStack(OfficeDraftStepBasics)
    val pop: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack()
    }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<OfficeDraftStepBasics> {
                TaskPage(stringResource(R.string.office_create_title), busy, pop, steps = DRAFT_STEPS, currentStep = 1) {
                    StepProgress(step = 1, total = 3)
                    if (!canCreate) {
                        Text(stringResource(R.string.office_create_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@TaskPage
                    }
                    OutlinedTextField(
                        value = form.title,
                        onValueChange = { form.title = it },
                        label = { Text(stringResource(R.string.office_name)) },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = { form.description = it },
                        label = { Text(stringResource(R.string.office_description)) },
                        enabled = !busy,
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(R.string.office_next, !busy && form.title.isNotBlank() && form.description.isNotBlank()) {
                        backStack.add(OfficeDraftStepMaterials)
                    }
                }
            }
            entry<OfficeDraftStepMaterials> {
                TaskPage(stringResource(R.string.office_create_title), busy, pop, steps = DRAFT_STEPS, currentStep = 2) {
                    StepProgress(step = 2, total = 3)
                    if (!canCreate) {
                        Text(stringResource(R.string.office_create_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@TaskPage
                    }
                    OfficeAttachment(state, busy, form.id, form.attachment, { form.attachment = it }, send = { action -> send.send(action) })
                    Text(
                        stringResource(R.string.office_window_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DateTimeField(form.opens, stringResource(R.string.office_opens), !busy) { form.opens = it }
                    DateTimeField(form.closes, stringResource(R.string.office_closes), !busy) { form.closes = it }
                    DateTimeField(form.starts, stringResource(R.string.office_starts), !busy) { form.starts = it }
                    ActionButton(R.string.office_next, !busy && form.timesValid) {
                        backStack.add(OfficeDraftStepConfirm)
                    }
                    TextButtonLikeBack(pop, busy)
                }
            }
            entry<OfficeDraftStepConfirm> {
                TaskPage(stringResource(R.string.office_create_title), busy, pop, steps = DRAFT_STEPS, currentStep = 3) {
                    StepProgress(step = 3, total = 3)
                    if (!canCreate) {
                        Text(stringResource(R.string.office_create_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@TaskPage
                    }
                    ManagerCard(
                        title = form.title,
                        emphasized = false,
                    ) {
                        Text(form.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                R.string.office_create_window,
                                form.opens,
                                form.closes,
                                form.starts,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (form.attachment.isNotBlank()) Text(
                            stringResource(R.string.office_create_attachment, shortIdentifier(form.attachment)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ActionButton(R.string.office_save_request, !busy && canCreate && state != null && form.timesValid && !submission.pending) {
                        // Only an accepted snapshot containing this event id completes the draft.
                        submission.submit(send, form.request()) { snapshot ->
                            if (snapshot.optJSONObject("events")?.has(form.id) == true) {
                                onCreated(form.id)
                                true
                            } else false
                        }
                    }
                    submission.failure?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}

@Composable
private fun TextButtonLikeBack(onBack: () -> Unit, busy: Boolean) {
    androidx.compose.material3.TextButton(
        onClick = onBack,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.workflow_back)) }
}

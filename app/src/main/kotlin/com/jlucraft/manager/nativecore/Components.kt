package com.jlucraft.manager.nativecore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

val MIN_TOUCH_TARGET = 48.dp

/** Upper bound for a single-pane page so cards do not stretch across an expanded window. */
val CONTENT_MAX_WIDTH = 840.dp

/** True when the window is wide enough to spend its full width instead of one centered phone column. */
@Composable
internal fun isWideLayout(): Boolean = currentWindowAdaptiveInfo().windowSizeClass
    .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

/** Fixed width of the leading pane on an expanded window. */
internal val WIDE_LEADING_PANE_WIDTH = 300.dp

/** The compact single-column page body shared by the wide-capable layouts below. */
@Composable
private fun CompactColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(SPACING_L),
            verticalArrangement = Arrangement.spacedBy(SPACING_L),
            content = content,
        )
    }
}

/**
 * Master/detail page body.
 *
 * A compact window keeps exactly the stacked order it had: [master] first, then [detail], in one
 * scrolling column. An expanded window puts [master] in a fixed-width leading pane and [detail] in
 * the remaining width, so the workbench, settings and league pages spend their width on two panes
 * instead of centering a phone column inside a large empty window.
 */
@Composable
internal fun MasterDetailPage(
    master: @Composable ColumnScope.() -> Unit,
    detail: @Composable ColumnScope.() -> Unit,
) {
    if (!isWideLayout()) {
        CompactColumn {
            master()
            detail()
        }
        return
    }
    Row(Modifier.fillMaxSize()) {
        PagePane(Modifier.width(WIDE_LEADING_PANE_WIDTH)) { master() }
        PagePane(Modifier.weight(1f)) { detail() }
    }
}

/** Test hook for the outline's current row; the highlight itself is a colour, which tests cannot read. */
internal const val STEP_OUTLINE_CURRENT_TAG = "step-outline-current"

@Composable
internal fun PublicJsonActions(value: String, label: Int, enabled: Boolean = true) {
    val context = LocalContext.current
    val labelText = stringResource(label)
    Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(enabled = enabled && value.isNotBlank(), onClick = { copyToClipboard(context, labelText, value) }, modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.identity_export_copy))
        }
        OutlinedButton(enabled = enabled && value.isNotBlank(), onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value)
            context.startActivity(Intent.createChooser(send, labelText))
        }, modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.identity_export_share)) }
    }
}

/** One row of the wide step outline: the step number and name, with the current step emphasised. */
@Composable
private fun StepOutlineRow(number: Int, title: String, current: Boolean, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (current) Modifier.testTag(STEP_OUTLINE_CURRENT_TAG) else Modifier,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPACING_S),
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.labelLarge,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            title,
            style = if (current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = when {
                current -> MaterialTheme.colorScheme.primary
                done -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * The step outline shown beside a multi-step page on an expanded window.
 *
 * It is derived from the same step names the pages already print, so it adds navigation context
 * without introducing state or data of its own.
 */
@Composable
internal fun StepOutline(current: Int, steps: List<Int>) {
    Text(
        stringResource(R.string.wide_outline_title),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    steps.forEachIndexed { index, title ->
        StepOutlineRow(
            number = index + 1,
            title = stringResource(title),
            current = index + 1 == current,
            done = index + 1 < current,
        )
    }
    Text(
        stringResource(R.string.step_progress, current, steps.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One scrolling pane of a wide page body. */
@Composable
internal fun PagePane(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(SPACING_L),
        verticalArrangement = Arrangement.spacedBy(SPACING_L),
        content = content,
    )
}

val SPACING_XS = 4.dp
val SPACING_S = 8.dp
val SPACING_M = 12.dp
val SPACING_L = 16.dp
val SPACING_XL = 32.dp
val LOCKED_CONTENT_MAX_WIDTH = 360.dp
const val DISABLED_BORDER_ALPHA = 0.38f

private val LOCAL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

internal fun parseLocalDateTime(value: String): Long? = runCatching {
    LocalDateTime.parse(value.trim(), LOCAL_DATE_TIME_FORMAT)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

private const val DEFAULT_PICKER_HOUR = 9

/** Typed or picked local date-time; the text stays the single source of truth so callers keep using [parseLocalDateTime]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(value: String, label: String, enabled: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var step by remember { mutableStateOf(PickerStep.Closed) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val current = remember(value) { runCatching { LocalDateTime.parse(value.trim(), LOCAL_DATE_TIME_FORMAT) }.getOrNull() }
    val invalid = value.isNotBlank() && current == null
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled, singleLine = true, isError = invalid,
        placeholder = { Text(stringResource(R.string.datetime_placeholder)) },
        supportingText = if (invalid) ({ Text(stringResource(R.string.governance_datetime_hint)) }) else null,
        trailingIcon = {
            IconButton(onClick = { step = PickerStep.Date }, enabled = enabled, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.datetime_pick))
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
    if (step == PickerStep.Date) {
        val initial = (current?.toLocalDate() ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dates = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { step = PickerStep.Closed },
            confirmButton = {
                TextButton(enabled = dates.selectedDateMillis != null, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = {
                    pickedDate = Instant.ofEpochMilli(dates.selectedDateMillis ?: return@TextButton).atZone(ZoneOffset.UTC).toLocalDate()
                    step = PickerStep.Time
                }) { Text(stringResource(R.string.datetime_next)) }
            },
            dismissButton = {
                TextButton(onClick = { step = PickerStep.Closed }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.dialog_cancel)) }
            },
        ) { DatePicker(dates) }
    }
    if (step == PickerStep.Time) {
        val time = rememberTimePickerState(current?.hour ?: DEFAULT_PICKER_HOUR, current?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { step = PickerStep.Closed },
            text = { TimePicker(time) },
            confirmButton = {
                TextButton(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = {
                    pickedDate?.let { onChange(LocalDateTime.of(it, LocalTime.of(time.hour, time.minute)).format(LOCAL_DATE_TIME_FORMAT)) }
                    step = PickerStep.Closed
                }) { Text(stringResource(R.string.datetime_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { step = PickerStep.Closed }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

private enum class PickerStep { Closed, Date, Time }

/** Card with the standard padding/title/status header. [emphasized] cards are elevated; secondary or informational content passes `false` for the flat tonal variant so the page keeps a clear container hierarchy. */
@Composable
fun ManagerCard(
    title: String? = null,
    helper: String? = null,
    statusText: String? = null,
    statusActive: Boolean = false,
    emphasized: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(SPACING_L),
            verticalArrangement = Arrangement.spacedBy(SPACING_M),
        ) {
            if (title != null || helper != null || statusText != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
                    if (helper != null) {
                        Text(
                            helper,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (statusText != null) StatusPill(text = statusText, active = statusActive)
            }
            content()
        }
    }
    if (emphasized) ElevatedCard(modifier = Modifier.fillMaxWidth()) { body() } else Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { body() }
}

/** Quiet panel nested in a card (scoreboard, dispute, log): tonal surface with the standard inner padding. */
@Composable
internal fun DetailSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SPACING_M),
            verticalArrangement = Arrangement.spacedBy(SPACING_XS),
            content = content,
        )
    }
}

@Composable
internal fun StatusPill(text: String, active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = SPACING_XS),
        )
    }
}

internal fun copyToClipboard(context: android.content.Context, label: String, value: String) {
    context.getSystemService(android.content.ClipboardManager::class.java)
        .setPrimaryClip(android.content.ClipData.newPlainText(label, value))
}

private const val SHORT_IDENTIFIER_EDGE = 8

internal fun shortIdentifier(value: String): String =
    if (value.length > SHORT_IDENTIFIER_EDGE * 2 + 1) "${value.take(SHORT_IDENTIFIER_EDGE)}…${value.takeLast(SHORT_IDENTIFIER_EDGE)}" else value

@Composable
fun IdentifierDetails(value: String, label: Int = R.string.identifier_details) {
    if (value.isBlank()) return
    var expanded by remember(value) { mutableStateOf(false) }
    var copied by remember(value) { mutableStateOf(false) }
    val context = LocalContext.current
    val labelText = stringResource(label)
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
        Text(labelText)
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = shrinkVertically() + fadeOut()) {
        Column {
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = {
                copyToClipboard(context, labelText, value)
                copied = true
            }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(if (copied) R.string.identifier_copied else R.string.identifier_copy)) }
        }
    }
}

/** Persistent, dismissible error banner: announced to screen readers and selectable so long messages stay readable. */
@Composable
internal fun ErrorBanner(text: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(modifier = Modifier.weight(1f).padding(vertical = SPACING_M, horizontal = SPACING_L)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.error_dismiss))
            }
        }
    }
}

/** Long checksums wrap at this many lines before the field scrolls internally. */
private const val CHECKSUM_MIN_LINES = 2
private const val CHECKSUM_MAX_LINES = 3

/**
 * Full identifier or checksum on a review page: monospaced, wrapped over two or three lines, and
 * never middle-truncated, so the value stays comparable against the source the organizer sent.
 */
@Composable
internal fun IdentifierField(value: String, label: String? = null) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
        if (label != null) Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(SPACING_M),
                )
            }
        }
    }
}

/**
 * A long checksum being typed: multiline monospaced input so the tail stays visible instead of
 * being clipped by a single-line field.
 */
@Composable
internal fun ChecksumField(
    value: String,
    label: String,
    enabled: Boolean,
    hint: String? = null,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        supportingText = hint?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        minLines = CHECKSUM_MIN_LINES,
        maxLines = CHECKSUM_MAX_LINES,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Same text format as [parseLocalDateTime], used to seed editable date-time fields from snapshot millis. */
internal fun formatLocalDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(LOCAL_DATE_TIME_FORMAT)

/**
 * Pretty-printed JSON with byte arrays collapsed into one hexadecimal line.
 *
 * Protobuf-backed fields such as a public key arrive as a JSON array of decimal bytes. Printing them
 * one byte per line fills a screen and buries every other field, so each byte array is serialized as
 * a single continuous hex string: readable, copyable, and comparable against the source material.
 */
internal fun technicalJson(value: JSONObject, indent: Int = 0): String = buildString {
    val pad = "  ".repeat(indent)
    val childPad = "  ".repeat(indent + 1)
    append("{\n")
    val names = value.keys().asSequence().toList()
    names.forEachIndexed { index, name ->
        append(childPad).append(JSONObject.quote(name)).append(": ")
        append(technicalValue(value.opt(name), indent + 1))
        if (index != names.lastIndex) append(',')
        append('\n')
    }
    append(pad).append('}')
}

private fun technicalValue(value: Any?, indent: Int): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONArray -> if (isByteArray(value)) hexBytes(value) else technicalArray(value, indent)
    is JSONObject -> technicalJson(value, indent)
    is Number, is Boolean -> value.toString()
    else -> JSONObject.quote(value.toString())
}

private fun technicalArray(value: JSONArray, indent: Int): String {
    if (value.length() == 0) return "[]"
    val pad = "  ".repeat(indent)
    val childPad = "  ".repeat(indent + 1)
    return buildString {
        append("[\n")
        for (index in 0 until value.length()) {
            append(childPad).append(technicalValue(value.opt(index), indent + 1))
            if (index != value.length() - 1) append(',')
            append('\n')
        }
        append(pad).append(']')
    }
}

/** A protobuf byte field, not a real list: every element is an unsigned byte and the array is non-empty. */
private fun isByteArray(value: JSONArray): Boolean {
    if (value.length() == 0) return false
    return (0 until value.length()).all { index ->
        val element = value.opt(index)
        element is Int && element in 0..255
    }
}

private fun hexBytes(value: JSONArray): String = buildString {
    append('"')
    for (index in 0 until value.length()) {
        append(value.optInt(index).toString(16).padStart(2, '0'))
    }
    append('"')
}

/** One before/after pair on a review page, so the changed value can be compared. */
@Composable
internal fun ChangeCompareRow(label: String, before: String, after: String, changed: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.compare_before, before.ifBlank { stringResource(R.string.identity_none) }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.compare_after, after.ifBlank { stringResource(R.string.identity_none) }),
            style = MaterialTheme.typography.bodyMedium,
            color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Visible back control for every secondary page; it matches the system back gesture. */
@Composable
internal fun BackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigation_back))
    }
}

/** One-line progress for a flow that is split across dedicated pages. */
@Composable
internal fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
        Text(
            stringResource(R.string.step_progress, step, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { step.toFloat() / total }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * A bounded numeric choice with explicit stepper buttons.
 *
 * Long-form number entry on a touch screen has no affordance: nothing shows that a value is
 * expected, what range applies, or that the field is adjustable. This adds step targets of at least
 * [MIN_TOUCH_TARGET] on both sides of the value, clamps to [range], and prints the range so the
 * operator never has to guess a rejected value.
 */
@Composable
internal fun NumberStepperField(
    value: String,
    label: Int,
    range: IntRange,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    step: Int = 1,
    onValueChange: (String) -> Unit,
) {
    val current = value.trim().toIntOrNull()
    val labelText = stringResource(label)
    val decreaseLabel = stringResource(R.string.number_stepper_decrease)
    val increaseLabel = stringResource(R.string.number_stepper_increase)
    val canDecrease = enabled && current != null && current > range.first
    val canIncrease = enabled && (current == null || current < range.last)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
        Text(
            labelText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SPACING_S),
        ) {
            TextButton(
                onClick = { onValueChange(((current ?: range.first) - step).coerceIn(range).toString()) },
                enabled = canDecrease,
                modifier = Modifier.size(MIN_TOUCH_TARGET),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    stringResource(R.string.number_stepper_minus),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = decreaseLabel },
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = { typed -> onValueChange(typed.filter(Char::isDigit)) },
                enabled = enabled,
                singleLine = true,
                isError = current != null && current !in range,
                // The visible label is a sibling Text, so the field itself needs a name for
                // TalkBack; this adds the accessible name without touching the layout.
                modifier = Modifier.weight(1f).semantics { contentDescription = labelText },
            )
            TextButton(
                onClick = { onValueChange(((current ?: range.first) + step).coerceIn(range).toString()) },
                enabled = canIncrease,
                modifier = Modifier.size(MIN_TOUCH_TARGET),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    stringResource(R.string.number_stepper_plus),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = increaseLabel },
                )
            }
        }
        Text(
            stringResource(R.string.number_stepper_range, range.first, range.last),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Menu row that opens exactly one task page; the help line states what waits there. */
@Composable
internal fun MenuEntry(label: String, help: String? = null, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (help != null) Text(help, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * A roster row: a bordered list item. It takes a trailing chevron and a 48dp tap target
 * only when [onClick] is present, so a read-only roster row is bordered without pretending to be
 * tappable, and a selectable one can never look like static text.
 */
@Composable
internal fun RosterRow(
    label: String,
    help: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val body: @Composable RowScope.() -> Unit = {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (help != null) Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(MIN_TOUCH_TARGET / 2),
        )
    }
    if (onClick != null) OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        content = body,
    ) else OutlinedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SPACING_M),
            verticalAlignment = Alignment.CenterVertically,
            content = body,
        )
    }
}

/**
 * M3 screen shell: pinned top app bar, optional busy indicator, insets-aware content.
 *
 * [bottomBar] is the pinned action bar for a wizard or form page: it sits above the system
 * navigation bar, keeps the keyboard below it, and never scrolls away with the form. Pages
 * without a pinned action keep passing their actions inline in [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManagerScreenScaffold(
    title: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = floatingActionButton,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/**
 * A page's pinned bottom action, published by a nested step composable and rendered by the page that
 * owns the scaffold. A wizard step decides what its forward action is; the page owns where that
 * action sits, so the action stays fixed above the keyboard and the gesture bar.
 */
internal class BottomActionSlot {
    internal var action by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** Publishes this step's pinned action for the current composition. */
    fun set(action: @Composable () -> Unit) {
        this.action = action
    }
}

@Composable
internal fun rememberBottomActionSlot(): BottomActionSlot = remember { BottomActionSlot() }

/**
 * Pinned action bar for the advance/submit action of one wizard or form page: a hairline divider,
 * the actions themselves, and the system navigation-bar safe padding so the gesture bar never
 * clips the button.
 */
@Composable
internal fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SPACING_L, vertical = SPACING_M),
                verticalArrangement = Arrangement.spacedBy(SPACING_S),
                content = content,
            )
        }
    }
}


/** A named page section above one or more cards. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = SPACING_XS),
    )
}

/** A whole section, headed by [SectionHeader], that reveals its cards only when expanded. */
@Composable
internal fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = false,
    showLabel: Int = R.string.advanced_show,
    hideLabel: Int = R.string.advanced_hide,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(text = title, modifier = Modifier.weight(1f))
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(if (expanded) hideLabel else showLabel))
        }
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = shrinkVertically() + fadeOut()) {
        Column(content = content)
    }
}

/** Shared action button: primary, secondary, or destructive with a confirmation dialog. */
internal enum class ActionKind { Primary, Secondary, Confirmed, Destructive }

@Composable
internal fun ActionButton(
    label: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    kind: ActionKind = ActionKind.Primary,
    confirmText: Int = R.string.confirm_irreversible,
    click: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val buttonModifier = modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
    val onClick = if (kind == ActionKind.Destructive || kind == ActionKind.Confirmed) ({ confirming = true }) else click
    when (kind) {
        ActionKind.Primary, ActionKind.Confirmed -> Button(onClick = onClick, enabled = enabled, modifier = buttonModifier) { Text(stringResource(label)) }
        ActionKind.Secondary -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = buttonModifier) { Text(stringResource(label)) }
        ActionKind.Destructive -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = buttonModifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else DISABLED_BORDER_ALPHA)),
        ) { Text(stringResource(label)) }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(label)) },
        text = { Text(stringResource(confirmText)) },
        confirmButton = {
            TextButton(onClick = { confirming = false; click() }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                colors = ButtonDefaults.textButtonColors(contentColor = if (kind == ActionKind.Destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) { Text(stringResource(label)) }
        },
        dismissButton = {
            TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.confirm_dismiss)) }
        },
    )
}

/** Existing options first; manual entry stays available as a fallback. */
@Composable
internal fun ChoiceField(label: Int, value: String, options: List<String>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(options.isEmpty()) }
    if (manual || options.isEmpty()) {
        OutlinedTextField(value, onChange, label = { Text(stringResource(label)) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (options.isNotEmpty()) TextButton(onClick = { manual = false }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.governance_choose_from_list))
        }
    } else {
        val labelText = stringResource(label)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
            Text(
                labelText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
                        .semantics { contentDescription = labelText },
                ) {
                    Text(if (value.isBlank()) stringResource(R.string.governance_choose) else value)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.governance_manual_entry)) }, onClick = { manual = true; expanded = false })
                }
            }
        }
    }
}

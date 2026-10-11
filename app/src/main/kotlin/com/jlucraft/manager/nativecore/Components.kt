package com.jlucraft.manager.nativecore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
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

val SPACING_XS = 4.dp
val SPACING_S = 8.dp
val SPACING_M = 12.dp
val SPACING_L = 16.dp
val SPACING_XL = 32.dp
val DETAIL_SURFACE_CORNER = 12.dp
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

@Composable
fun ManagerCard(
    title: String? = null,
    helper: String? = null,
    statusText: String? = null,
    statusActive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SPACING_L),
            verticalArrangement = Arrangement.spacedBy(SPACING_M),
        ) {
            if (title != null || statusText != null) Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
internal fun StatusPill(text: String, active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(percent = 50),
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
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
        Text(stringResource(label))
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = shrinkVertically() + fadeOut()) {
        Column {
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = {
                copyToClipboard(context, context.getString(R.string.identifier_details), value)
                copied = true
            }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(if (copied) R.string.identifier_copied else R.string.identifier_copy)) }
        }
    }
}

/** M3 screen shell: pinned top app bar, optional busy indicator, insets-aware content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManagerScreenScaffold(
    title: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
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
        modifier = modifier.padding(horizontal = SPACING_L, vertical = SPACING_M),
    )
}

/** A whole section, headed by [SectionHeader], that reveals its cards only when expanded. */
@Composable
internal fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(text = title, modifier = Modifier.weight(1f))
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(if (expanded) R.string.advanced_hide else R.string.advanced_show))
        }
    }
    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = shrinkVertically() + fadeOut()) {
        Column(content = content)
    }
}

/** Shared action button: primary, secondary, or destructive with a confirmation dialog. */
internal enum class ActionKind { Primary, Secondary, Confirmed, Destructive }

@Composable
internal fun ActionButton(label: Int, enabled: Boolean, kind: ActionKind = ActionKind.Primary, confirmText: Int = R.string.confirm_irreversible, modifier: Modifier = Modifier.fillMaxWidth(), click: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val buttonModifier = modifier.heightIn(min = MIN_TOUCH_TARGET)
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

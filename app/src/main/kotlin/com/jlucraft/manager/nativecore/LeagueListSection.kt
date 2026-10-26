package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jlucraft.manager.R
import org.json.JSONObject

/** League list pane: one summary row per league; selecting opens the detail pane. */
@Composable
fun LeagueListSection(
    state: JSONObject?,
    busy: Boolean,
    onConnect: (() -> Unit)? = null,
    onStart: (String) -> Unit,
    onOpen: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SPACING_M)) {
        SectionHeader(stringResource(R.string.league_list_title))
        if (state == null) {
            Text(
                stringResource(R.string.league_list_disconnected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onConnect != null) ActionButton(R.string.office_disconnected_action, enabled = !busy) { onConnect() }
        } else {
            val leagues = state.optJSONObject("leagues")
            if (leagues == null || leagues.length() == 0) {
                Text(
                    stringResource(R.string.league_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                leagues.keys().asSequence().toList().sorted().forEach { id ->
                    key(id) { LeagueSummary(id = id, item = leagues.getJSONObject(id), onOpen = onOpen) }
                }
            }
        }
    }
}

@Composable
private fun LeagueSummary(id: String, item: JSONObject, onOpen: ((String) -> Unit)?) {
    val phase = item.getString("phase")
    val (phaseLabel, phaseActive) = phaseStatus(phase)
    val rules = item.getJSONObject("rules")
    ManagerCard(
        title = id,
        helper = stringResource(
            R.string.governance_rules,
            rules.optInt("seats"),
            stringResource(if (rules.optBoolean("cross_club")) R.string.league_cross_club else R.string.league_same_school),
        ),
        statusText = phaseLabel,
        statusActive = phaseActive,
    ) {
        Text(
            stringResource(R.string.league_team_count, item.optJSONObject("teams")?.length() ?: 0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onOpen != null) {
            TextButton(onClick = { onOpen(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.league_open_detail))
            }
        }
    }
}

@Composable
 internal fun phaseStatus(phase: String): Pair<String, Boolean> = when (phase) {
    "Registration" -> stringResource(R.string.league_phase_registration) to true
    "Playing", "Ongoing", "Running" -> stringResource(R.string.league_phase_ongoing) to false
    "Finished" -> stringResource(R.string.league_phase_finished) to false
    else -> stringResource(R.string.league_phase_unknown) to false
}

@Composable
 internal fun TeamRow(team: String, roster: JSONObject) {
    val registered = roster.getBoolean("registered")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(team, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.league_team_members, roster.getJSONArray("members").length()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(SPACING_S))
        Text(
            stringResource(if (registered) R.string.league_registered else R.string.league_not_registered),
            style = MaterialTheme.typography.labelMedium,
            color = if (registered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
 internal fun Scoreboard(title: String, scores: JSONObject) {
    val entries = scores.keys().asSequence().toList().sortedByDescending { scores.getLong(it) }
    DetailSurface {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.league_no_scores),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEachIndexed { index, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.league_points, scores.getLong(name)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

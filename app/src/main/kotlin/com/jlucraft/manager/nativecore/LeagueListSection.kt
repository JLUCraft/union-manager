package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jlucraft.manager.R
import org.json.JSONObject


@Composable
fun LeagueListSection(state: JSONObject?, busy: Boolean, onStart: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SPACING_M)) {
        Text(stringResource(R.string.league_list_title), style = MaterialTheme.typography.titleMedium)
        if (state == null) {
            Text(
                stringResource(R.string.league_list_disconnected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    LeagueCard(id = id, item = leagues.getJSONObject(id), busy = busy, onStart = onStart)
                }
            }
        }
    }
}

@Composable
private fun LeagueCard(id: String, item: JSONObject, busy: Boolean, onStart: (String) -> Unit) {
    val phase = item.getString("phase")
    val (phaseLabel, phaseActive) = phaseStatus(phase)
    val rules = item.optJSONObject("rules")
    ManagerCard(
        title = id,
        helper = if (rules != null) {
            stringResource(
                R.string.governance_rules,
                rules.optInt("seats"),
                stringResource(if (rules.optBoolean("cross_club")) R.string.league_cross_club else R.string.league_same_school),
            )
        } else null,
        statusText = phaseLabel,
        statusActive = phaseActive,
    ) {
        Text(stringResource(R.string.league_teams_title), style = MaterialTheme.typography.titleSmall)
        val teams = item.optJSONObject("teams")
        if (teams == null || teams.length() == 0) {
            Text(
                stringResource(R.string.league_no_teams),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            teams.keys().asSequence().toList().sorted().forEach { team ->
                TeamRow(team = team, roster = teams.getJSONObject(team))
            }
        }
        if (phase == "Registration") {
            Button(
                onClick = { onStart(id) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            ) {
                Text(stringResource(R.string.league_lock_and_start))
            }
        }
        Scoreboard(stringResource(R.string.league_team_scoreboard), item.optJSONObject("team_scores") ?: JSONObject())
        Scoreboard(stringResource(R.string.league_player_scoreboard), item.optJSONObject("player_scores") ?: JSONObject())
    }
}

@Composable
private fun phaseStatus(phase: String): Pair<String, Boolean> = when (phase) {
    "Registration" -> stringResource(R.string.league_phase_registration) to true
    "Playing", "Ongoing", "Running" -> stringResource(R.string.league_phase_ongoing) to false
    "Finished" -> stringResource(R.string.league_phase_finished) to false
    else -> stringResource(R.string.league_phase_unknown) to false
}

@Composable
private fun TeamRow(team: String, roster: JSONObject) {
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
            color = if (registered) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Scoreboard(title: String, scores: JSONObject) {
    val entries = scores.keys().asSequence().toList().sortedByDescending { scores.getLong(it) }
    Column(verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
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

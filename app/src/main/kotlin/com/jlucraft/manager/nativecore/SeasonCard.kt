package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONObject

/** Season summaries and the two explicit season tasks; neither form lives on this page. */
@Composable
fun SeasonCard(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onOpenCreateSeason: (() -> Unit)? = null,
    onOpenSchedule: (() -> Unit)? = null,
    request: (JSONObject) -> Unit,
) {
    if (state == null) return
    ManagerCard(
        title = stringResource(R.string.governance_season_schedule),
        helper = stringResource(R.string.governance_season_schedule_helper),
    ) {
        if (onOpenCreateSeason != null) MenuEntry(
            label = stringResource(R.string.governance_create_season),
            enabled = !busy,
            onClick = onOpenCreateSeason,
        )
        if (onOpenSchedule != null) MenuEntry(
            label = stringResource(R.string.governance_schedule_league),
            help = stringResource(R.string.league_season_schedule_help),
            enabled = !busy,
            onClick = onOpenSchedule,
        )
        val seasons = state.optJSONObject("seasons") ?: JSONObject()
        if (seasons.length() > 0) Text(
            stringResource(R.string.governance_existing_seasons),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        seasons.keys().asSequence().sorted().forEach { id ->
            // Title and id are two facts; the id gets its own line instead of a joined label.
            Text(
                stringResource(R.string.governance_season_title, seasons.getJSONObject(id).optString("title")),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val leagues = state.optJSONObject("leagues") ?: JSONObject()
        leagues.keys().asSequence().sorted().forEach { id ->
            val item = leagues.getJSONObject(id)
            if (item.optString("phase") == "Playing" && item.optString("organizer") == identity) {
                val paused = item.optBoolean("manual_pause")
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET), onClick = {
                    request(JSONObject().put("type", "pause_league").put("league", id).put("paused", !paused))
                }) {
                    Text(stringResource(if (paused) R.string.governance_resume_league else R.string.governance_pause_league, id))
                }
            }
        }
    }
}

private fun parseScoreAdjustments(value: String): JSONObject? = runCatching {
    val result = JSONObject()
    value.lines().filter { it.isNotBlank() }.forEach {
        val pair = it.trim().split(Regex("\\s+"))
        require(pair.size == 2 && !result.has(pair[0]))
        result.put(pair[0], pair[1].toLong())
    }
    result
}.getOrNull()

/** Dispute summaries; opening one moves the resolution form to its own page. */
@Composable
internal fun DisputeListCard(state: JSONObject, busy: Boolean, onOpen: (String) -> Unit) {
    val disputes = state.optJSONObject("disputes") ?: JSONObject()
    ManagerCard(title = stringResource(R.string.league_disputes_pending, disputes.length())) {
        if (disputes.length() == 0) {
            Text(stringResource(R.string.governance_no_disputes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        disputes.keys().asSequence().sorted().forEach { id ->
            val dispute = disputes.getJSONObject(id)
            key(id) {
                MenuEntry(
                    label = stringResource(R.string.governance_dispute_entry, dispute.optString("league"), dispute.optString("member")),
                    help = dispute.optString("reason"),
                    enabled = !busy,
                    onClick = { onOpen(id) },
                )
            }
        }
    }
}

/** One dispute with its resolution form; the list page never renders this form. */
@Composable
internal fun DisputeDetailCard(state: JSONObject, disputeId: String, busy: Boolean, request: (JSONObject) -> Unit) {
    val dispute = state.optJSONObject("disputes")?.optJSONObject(disputeId)
    if (dispute == null) {
        Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var response by remember(disputeId) { mutableStateOf("") }
    var players by remember(disputeId) { mutableStateOf("") }
    var teams by remember(disputeId) { mutableStateOf("") }
    ManagerCard(
        title = stringResource(R.string.governance_dispute_entry, dispute.optString("league"), dispute.optString("member")),
        helper = stringResource(R.string.governance_dispute_helper),
    ) {
        Text(dispute.optString("reason"), style = MaterialTheme.typography.bodyMedium)
        val resolution = dispute.optJSONObject("resolution")
        if (resolution != null) {
            Text(resolution.optString("response"), style = MaterialTheme.typography.bodyMedium)
            return@ManagerCard
        }
        OutlinedTextField(response, { response = it }, label = { Text(stringResource(R.string.governance_review_reason)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(players, { players = it }, label = { Text(stringResource(R.string.governance_player_scores)) }, enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(teams, { teams = it }, label = { Text(stringResource(R.string.governance_team_scores)) }, enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = !busy && response.isNotBlank() && parseScoreAdjustments(players) != null && parseScoreAdjustments(teams) != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            onClick = {
                request(JSONObject().put("type", "resolve_dispute").put("id", disputeId).put("response", response)
                    .put("players", parseScoreAdjustments(players)).put("teams", parseScoreAdjustments(teams)))
            },
        ) { Text(stringResource(R.string.governance_submit_review)) }
    }
}

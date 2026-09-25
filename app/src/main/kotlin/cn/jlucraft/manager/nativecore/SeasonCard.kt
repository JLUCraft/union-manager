package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.jlucraft.manager.R
import org.json.JSONObject

private val DISPUTE_CARD_RADIUS = 12.dp
private val DISPUTE_CARD_PADDING = 12.dp

@Composable
fun SeasonCard(state: JSONObject?, identity: String, busy: Boolean, request: (JSONObject) -> Unit) {
    if (state == null) return
    var season by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var league by remember { mutableStateOf("") }
    var opens by remember { mutableStateOf("") }
    var closes by remember { mutableStateOf("") }
    var starts by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var players by remember { mutableStateOf("") }
    var teams by remember { mutableStateOf("") }
    fun scores(value: String): JSONObject? = runCatching {
        val result = JSONObject()
        value.lines().filter { it.isNotBlank() }.forEach {
            val pair = it.trim().split(Regex("\\s+"))
            require(pair.size == 2 && !result.has(pair[0]))
            result.put(pair[0], pair[1].toLong())
        }
        result
    }.getOrNull()
    val a = parseLocalDateTime(opens); val b = parseLocalDateTime(closes); val c = parseLocalDateTime(starts)
    ManagerCard(stringResource(R.string.governance_season_schedule), helper = stringResource(R.string.governance_season_schedule_helper)) {
        Text(stringResource(R.string.governance_create_season), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(season, { season = it }, label = { Text(stringResource(R.string.governance_season_id)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.governance_season_name)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && season.isNotBlank() && title.isNotBlank(), modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { request(JSONObject().put("type", "create_season").put("id", season).put("title", title)) }) { Text(stringResource(R.string.governance_create_season)) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.governance_schedule_league), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(league, { league = it }, label = { Text(stringResource(R.string.governance_league_id)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(opens, { opens = it }, label = { Text(stringResource(R.string.governance_registration_opens)) }, enabled = !busy, singleLine = true, supportingText = { Text(stringResource(R.string.governance_datetime_hint)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(closes, { closes = it }, label = { Text(stringResource(R.string.governance_registration_closes)) }, enabled = !busy, singleLine = true, supportingText = { Text(stringResource(R.string.governance_datetime_hint)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(starts, { starts = it }, label = { Text(stringResource(R.string.governance_match_starts)) }, enabled = !busy, singleLine = true, supportingText = { Text(stringResource(R.string.governance_datetime_hint)) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && season.isNotBlank() && league.isNotBlank() && a != null && b != null && c != null && a < b && b <= c, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = {
            request(JSONObject().put("type", "schedule_league").put("season", season).put("league", league).put("schedule", JSONObject().put("opens_ms", a).put("closes_ms", b).put("starts_ms", c)))
        }) { Text(stringResource(R.string.governance_schedule_league)) }
        val seasons = state.optJSONObject("seasons") ?: JSONObject()
        if (seasons.length() > 0) Text(stringResource(R.string.governance_existing_seasons), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        seasons.keys().asSequence().forEach { id -> Text(stringResource(R.string.governance_season_entry, seasons.getJSONObject(id).optString("title"), id), style = MaterialTheme.typography.bodySmall) }
        val leagues = state.optJSONObject("leagues") ?: JSONObject()
        leagues.keys().asSequence().forEach { id ->
            val item = leagues.getJSONObject(id)
            if (item.optString("phase") == "Playing" && item.optString("organizer") == identity) {
                val paused = item.optBoolean("manual_pause")
                Button(enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { request(JSONObject().put("type", "pause_league").put("league", id).put("paused", !paused)) }) {
                    Text(stringResource(if (paused) R.string.governance_resume_league else R.string.governance_pause_league, id))
                }
            }
        }
    }
    ManagerCard(stringResource(R.string.governance_resolve_dispute), helper = stringResource(R.string.governance_dispute_helper)) {
        OutlinedTextField(response, { response = it }, label = { Text(stringResource(R.string.governance_review_reason)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(players, { players = it }, label = { Text(stringResource(R.string.governance_player_scores)) }, enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(teams, { teams = it }, label = { Text(stringResource(R.string.governance_team_scores)) }, enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
        val disputes = state.optJSONObject("disputes") ?: JSONObject()
        if (disputes.length() == 0) Text(stringResource(R.string.governance_no_disputes))
        disputes.keys().asSequence().forEach { id ->
            val d = disputes.getJSONObject(id)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(DISPUTE_CARD_RADIUS), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(DISPUTE_CARD_PADDING), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.governance_dispute_entry, d.optString("league"), d.optString("member")), style = MaterialTheme.typography.titleSmall)
                    Text(d.optString("reason"), style = MaterialTheme.typography.bodySmall)
                    if (!d.isNull("resolution")) Text(d.getJSONObject("resolution").optString("response"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Button(enabled = !busy && response.isNotBlank() && scores(players) != null && scores(teams) != null, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = {
                        request(JSONObject().put("type", "resolve_dispute").put("id", id).put("response", response).put("players", scores(players)).put("teams", scores(teams)))
                    }) { Text(stringResource(R.string.governance_submit_review)) }
                }
            }
        }
    }
}

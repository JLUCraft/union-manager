package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

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
    fun date(value: String): Long? = runCatching { LocalDateTime.parse(value.trim()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    fun scores(value: String): JSONObject? = runCatching {
        val result = JSONObject()
        value.lines().filter { it.isNotBlank() }.forEach {
            val pair = it.trim().split(Regex("\\s+"))
            require(pair.size == 2 && !result.has(pair[0]))
            result.put(pair[0], pair[1].toLong())
        }
        result
    }.getOrNull()
    val a = date(opens); val b = date(closes); val c = date(starts)
    ManagerCard("赛季与赛程", helper = "时间使用本机时区，格式为 2026-10-01T19:00") {
        Text("创建赛季", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(season, { season = it }, label = { Text("赛季标识") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it }, label = { Text("赛季名称") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && season.isNotBlank() && title.isNotBlank(), onClick = { request(JSONObject().put("type", "create_season").put("id", season).put("title", title)) }) { Text("创建赛季") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("安排赛程", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(league, { league = it }, label = { Text("联赛标识") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(opens, { opens = it }, label = { Text("报名开始") }, enabled = !busy, singleLine = true, supportingText = { Text("格式：2026-10-01T19:00，本机时区") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(closes, { closes = it }, label = { Text("报名截止") }, enabled = !busy, singleLine = true, supportingText = { Text("格式：2026-10-01T19:00，本机时区") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(starts, { starts = it }, label = { Text("开赛时间") }, enabled = !busy, singleLine = true, supportingText = { Text("格式：2026-10-01T19:00，本机时区") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && season.isNotBlank() && league.isNotBlank() && a != null && b != null && c != null && a < b && b <= c, onClick = {
            request(JSONObject().put("type", "schedule_league").put("season", season).put("league", league).put("schedule", JSONObject().put("opens_ms", a).put("closes_ms", b).put("starts_ms", c)))
        }) { Text("安排赛程") }
        val seasons = state.optJSONObject("seasons") ?: JSONObject()
        if (seasons.length() > 0) Text("已有赛季", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        seasons.keys().asSequence().forEach { id -> Text("· ${seasons.getJSONObject(id).optString("title")}（$id）", style = MaterialTheme.typography.bodySmall) }
        val leagues = state.optJSONObject("leagues") ?: JSONObject()
        leagues.keys().asSequence().forEach { id ->
            val item = leagues.getJSONObject(id)
            if (item.optString("phase") == "Playing" && item.optString("organizer") == identity) {
                val paused = item.optBoolean("manual_pause")
                Button(enabled = !busy, onClick = { request(JSONObject().put("type", "pause_league").put("league", id).put("paused", !paused)) }) { Text("${if (paused) "恢复" else "暂停"} $id") }
            }
        }
    }
    ManagerCard("赛果复核", helper = "积分调整每行填写一个成员或队伍标识和增减分，用空格分隔；留空表示积分不变。") {
        OutlinedTextField(response, { response = it }, label = { Text("复核说明") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(players, { players = it }, label = { Text("玩家积分调整") }, enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(teams, { teams = it }, label = { Text("队伍积分调整") }, enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
        val disputes = state.optJSONObject("disputes") ?: JSONObject()
        if (disputes.length() == 0) Text("暂无赛果争议")
        disputes.keys().asSequence().forEach { id ->
            val d = disputes.getJSONObject(id)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${d.optString("league")} · ${d.optString("member")}", style = MaterialTheme.typography.titleSmall)
                    Text(d.optString("reason"), style = MaterialTheme.typography.bodySmall)
                    if (!d.isNull("resolution")) Text(d.getJSONObject("resolution").optString("response"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Button(enabled = !busy && response.isNotBlank() && scores(players) != null && scores(teams) != null, onClick = {
                        request(JSONObject().put("type", "resolve_dispute").put("id", id).put("response", response).put("players", scores(players)).put("teams", scores(teams)))
                    }) { Text("提交复核结果") }
                }
            }
        }
    }
}

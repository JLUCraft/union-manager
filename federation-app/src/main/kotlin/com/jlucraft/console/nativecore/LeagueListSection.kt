package com.jlucraft.console.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun LeagueListSection(state: JSONObject?, busy: Boolean, onStart: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("联赛列表", style = MaterialTheme.typography.titleMedium)
        if (state == null) {
            Text(
                "连接联盟后，这里会显示联赛、报名情况与积分榜。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val leagues = state.optJSONObject("leagues")
            if (leagues == null || leagues.length() == 0) {
                Text(
                    "联盟内还没有联赛，创建后将在此显示。",
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(id, style = MaterialTheme.typography.titleMedium)
                    val rules = item.optJSONObject("rules")
                    if (rules != null) {
                        val crossClub = if (rules.optBoolean("cross_club")) "允许跨校组队" else "仅限校内组队"
                        Text(
                            "每队 ${rules.optInt("seats")} 人 · $crossClub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PhaseBadge(phase)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("报名队伍", style = MaterialTheme.typography.titleSmall)
            val teams = item.optJSONObject("teams")
            if (teams == null || teams.length() == 0) {
                Text(
                    "暂无队伍报名",
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("锁定报名并随机抽签开赛")
                }
            }
            Scoreboard("队伍积分榜", item.optJSONObject("team_scores") ?: JSONObject())
            Scoreboard("玩家积分榜", item.optJSONObject("player_scores") ?: JSONObject())
        }
    }
}

@Composable
private fun TeamRow(team: String, roster: JSONObject) {
    val registered = roster.getBoolean("registered")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(team, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "报名 ${roster.getJSONArray("members").length()} 人",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (registered) "已提交" else "未提交",
            style = MaterialTheme.typography.labelMedium,
            color = if (registered) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhaseBadge(phase: String) {
    val (label, active) = when (phase) {
        "Registration" -> "报名中" to true
        "Ongoing", "Running" -> "进行中" to false
        "Finished" -> "已结束" to false
        else -> phase to false
    }
    Surface(
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Scoreboard(title: String, scores: JSONObject) {
    val entries = scores.keys().asSequence().toList().sortedByDescending { scores.getLong(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (entries.isEmpty()) {
            Text(
                "暂无成绩",
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
                    Text("${scores.getLong(name)} 分", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

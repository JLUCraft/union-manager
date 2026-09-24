package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import org.json.JSONArray
import java.util.UUID

@Composable
fun GovernanceCard(state: JSONObject?, identity: String, busy: Boolean, request: (JSONObject) -> Unit) {
    if (state == null) return
    var device by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    val council = state.optJSONObject("council")
    val schools = council?.optJSONObject("schools")
    val school = schools?.keys()?.asSequence()?.firstOrNull { id ->
        val devices = schools.getJSONArray(id)
        (0 until devices.length()).any { devices.getString(it) == identity }
    }
    val admins = state.optJSONArray("admins") ?: JSONArray()
    val admin = (0 until admins.length()).any { admins.getString(it) == identity }
    val now = maxOf(System.currentTimeMillis(), state.optLong("clock_ms"))
    ManagerCard("联盟治理", helper = if (council == null) "尚未设立社团议事会" else "普通决策需 ${council.optInt("quorum")} 个社团同意") {
        if (admin || school != null) {
            OutlinedTextField(value = device, onValueChange = { device = it }, label = { Text("管理设备身份") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(modifier = Modifier.weight(1f), enabled = !busy && device.isNotBlank(), onClick = { request(JSONObject().put("type", "grant_admin").put("device", device)) }) { Text("授予管理员权限") }
                Button(modifier = Modifier.weight(1f), enabled = !busy && device.isNotBlank(), onClick = { request(JSONObject().put("type", "revoke_admin").put("device", device)) }) { Text("撤销管理员权限") }
            }
        }
        val proposals = state.optJSONObject("proposals") ?: JSONObject()
        if (proposals.length() == 0) Text("暂无治理提案")
        val proposalIds = proposals.keys().asSequence().toList()
        proposalIds.forEachIndexed { index, id ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val proposal = proposals.getJSONObject(id)
            val action = proposal.getJSONObject("action")
            val approvals = proposal.getJSONObject("approvals")
            val names = mapOf("create_season" to "创建赛季", "schedule_league" to "安排赛程", "resolve_dispute" to "赛果复核", "set_consensus_members" to "变更共识节点", "grant_admin" to "授予管理员", "revoke_admin" to "撤销管理员", "rotate_member_device" to "更换成员设备", "enroll" to "登记成员", "create_league" to "创建联赛", "update_council" to "调整议事规则")
            val type = action.getString("type")
            val active = !proposal.optBoolean("executed") && !proposal.optBoolean("cancelled") && proposal.getLong("expires_ms") > now && proposal.optLong("epoch") == council?.optLong("epoch")
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(names[type] ?: "未知提案", style = MaterialTheme.typography.titleSmall)
                    val labels = mapOf("id" to "标识", "title" to "名称", "season" to "赛季", "response" to "复核说明", "device" to "设备", "member" to "成员", "club" to "社团", "league" to "联赛", "host" to "比赛服务", "scorer" to "计分服务")
                    labels.forEach { (key, label) -> if (action.has(key)) Text("$label：${action.optString(key)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    listOf("schedule", "players", "teams", "members").forEach { key ->
                        action.optJSONObject(key)?.let { value ->
                            val label = mapOf("schedule" to "赛程", "players" to "玩家积分调整", "teams" to "队伍积分调整", "members" to "共识节点")[key]
                            Text(label ?: key, style = MaterialTheme.typography.bodySmall)
                            value.keys().asSequence().forEach { item -> Text("$item：${value.opt(item)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    action.optJSONObject("rules")?.let { Text("每队 ${it.optInt("seats")} 人 · ${if (it.optBoolean("cross_club")) "允许跨校" else "同校组队"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val proposed = action.optJSONObject("council")
                    proposed?.let {
                        Text("普通门槛 ${it.optInt("quorum")} · 规则门槛 ${it.optInt("change_quorum")} · 冷静期 ${it.optLong("cooldown_ms") / 3600000} 小时", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val members = it.optJSONObject("schools") ?: JSONObject()
                        members.keys().asSequence().forEach { name ->
                            Text(name, style = MaterialTheme.typography.bodySmall)
                            val devices = members.getJSONArray(name)
                            (0 until devices.length()).forEach { index -> Text(devices.getString(index), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Text("已同意：${approvals.keys().asSequence().joinToString("、").ifEmpty { "无" }}", style = MaterialTheme.typography.bodySmall)
                    val statusText = when { proposal.optBoolean("executed") -> "已执行"; proposal.optBoolean("cancelled") -> "已取消"; !active -> "已过期或规则已变更"; else -> "等待处理" }
                    Text(statusText, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (active && type in names) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(modifier = Modifier.weight(1f), enabled = !busy && school != null && !approvals.has(school), onClick = { request(JSONObject().put("type", "approve_proposal").put("id", id)) }) { Text("同意提案") }
                            val change = type == "update_council"
                            val enough = approvals.length() >= (if (change) council.optInt("change_quorum") else council.optInt("quorum"))
                            val cooled = !change || now >= proposal.getLong("created_ms") + (council?.optLong("cooldown_ms") ?: Long.MAX_VALUE)
                            Button(modifier = Modifier.weight(1f), enabled = !busy && enough && cooled, onClick = { request(JSONObject().put("type", "execute_proposal").put("id", id)) }) { Text("执行已通过提案") }
                        }
                        if (proposal.getString("proposer") == identity) Button(enabled = !busy, onClick = { request(JSONObject().put("type", "cancel_proposal").put("id", id)) }) { Text("撤回提案") }
                    }
                }
            }
        }
        if (admin || school != null) {
            Text("发起成员投票", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("成员投票标题") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = choices, onValueChange = { choices = it }, label = { Text("投票选项，每行一项") }, enabled = !busy, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && title.isNotBlank() && choices.lines().filter { it.isNotBlank() }.size >= 2, onClick = {
                request(JSONObject().put("type", "create_ballot").put("id", UUID.randomUUID().toString()).put("title", title).put("options", JSONArray(choices.lines().filter { it.isNotBlank() })).put("closes_ms", now + 7 * 86400000L))
            }) { Text("发起七天成员投票") }
        }
        if (admin) {
            val feedback = state.optJSONObject("feedback") ?: JSONObject()
            if (feedback.length() > 0) Text("成员申请与回复", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = response, onValueChange = { response = it }, label = { Text("回复内容") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            feedback.keys().asSequence().forEach { id ->
                val item = feedback.getJSONObject(id)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${item.optString("member")} · ${item.optString("category")}", style = MaterialTheme.typography.titleSmall)
                        Text(item.optString("text"), style = MaterialTheme.typography.bodySmall)
                        if (!item.isNull("response")) Text("回复：${item.optString("response")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(enabled = !busy && response.isNotBlank(), onClick = { request(JSONObject().put("type", "respond_feedback").put("id", id).put("text", response)) }) { Text("回复此申请") }
                    }
                }
            }
        }
    }
}

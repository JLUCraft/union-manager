package com.jlucraft.console.nativecore

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun CreateLeagueCard(
    league: String,
    seats: String,
    crossClub: Boolean,
    host: String,
    scorer: String,
    connected: Boolean,
    busy: Boolean,
    seatsValid: Boolean,
    onLeagueChange: (String) -> Unit,
    onSeatsChange: (String) -> Unit,
    onCrossClubChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onScorerChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(host.isBlank() || scorer.isBlank()) }
    ManagerCard(
        title = "创建联赛",
        helper = if (connected) null else "连接联盟后可创建联赛",
    ) {
        OutlinedTextField(
            value = league,
            onValueChange = onLeagueChange,
            label = { Text("联赛名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = seats,
            onValueChange = onSeatsChange,
            label = { Text("每队参赛人数") },
            singleLine = true,
            supportingText = if (seatsValid) null else ({ Text("请输入大于 0 的数字") }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = crossClub, onValueChange = onCrossClubChange, role = Role.Checkbox),
        ) {
            Checkbox(checked = crossClub, onCheckedChange = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("允许跨校组队", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "收起比赛服务配置" else "比赛服务配置（必填）")

        }
        if (showAdvanced) {
            Text(
                "比赛代理与计分服务身份由联盟组织者分配。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("比赛代理身份") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scorer,
                onValueChange = onScorerChange,
                label = { Text("计分服务身份") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = onCreate,
            enabled = !busy && connected && seatsValid && league.isNotBlank() && host.isNotBlank() && scorer.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("创建联赛")
        }
    }
}

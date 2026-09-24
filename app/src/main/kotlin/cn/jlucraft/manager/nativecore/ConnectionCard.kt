package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionCard(
    address: String,
    peer: String,
    identity: String,
    message: String,
    connected: Boolean,
    leagueCount: Int,
    busy: Boolean,
    onAddressChange: (String) -> Unit,
    onPeerChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    ManagerCard(
        title = "联盟连接",
        helper = if (connected) "联盟内共有 $leagueCount 个联赛" else "连接后可管理联盟成员与联赛",
        statusText = if (connected) "已连接" else "未连接",
        statusActive = connected,
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text("联盟连接地址") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = peer,
            onValueChange = onPeerChange,
            label = { Text("联盟身份（组织者提供）") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onConnect, enabled = !busy && address.isNotBlank() && peer.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("连接 / 刷新")
        }
        if (identity.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "本机管理设备身份",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        identity,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        if (message.isNotBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

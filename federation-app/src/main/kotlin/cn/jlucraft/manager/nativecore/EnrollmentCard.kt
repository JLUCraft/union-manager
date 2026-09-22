package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EnrollmentCard(
    memberId: String,
    memberClub: String,
    memberDevice: String,
    connected: Boolean,
    busy: Boolean,
    onMemberIdChange: (String) -> Unit,
    onMemberClubChange: (String) -> Unit,
    onMemberDeviceChange: (String) -> Unit,
    onEnroll: () -> Unit,
) {
    ManagerCard(
        title = "登记联盟成员",
        helper = if (connected) "为联盟登记成员及其设备身份" else "连接联盟后可登记成员",
    ) {
        OutlinedTextField(
            value = memberId,
            onValueChange = onMemberIdChange,
            label = { Text("成员编号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = memberClub,
            onValueChange = onMemberClubChange,
            label = { Text("所属学校社团") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = memberDevice,
            onValueChange = onMemberDeviceChange,
            label = { Text("成员设备身份") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onEnroll,
            enabled = !busy && connected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("登记成员")
        }
    }
}

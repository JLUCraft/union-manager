package com.jlucraft.manager.nativecore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import org.json.JSONObject

@Composable
internal fun ProfileOverviewCard(state: JSONObject, device: String) {
    val memberId = remember(state, device) { runCatching { identityMember(state, device) }.getOrNull()?.memberId }
    ManagerCard(title = stringResource(R.string.profile_overview_title)) {
        if (memberId == null) {
            Text(stringResource(R.string.profile_overview_unbound), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.identity_my_member), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(memberId, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
        }
    }
}

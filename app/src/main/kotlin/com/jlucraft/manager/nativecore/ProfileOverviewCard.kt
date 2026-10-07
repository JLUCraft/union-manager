package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import org.json.JSONObject

@Composable
internal fun ProfileOverviewCard(state: JSONObject?, device: String) {
    val memberId = remember(state, device) { state?.let { runCatching { identityMember(it, device) }.getOrNull()?.memberId } }
    ManagerCard(
        title = stringResource(R.string.profile_overview_title),
        statusText = stringResource(if (state != null) R.string.connection_connected else R.string.connection_disconnected),
        statusActive = state != null,
    ) {
        when {
            state == null -> Text(stringResource(R.string.profile_overview_offline), style = MaterialTheme.typography.titleMedium)
            memberId == null -> Text(stringResource(R.string.profile_overview_unbound), style = MaterialTheme.typography.titleMedium)
            else -> Text(memberId, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S)) {
            Text(stringResource(R.string.profile_overview_device), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortIdentifier(device), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

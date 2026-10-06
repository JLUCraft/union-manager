package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jlucraft.manager.R

private val STEP_MARK_SIZE = 24.dp

/** First-time path for a new president. Every mark comes from real state; it disappears once all steps are done. */
@Composable
internal fun SetupChecklist(identityReady: Boolean, configured: Boolean, connected: Boolean) {
    if (identityReady && configured && connected) return
    ManagerCard(title = stringResource(R.string.setup_title), helper = stringResource(R.string.setup_helper)) {
        SetupStep(1, stringResource(R.string.setup_step_identity), identityReady)
        SetupStep(2, stringResource(R.string.setup_step_config), configured)
        SetupStep(3, stringResource(R.string.setup_step_connect), connected)
    }
}

@Composable
private fun SetupStep(number: Int, label: String, done: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SPACING_M), verticalAlignment = Alignment.CenterVertically) {
        if (done) Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.setup_done), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(STEP_MARK_SIZE))
        else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(STEP_MARK_SIZE)) {
            Box(contentAlignment = Alignment.Center) { Text(number.toString(), style = MaterialTheme.typography.labelMedium) }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

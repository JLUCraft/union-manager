package com.jlucraft.manager.nativecore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Public material only; the vault retains ownership of the transient secret. */
internal fun devicePublicIdentity(secret: ByteArray): String = UnionNative.publicIdentity(secret)

@Composable
internal fun DeviceIdentityCard(publicIdentity: String) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()
    val parsed = remember(publicIdentity) { runCatching { JSONObject(publicIdentity) }.getOrNull() }
    val peer = parsed?.optString("peer").orEmpty()
    val publicKey = parsed?.optString("public_key").orEmpty()
    var working by remember(publicIdentity) { mutableStateOf(false) }
    var exported by remember(publicIdentity) { mutableStateOf(false) }
    var failed by remember(publicIdentity) { mutableStateOf(false) }
    val fileName = stringResource(R.string.device_identity_file_name)
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        activity?.documentPickerActive = false
        if (uri != null) scope.launch {
            working = true; failed = false; exported = false
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(publicIdentity.toByteArray()) }
                }
                exported = true
            } catch (_: Exception) { failed = true } finally { working = false }
        }
    }
    ManagerCard(
        title = stringResource(R.string.device_identity_title),
        helper = stringResource(R.string.device_identity_explanation),
    ) {
        if (publicIdentity.isBlank()) {
            Text(stringResource(R.string.device_identity_empty))
            return@ManagerCard
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.device_identity_export_failed), color = MaterialTheme.colorScheme.error)
        if (exported) Text(stringResource(R.string.device_identity_exported), color = MaterialTheme.colorScheme.primary)
        if (peer.isNotBlank()) {
            Text(stringResource(R.string.device_identity_peer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortIdentifier(peer), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        }
        if (publicKey.isNotBlank()) {
            Text(stringResource(R.string.device_identity_key), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortIdentifier(publicKey), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        }
        PublicJsonActions(publicIdentity, R.string.device_identity_title, enabled = !working)
        OutlinedButton(onClick = {
            activity?.documentPickerActive = true
            try { download.launch(fileName) } catch (_: Exception) { activity?.documentPickerActive = false; failed = true }
        }, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.device_identity_download))
        }
        IdentifierDetails(publicIdentity)
    }
}

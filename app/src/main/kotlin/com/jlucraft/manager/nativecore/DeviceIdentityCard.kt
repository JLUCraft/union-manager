package com.jlucraft.manager.nativecore

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject

/** Public material only; the vault retains ownership of the transient secret. */
internal fun devicePublicIdentity(secret: ByteArray): String = JSONObject()
    .put("peer", UnionNative.peerId(secret))
    .put("public_key", JSONArray(UnionNative.publicKey(secret)))
    .toString(2)

@Composable
internal fun DeviceIdentityCard(publicIdentity: String) {
    val context = LocalContext.current
    var copied by remember(publicIdentity) { mutableStateOf(false) }
    val peer = remember(publicIdentity) { runCatching { JSONObject(publicIdentity).getString("peer") }.getOrDefault("") }
    ManagerCard(
        title = stringResource(R.string.device_identity_title),
        helper = stringResource(R.string.device_identity_explanation),
    ) {
        if (publicIdentity.isBlank()) {
            Text(stringResource(R.string.device_identity_empty))
            return@ManagerCard
        }
        if (peer.isNotBlank()) {
            Text(stringResource(R.string.device_identity_peer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortIdentifier(peer), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { copyToClipboard(context, context.getString(R.string.device_identity_title), publicIdentity); copied = true },
                modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
            ) { Text(stringResource(if (copied) R.string.device_identity_copied else R.string.device_identity_copy)) }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, publicIdentity)
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.device_identity_share_title)))
                },
                modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
            ) { Text(stringResource(R.string.device_identity_share)) }
        }
        IdentifierDetails(publicIdentity)
    }
}

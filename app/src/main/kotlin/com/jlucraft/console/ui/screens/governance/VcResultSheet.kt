package com.jlucraft.console.ui.screens.governance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlucraft.console.data.model.VcVerificationResult
import com.jlucraft.console.data.model.VerifiableCredential
import com.jlucraft.console.ui.theme.StatusGreen

/**
 * Bottom sheet displaying a Verifiable Credential JSON with copy and share actions.
 *
 * After a successful VC issuance or VC verification, this sheet shows:
 *   - The VC JSON (pretty-printed) for copy/share
 *   - Verification result if available
 *   - Copy to clipboard action
 *   - Share via Android share sheet action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VcResultSheet(
    vcJson: String?,
    vc: VerifiableCredential? = null,
    verificationResult: VcVerificationResult? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "VC",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "可验证凭证 (VC)",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (vc != null) {
                        Text(
                            text = "签发者: ${vc.issuer}  |  主体: ${vc.credentialSubject.id.take(20)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── VC subject summary ──
            if (vc != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "主体信息",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        VcFieldRow("DID", vc.credentialSubject.id)
                        VcFieldRow("角色", vc.credentialSubject.role)
                        VcFieldRow("显示名称", vc.credentialSubject.displayName)
                        vc.credentialSubject.clubCode?.let { VcFieldRow("社团", it) }
                        VcFieldRow("签发日期", vc.issuanceDate)
                        vc.expirationDate?.let { VcFieldRow("过期日期", it) }
                        if (vc.credentialSubject.permissions.isNotEmpty()) {
                            VcFieldRow("权限", vc.credentialSubject.permissions.joinToString(", "))
                        }
                    }
                }
            }

            // ── Verification result ──
            if (verificationResult != null) {
                val validColor = if (verificationResult.valid) StatusGreen else MaterialTheme.colorScheme.error
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = validColor.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "验证结果",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (verificationResult.valid) "VC 验证通过" else "VC 验证失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = validColor
                        )
                        verificationResult.errors.forEach { error ->
                            Text(
                                text = "错误: $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        verificationResult.warnings.forEach { warning ->
                            Text(
                                text = "警告: $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ── VC JSON (copy/share target) ──
            if (vcJson != null && vcJson.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "VC JSON",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = vcJson,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ── Action buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (vcJson != null && vcJson.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { copyToClipboard(context, vcJson) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制")
                    }

                    OutlinedButton(
                        onClick = { shareVc(context, vcJson) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享")
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun VcFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.take(40),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("VC JSON", text))
    Toast.makeText(context, "VC JSON 已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

private fun shareVc(context: Context, text: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "application/json"
    }
    val shareIntent = Intent.createChooser(sendIntent, "分享 VC")
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
}

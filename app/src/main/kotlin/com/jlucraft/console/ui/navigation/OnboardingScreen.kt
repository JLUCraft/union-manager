package com.jlucraft.console.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.TeeCapability
import com.jlucraft.console.data.local.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Onboarding gate that blocks navigation until:
 *  1. TEE capability is detected
 *  2. Ed25519 key pair is generated (on IO dispatcher)
 *  3. Public key is confirmed/shown (QR placeholder)
 *  4. User acknowledges completion → marks onboarding_completed=true
 */
@Composable
fun OnboardingScreen(
    teeAuth: TeeAuthManager,
    settingsStore: SettingsStore,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var teeLabel by remember { mutableStateOf("检测中...") }
    var pubKey by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var generationDone by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrError by remember { mutableStateOf<String?>(null) }
    var acknowledged by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        teeLabel = when (val cap = teeAuth.capability) {
            is TeeCapability.StrongBoxAvailable -> "StrongBox (最高安全)"
            is TeeCapability.TeeOnlyAvailable -> "TEE (标准安全)"
            is TeeCapability.NoHardwareBackedKey -> "无硬件安全能力 (${cap.reason})"
        }

        val result = withContext(Dispatchers.IO) {
            teeAuth.tryGetPublicKey()
        }
        result.fold(
            onSuccess = { key ->
                pubKey = key
                generationDone = true
                teeLabel = when (val cap = teeAuth.capability) {
                    is TeeCapability.StrongBoxAvailable -> "StrongBox (最高安全)"
                    is TeeCapability.TeeOnlyAvailable -> "TEE (标准安全)"
                    is TeeCapability.NoHardwareBackedKey -> "无硬件安全能力 (${cap.reason})"
                }
            },
            onFailure = { e ->
                errorMsg = "密钥生成失败: ${e.message}"
                teeLabel = when (val cap = teeAuth.capability) {
                    is TeeCapability.StrongBoxAvailable -> "StrongBox (最高安全)"
                    is TeeCapability.TeeOnlyAvailable -> "TEE (标准安全)"
                    is TeeCapability.NoHardwareBackedKey -> "无硬件安全能力 (${cap.reason})"
                }
            }
        )
    }

    // Generate QR bitmap from public key on background dispatcher
    LaunchedEffect(pubKey) {
        if (pubKey.isNullOrEmpty()) {
            qrError = "公钥为空，无法生成 QR 码"
            qrBitmap = null
            return@LaunchedEffect
        }
        qrError = null
        qrBitmap = null
        val content = """{"pubkey":"$pubKey","type":"manager-onboarding"}"""
        val bitmap = withContext(Dispatchers.IO) {
            try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
                val bm = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
                for (x in 0 until 512) {
                    for (y in 0 until 512) {
                        bm.setPixel(
                            x, y,
                            if (bitMatrix[x, y]) android.graphics.Color.BLACK
                            else android.graphics.Color.WHITE
                        )
                    }
                }
                bm
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            qrBitmap = bitmap
        } else {
            qrError = "QR 码生成失败"
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Union Manager 初始化",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Step 1: TEE Detection ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (teeAuth.isTeeBacked)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. TEE 安全环境检测",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "状态: $teeLabel",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ 需要可用的硬件安全密钥后才能继续",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Step 2: Key Generation ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (generationDone)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Ed25519 密钥生成",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (generationDone && pubKey != null) {
                    Text(
                        text = "✅ 密钥已生成",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "设备公钥 (Base64):",
                        style = MaterialTheme.typography.labelMedium
                    )
                    // QR code from real public key
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // QR bitmap (180×180 dp area)
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    qrBitmap != null -> {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "设备公钥 QR 码",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    qrError != null -> {
                                        Text(
                                            text = qrError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    else -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = truncateKey(pubKey!!),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (errorMsg != null) {
                    Text(
                        text = "❌ $errorMsg",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (!generationDone && errorMsg == null) {
                    Text(
                        text = "⏳ 正在生成密钥...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Step 3: Acknowledge ──
        if (generationDone) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3. 确认完成",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = acknowledged,
                            onCheckedChange = { acknowledged = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "我已记录并确认设备公钥",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                // Persist onboarding flag first (suspend), then notify caller
                                settingsStore.setOnboardingCompleted(true)
                            }
                            // onComplete is a pure UI callback; call it directly after launch
                            onComplete()
                        },
                        enabled = acknowledged,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始使用")
                    }
                }
            }
        }
    }
}

/**
 * Truncate a Base64 public key for display.
 * Ed25519 SubjectPublicKeyInfo DER in Base64 is typically ~44 chars;
 * we show the first 16 + "..." + last 12 for readability.
 */
private fun truncateKey(key: String): String {
    if (key.length <= 36) return key
    return key.take(16) + "..." + key.takeLast(12)
}

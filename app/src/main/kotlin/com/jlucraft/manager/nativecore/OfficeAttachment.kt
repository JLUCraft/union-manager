package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.heightIn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun OfficeAttachment(state: JSONObject?, busy: Boolean, attachmentId: String, cid: String, onChange: (String) -> Unit, send: (JSONObject) -> Unit, editable: Boolean = true) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()
    val branch = "request-$attachmentId"
    val path = "document"
    var pendingUpload by remember { mutableStateOf(false) }
    var previousCommit by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var temporary by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) { onDispose { temporary?.delete() } }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        activity?.documentPickerActive = false
        if (uri != null) scope.launch {
            working = true; failed = false; exported = false
            try {
                val file = withContext(Dispatchers.IO) {
                    val target = File.createTempFile("office-upload-", ".bin", context.cacheDir)
                    try {
                        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            target.outputStream().use { output ->
                                copyOfficeFile(input, output)
                            }
                        }
                        target
                    } catch (error: Exception) { target.delete(); throw error }
                }
                temporary?.delete(); temporary = file
                previousCommit = state?.optJSONObject("files")?.optJSONObject("branches")?.optJSONObject(branch)?.optString("commit")
                pendingUpload = true
                send(JSONObject().put("type", "local.files.upload").put("local_path", file.absolutePath)
                    .put("branch", branch).put("path", path).put("message", "attachment"))
            } catch (_: Exception) { failed = true } finally { working = false }
        }
    }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        activity?.documentPickerActive = false
        if (uri != null && state != null) scope.launch {
            working = true; failed = false; exported = false
            try {
                withContext(Dispatchers.IO) {
                    val bytes = UnionNative.readOfficeFile(state.toString(), cid)
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
                }
                exported = true
            } catch (_: Exception) { failed = true } finally { working = false }
        }
    }
    val defaultName = stringResource(R.string.office_files_default_name)
    val enabled = state != null && !busy && !working
    LaunchedEffect(state, busy) {
        if (pendingUpload && !busy && state != null) {
            val files = state.optJSONObject("files")
            val commit = files?.optJSONObject("branches")?.optJSONObject(branch)?.optString("commit")
            val encoded = commit?.let { files.optJSONObject("blocks")?.optString(it) }
            val uploaded = runCatching {
                JSONObject(String(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP), Charsets.UTF_8))
                    .getJSONObject("files").getJSONObject(path).getString("/")
            }.getOrNull()
            if (uploaded != null && commit != previousCommit) onChange(uploaded) else failed = true
            pendingUpload = false
        }
    }
    Column {
        if (working || (pendingUpload && busy)) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.office_files_error), color = MaterialTheme.colorScheme.error)
        if (exported) Text(stringResource(R.string.office_files_exported), color = MaterialTheme.colorScheme.primary)
        if (editable) Button(onClick = {
            activity?.documentPickerActive = true
            try { upload.launch("*/*") } catch (_: Exception) { activity?.documentPickerActive = false; failed = true }
        }, enabled = enabled && attachmentId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(if (cid.isBlank()) R.string.office_attachment_add else R.string.office_attachment_replace))
        }
        if (cid.isNotBlank()) {
            TextButton(onClick = {
                activity?.documentPickerActive = true
                try { download.launch(defaultName) } catch (_: Exception) { activity?.documentPickerActive = false; failed = true }
            }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.office_attachment_download))
            }
        }
    }
}

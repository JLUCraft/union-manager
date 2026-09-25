package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.heightIn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cn.jlucraft.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun OfficeFilesCard(state: JSONObject?, busy: Boolean, send: (JSONObject) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var branch by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var cid by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var temporary by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) { onDispose { temporary?.delete() } }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            working = true; failed = false
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
                send(JSONObject().put("type", "local.files.upload").put("local_path", file.absolutePath)
                    .put("branch", branch).put("path", path).put("message", message))
            } catch (_: Exception) { failed = true } finally { working = false }
        }
    }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && state != null) scope.launch {
            working = true; failed = false
            try {
                withContext(Dispatchers.IO) {
                    val bytes = UnionNative.readOfficeFile(state.toString(), cid)
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
                }
            } catch (_: Exception) { failed = true } finally { working = false }
        }
    }
    val defaultName = stringResource(R.string.office_files_default_name)
    val enabled = state != null && !busy && !working
    ManagerCard(title = stringResource(R.string.office_files_title), helper = stringResource(R.string.office_files_help)) {
        if (state == null) Text(stringResource(R.string.office_connect))
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.office_files_error), color = MaterialTheme.colorScheme.error)
        val heads = state?.optJSONObject("files")?.optJSONObject("branches")
        if (state != null && (heads == null || heads.length() == 0)) Text(stringResource(R.string.office_files_empty))
        heads?.keys()?.asSequence()?.toList()?.sorted()?.forEach { name ->
            key(name) {
                val head = heads.getJSONObject(name)
                Text(name, style = MaterialTheme.typography.titleSmall)
                IdentifierDetails(head.getString("commit"))
                IdentifierDetails(head.getString("author"))
                val entries = runCatching {
                    val encoded = state!!.getJSONObject("files").getJSONObject("blocks").getString(head.getString("commit"))
                    JSONObject(String(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP), Charsets.UTF_8)).getJSONObject("files")
                }.getOrNull()
                entries?.keys()?.asSequence()?.toList()?.sorted()?.forEach { filePath ->
                    key(name, filePath) {
                        val version = entries.getJSONObject(filePath).getString("/")
                        Text(filePath)
                        IdentifierDetails(version)
                        Button(onClick = { branch = name; path = filePath; cid = version }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
                            Text(stringResource(R.string.office_files_select))
                        }
                    }
                }
            }
        }
        OutlinedTextField(branch, { branch = it }, label = { Text(stringResource(R.string.office_files_branch)) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(path, { path = it }, label = { Text(stringResource(R.string.office_files_path)) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(message, { message = it }, label = { Text(stringResource(R.string.office_files_message)) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        Button(onClick = { upload.launch("*/*") }, enabled = enabled && branch.isNotBlank() && path.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.office_files_upload)) }
        OutlinedTextField(cid, { cid = it.trim() }, label = { Text(stringResource(R.string.office_files_cid)) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        Button(onClick = { download.launch(path.substringAfterLast('/').ifBlank { defaultName }) }, enabled = enabled && cid.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.office_files_export)) }
    }
}

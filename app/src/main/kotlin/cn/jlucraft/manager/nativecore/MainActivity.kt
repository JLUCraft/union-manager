package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.heightIn

import cn.jlucraft.manager.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import android.view.WindowManager

class MainActivity : ComponentActivity() {
    private lateinit var vault: BiometricVault
    private var unlocked by mutableStateOf(false)
    private var lockEpoch by mutableStateOf(0L)
    override fun onStop() {
        unlocked = false
        lockEpoch++
        vault.cancel()
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        vault = BiometricVault(this)
        val preferences = getPreferences(MODE_PRIVATE)
        setContent {
            JluManagerTheme {
                val scope = rememberCoroutineScope()
                var address by remember { mutableStateOf(preferences.getString("address", "") ?: "") }
                var peer by remember { mutableStateOf(preferences.getString("peer", "") ?: "") }
                var genesisHash by remember { mutableStateOf(preferences.getString("genesis_hash", "") ?: "") }
                var identityConfiguration by remember { mutableStateOf(preferences.getString("identity_configuration", "") ?: "") }
                val identityDirectory = java.io.File(noBackupFilesDir, "identity")
                var identity by remember { mutableStateOf("") }
                var state by remember { mutableStateOf<JSONObject?>(null) }
                var office by remember { mutableStateOf(preferences.getBoolean("office", false)) }
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                var league by remember { mutableStateOf("") }
                var seats by remember { mutableStateOf("4") }
                var crossClub by remember { mutableStateOf(false) }
                var host by remember { mutableStateOf("") }
                var scorer by remember { mutableStateOf("") }
                LaunchedEffect(lockEpoch) { state = null; busy = false; identity = "" }
                fun unlock() {
                    if (busy) return
                    busy = true
                    vault.authenticate(getString(R.string.unlock_management), { secret ->
                        identity = UnionNative.peerId(secret)
                        unlocked = true
                        busy = false
                        message = ""
                    }, { message = it; busy = false })
                }
                fun request(action: JSONObject? = null) {
                    if (busy || !unlocked) return
                    if (identityConfiguration.isBlank()) { message = getString(R.string.identity_configuration_required); return }
                    val requestEpoch = lockEpoch
                    val requestAddress = address
                    val requestPeer = peer
                    val requestGenesis = genesisHash
                    val requestOffice = office
                    val input = JSONObject().put("address", requestAddress).put("peer", requestPeer).put("genesis_hash", requestGenesis)
                    if (identityConfiguration.isNotBlank()) input.put("identity", identityConfig(identityConfiguration, identityDirectory))
                    state?.let { input.put("revision", it.getLong("revision")) }
                    action?.let { input.put("action", it) }
                    busy = true
                    vault.authenticate(if (action == null) getString(R.string.sync_management) else getString(R.string.confirm_management), { secret ->
                        val material = secret.copyOf()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    try { JSONObject(if (requestOffice) UnionNative.executeOffice(material, input.toString()) else UnionNative.execute(material, input.toString())) }
                                    finally { material.fill(0) }
                                }
                                if (lockEpoch == requestEpoch && unlocked) {
                                    if (!result.isNull("error")) throw NativeFailure(result.optString("error_code"), result.getString("error"))
                                    val snapshot = result.getJSONObject("state")
                                    result.optJSONObject("identity_view")?.let { projection ->
                                        snapshot.put("members", projection.getJSONObject("members")).put("admins", projection.getJSONArray("admins"))
                                    }
                                    state = snapshot
                                    preferences.edit().putString("address", requestAddress).putString("peer", requestPeer).putString("genesis_hash", requestGenesis).putBoolean("office", requestOffice).apply()
                                    message = if (action == null) getString(R.string.management_synced) else getString(R.string.management_done)
                                }
                            } catch (error: Exception) {
                                if (lockEpoch == requestEpoch) message = getString(if (error is NativeFailure && error.code == "authority_unavailable") R.string.identity_unavailable else R.string.management_failed)
                            } finally {
                                material.fill(0)
                                if (lockEpoch == requestEpoch) busy = false
                            }
                        }
                    }, { if (lockEpoch == requestEpoch) { message = it; busy = false } })
                }
                fun executeOps(input: JSONObject, reply: (JSONObject) -> Unit, identityOperation: Boolean = false) {
                    if (busy || !unlocked) return
                    busy = true
                    val epoch = lockEpoch
                    vault.authenticate(getString(if (identityOperation) R.string.identity_authenticate else R.string.ops_authenticate), { secret ->
                        val material = secret.copyOf()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    try { JSONObject(if (identityOperation && input.optString("operation") == "sign_offline") UnionNative.signIdentityRecord(material, input.getJSONObject("record").toString()) else if (identityOperation) UnionNative.executeIdentity(material, input.toString()) else UnionNative.executeOps(material, input.toString())) }
                                    finally { material.fill(0) }
                                }
                                if (lockEpoch == epoch && unlocked) reply(response)
                            } catch (_: Exception) {
                                if (lockEpoch == epoch && unlocked) reply(JSONObject().put("error", true).put("truncated", true))
                            } finally {
                                material.fill(0)
                                if (lockEpoch == epoch) busy = false
                            }
                        }
                    }, {
                        if (lockEpoch == epoch) {
                            busy = false
                            reply(JSONObject().put("error", true))
                        }
                    })
                }
                val seatsValid = (seats.toIntOrNull() ?: 0) > 0
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Column {
                            Text(stringResource(R.string.management_title), style = MaterialTheme.typography.headlineSmall)
                            Text(
                                stringResource(R.string.management_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!unlocked) {
                            Text(stringResource(R.string.management_locked))
                            if (message.isNotBlank()) Text(message)
                            Button(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { unlock() }, enabled = !busy) { Text(stringResource(R.string.management_unlock)) }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilterChip(selected = !office, enabled = !busy, onClick = { office = false; state = null; message = "" }, label = { Text(stringResource(R.string.mode_league)) })
                                FilterChip(selected = office, enabled = !busy, onClick = { office = true; state = null; message = "" }, label = { Text(stringResource(R.string.mode_office)) })
                            }
                            IdentityCard(identityConfiguration, identity, busy, identityDirectory, { value ->
                                identityConfiguration = value
                                preferences.edit().putString("identity_configuration", value).apply()
                                state = null
                            }) { input, reply -> executeOps(input, reply, true) }
                            GameOpsCard { input, reply -> executeOps(input, reply) }
                            ConnectionCard(
                                address = address,
                                peer = peer,
                                genesisHash = genesisHash,
                                identity = identity,
                                message = message,
                                connected = state != null,
                                busy = busy,
                                onAddressChange = { address = it; state = null },
                                onPeerChange = { peer = it; state = null },
                                onGenesisChange = { genesisHash = it.trim(); state = null },
                                onConnect = { request() },
                            )
                            if (!office) SeasonCard(state, identity, busy) { request(it) }
                            if (office) {
                                OfficeFilesCard(state, busy) { request(it) }
                                OfficeCard(state, busy) { request(it) }
                            } else {
                                CreateLeagueCard(
                                    league = league,
                                    seats = seats,
                                    crossClub = crossClub,
                                    host = host,
                                    scorer = scorer,
                                    connected = state != null,
                                    busy = busy,
                                    seatsValid = seatsValid,
                                    onLeagueChange = { league = it },
                                    onSeatsChange = { seats = it },
                                    onCrossClubChange = { crossClub = it },
                                    onHostChange = { host = it },
                                    onScorerChange = { scorer = it },
                                    onCreate = {
                                        request(JSONObject().put("type", "create_league").put("league", league).put("host", host).put("scorer", scorer)
                                            .put("rules", JSONObject().put("cross_club", crossClub).put("seats", seats.toInt()).put("reconnect_min_ms", 15000).put("reconnect_max_ms", 90000)))
                                    },
                                )
                                LeagueListSection(
                                    state = state,
                                    busy = busy,
                                    onStart = { id ->
                                        val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
                                        request(JSONObject().put("type", "start").put("league", id).put("seed", JSONArray(seed.map { it.toInt() and 255 })))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

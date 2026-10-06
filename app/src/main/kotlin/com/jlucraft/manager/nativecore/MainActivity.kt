package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import com.jlucraft.manager.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    internal var documentPickerActive = false
    private lateinit var vault: BiometricVault
    private var unlocked by mutableStateOf(false)
    private var lockEpoch by mutableStateOf(0L)
    private var focusedNotice by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("office_notice")) {
            unlocked = false
            lockEpoch++
            vault.cancel()
        }
        focusedNotice = intent.getStringExtra("office_notice")
    }
    override fun onStop() {
        // Android's document picker temporarily covers this activity. Each write still
        // obtains a fresh biometric signature after the picker returns.
        if (!documentPickerActive) {
            unlocked = false
            lockEpoch++
            vault.cancel()
        }
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focusedNotice = intent.getStringExtra("office_notice")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        vault = BiometricVault(this)
        val preferences = getPreferences(MODE_PRIVATE)
        setContent {
            JluManagerTheme {
                val scope = rememberCoroutineScope()
                val snackbar = remember { SnackbarHostState() }
                var address by remember { mutableStateOf(preferences.getString("address", "") ?: "") }
                var peer by remember { mutableStateOf(preferences.getString("peer", "") ?: "") }
                var genesisHash by remember { mutableStateOf(preferences.getString("genesis_hash", "") ?: "") }
                var identityConfiguration by remember { mutableStateOf(preferences.getString("identity_configuration", "") ?: "") }
                val identityDirectory = java.io.File(noBackupFilesDir, "identity")
                var identity by remember { mutableStateOf("") }
                var publicIdentity by remember { mutableStateOf("") }
                var genesisDraft by rememberSaveable { mutableStateOf("") }
                val identityProposal = rememberSaveable { mutableStateOf("") }
                var firstRun by remember { mutableStateOf(!vault.hasIdentity()) }
                var state by remember { mutableStateOf<JSONObject?>(null) }
                var office by remember { mutableStateOf(intent.hasExtra("office_notice") || preferences.getBoolean("office", true)) }
                var busy by remember { mutableStateOf(false) }
                var connectAfterLink by remember { mutableStateOf(false) }
                var pushReady by remember { mutableStateOf<Boolean?>(null) }
                var message by remember { mutableStateOf("") }
                var league by remember { mutableStateOf("") }
                var seats by remember { mutableStateOf("4") }
                var crossClub by remember { mutableStateOf(false) }
                var host by remember { mutableStateOf("") }
                var scorer by remember { mutableStateOf("") }
                var officeDestination by remember { mutableStateOf(OfficeDestination.Requests) }
                val topBackStack = rememberNavBackStack(if (office) OfficeRoot else LeagueHome)
                fun savedTarget(prefix: String, fallback: Boolean) = ConnectionTarget(
                    preferences.getString("${prefix}_address", if (fallback) address else "") ?: "",
                    preferences.getString("${prefix}_peer", if (fallback) peer else "") ?: "",
                    preferences.getString("${prefix}_genesis", if (fallback) genesisHash else "") ?: "",
                )
                var leagueTarget by remember { mutableStateOf(savedTarget("league", !office)) }
                var officeTarget by remember { mutableStateOf(savedTarget("office", office)) }
                fun selectMode(toOffice: Boolean) {
                    if (office == toOffice || busy) return
                    val current = ConnectionTarget(address, peer, genesisHash)
                    if (office) officeTarget = current else leagueTarget = current
                    val next = if (toOffice) officeTarget else leagueTarget
                    address = next.address
                    peer = next.peer
                    genesisHash = next.genesisHash
                    office = toOffice; state = null; message = ""; pushReady = null
                    topBackStack.clear()
                    topBackStack.add(if (toOffice) OfficeRoot else LeagueHome)
                }
                LaunchedEffect(lockEpoch) { state = null; busy = false; identity = ""; publicIdentity = "" }
                LaunchedEffect(focusedNotice) {
                    if (focusedNotice != null) {
                        selectMode(true)
                        officeDestination = OfficeDestination.Workflows
                        state = null
                    }
                }
                fun unlock() {
                    if (busy) return
                    busy = true
                    vault.authenticate(getString(R.string.unlock_management), { secret ->
                        identity = UnionNative.peerId(secret)
                        publicIdentity = devicePublicIdentity(secret)
                        firstRun = false
                        unlocked = true
                        busy = false
                        message = ""
                    }, { message = it; busy = false })
                }
                fun request(action: JSONObject? = null) {
                    if (busy || !unlocked) return
                    if (identityConfiguration.isBlank()) { message = getString(R.string.identity_configuration_required); return }
                    val requestEpoch = lockEpoch
                    var requestAddress = address
                    var requestPeer = peer
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
                                    try {
                                        val replicas = if (preferences.getString("office_nodes_genesis", "") == requestGenesis) preferences.getString("office_nodes", null)?.let { JSONObject(it) } else null
                                        val response = if (requestOffice) OfficeConnection.execute(material, input, replicas) else JSONObject(UnionNative.execute(material, input.toString()))
                                        if (requestOffice && response.isNull("error")) {
                                            requestAddress = response.getString("office_address")
                                            requestPeer = response.getString("office_peer")
                                        }
                                        if (requestOffice && response.isNull("error")) {
                                            try { response.put("push_ready", OfficePush.sync(this@MainActivity, material, requestAddress, requestPeer, requestGenesis)) }
                                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                            catch (_: Exception) { response.put("push_ready", false) }
                                        }
                                        response
                                    }
                                    finally { material.fill(0) }
                                }
                                if (lockEpoch == requestEpoch && unlocked) {
                                    if (!result.isNull("error")) throw NativeFailure(result.optString("error_code"), result.getString("error"))
                                    val snapshot = result.getJSONObject("state")
                                    result.optJSONObject("identity_view")?.let { projection ->
                                        snapshot.put("members", projection.getJSONObject("members")).put("admins", projection.getJSONArray("admins"))
                                    }
                                    state = snapshot
                                    if (requestOffice) {
                                        pushReady = result.optBoolean("push_ready")
                                        address = requestAddress; peer = requestPeer
                                        snapshot.optJSONObject("consensus_members")?.let { preferences.edit().putString("office_nodes_genesis",requestGenesis).putString("office_nodes",it.toString()).apply() }
                                    }
                                    if (requestOffice && !preferences.getBoolean("notification_permission_requested", false)) {
                                        preferences.edit().putBoolean("notification_permission_requested", true).apply()
                                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                                    }
                                    val targetPrefix = if (requestOffice) "office" else "league"
                                    preferences.edit().putString("${targetPrefix}_address", requestAddress).putString("${targetPrefix}_peer", requestPeer).putString("${targetPrefix}_genesis", requestGenesis).apply()
                                    preferences.edit().putString("address", requestAddress).putString("peer", requestPeer).putString("genesis_hash", requestGenesis).putBoolean("office", requestOffice).apply()
                                    message = if (action == null) getString(R.string.management_synced) else getString(R.string.management_done)
                                }
                            } catch (error: Exception) {
                                if (lockEpoch == requestEpoch) message = getString(failureMessageRes(error))
                            } finally {
                                material.fill(0)
                                if (lockEpoch == requestEpoch) busy = false
                            }
                        }
                    }, { if (lockEpoch == requestEpoch) { message = it; busy = false } })
                }
                LaunchedEffect(connectAfterLink) {
                    if (connectAfterLink) {
                        connectAfterLink = false
                        request()
                    }
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
                                    try { JSONObject(if (identityOperation && input.optString("operation") == "sign_offline") UnionNative.signIdentityOffline(material, JSONObject().put("config", input.getJSONObject("config")).put("record", input.getJSONObject("record")).toString()) else if (identityOperation) UnionNative.executeIdentity(material, input.toString()) else UnionNative.executeOps(material, input.toString())) }
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
                val connectionContent: @Composable () -> Unit = {
                    SetupChecklist(identityReady = publicIdentity.isNotBlank(), configured = identityConfiguration.isNotBlank(), connected = state != null)
                    DeviceIdentityCard(publicIdentity)
                    IdentityCard(identityConfiguration, identity, busy, identityDirectory, { value ->
                        identityConfiguration = value
                        preferences.edit().putString("identity_configuration", value).apply()
                        state = null
                        message = getString(R.string.identity_saved)
                    }, proposalState = identityProposal) { input, reply -> executeOps(input, reply, true) }
                    ConnectionCard(
                        address = address,
                        peer = peer,
                        genesisHash = genesisHash,
                        identity = "",
                        message = "",
                        connected = state != null,
                        busy = busy,
                        onAddressChange = { address = it; state = null },
                        onPeerChange = { peer = it; state = null },
                        onGenesisChange = { genesisHash = it.trim(); state = null },
                        onConnect = { request() },
                        onLink = { target ->
                            // The shared federation link addresses the launcher's league service.
                            selectMode(false)
                            address = target.address; peer = target.peer; genesisHash = target.genesisHash
                            state = null
                            connectAfterLink = true
                        },
                    )
                    AuditAnchorCard(state, identity, busy) { request(it) }
                    BusinessAdminCard(state, identity, busy) { request(it) }
                    GenesisBuilderCard(genesisDraft) { genesisDraft = it }
                }
                val seatsValid = (seats.toIntOrNull() ?: 0) > 0
                Surface(Modifier.fillMaxSize()) {
                    if (!unlocked) {
                        Box(Modifier.safeDrawingPadding().fillMaxSize()) {
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                            Column(
                                Modifier.align(Alignment.Center).widthIn(max = LOCKED_CONTENT_MAX_WIDTH).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = SPACING_XL),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(SPACING_L),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Text(
                                        stringResource(R.string.management_brand_mark),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(SPACING_XL),
                                    )
                                }
                                Text(stringResource(R.string.management_title), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    stringResource(R.string.management_locked),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                if (firstRun) Text(
                                    stringResource(R.string.management_first_run),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                if (message.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(DETAIL_SURFACE_CORNER),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ) {
                                        Text(
                                            message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = SPACING_L, vertical = SPACING_M),
                                        )
                                    }
                                }
                                Button(
                                    modifier = Modifier.fillMaxWidth().padding(top = SPACING_S).heightIn(min = MIN_TOUCH_TARGET),
                                    onClick = { unlock() },
                                    enabled = !busy,
                                ) { Text(stringResource(R.string.management_unlock)) }
                            }
                        }
                    } else {
                        LaunchedEffect(message) {
                            if (message.isNotBlank()) {
                                val shown = message
                                message = ""
                                scope.launch { snackbar.showSnackbar(shown) }
                            }
                        }
                        Box(Modifier.fillMaxSize()) {
                        ManagementNavigation(
                            office = office,
                            destination = officeDestination,
                            busy = busy,
                            onLeague = {
                                selectMode(false)
                                officeDestination = OfficeDestination.Requests
                                topBackStack.clear(); topBackStack.add(LeagueHome)
                            },
                            onOffice = {
                                if (it != OfficeDestination.Settings) selectMode(true)
                                officeDestination = it
                                topBackStack.clear(); topBackStack.add(OfficeRoot)
                            },
                        ) {
                            NavDisplay(
                                backStack = topBackStack,
                                modifier = Modifier.fillMaxSize(),
                                onBack = { if (topBackStack.size > 1) topBackStack.removeAt(topBackStack.lastIndex) },
                                entryProvider = entryProvider {
                                    entry<LeagueHome> {
                                        Column(
                                            Modifier.safeDrawingPadding().fillMaxSize().padding(horizontal = SPACING_L, vertical = SPACING_M).verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(SPACING_L),
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
                                            GameOpsCard { input, reply -> executeOps(input, reply) }
                                            SeasonCard(state, identity, busy) { request(it) }
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
                                    entry<OfficeRoot> {
                                        Column(Modifier.safeDrawingPadding().fillMaxSize()) {
                                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                                            OfficeHomeNav(
                                                state = state,
                                                busy = busy,
                                                identity = identity,
                                                focusedNotice = focusedNotice,
                                                pushReady = pushReady,
                                                openLeague = { resource, selectedLeague ->
                                                    selectMode(false)
                                                    address = resource.getString("address")
                                                    peer = resource.getString("peer")
                                                    genesisHash = resource.getString("genesis")
                                                    league = selectedLeague
                                                    request()
                                                },
                                                request = { request(it) },
                                                modifier = Modifier.weight(1f),
                                                connectionContent = connectionContent,
                                                destination = officeDestination,
                                                onOpenSettings = { officeDestination = OfficeDestination.Settings },
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
                        }
                    }
                }
            }
        }
    }
}

@Serializable internal data object LeagueHome : NavKey
@Serializable internal data object OfficeRoot : NavKey

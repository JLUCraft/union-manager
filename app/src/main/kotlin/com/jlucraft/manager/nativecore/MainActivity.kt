package com.jlucraft.manager.nativecore

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.edit
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import android.view.WindowManager

class MainActivity : ComponentActivity() {
    internal var documentPickerActive = false
    private lateinit var vault: BiometricVault
    private var unlocked by mutableStateOf(false)
    private var lockEpoch by mutableStateOf(0L)
    private var restoreEpoch by mutableStateOf(0L)
    private var focusedNotice by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("application_notice")) {
            // A notice re-enters through the same remembered-session gate as a cold launch.
            unlocked = false
            lockEpoch++
            vault.cancel()
            restoreEpoch++
        }
        focusedNotice = intent.getStringExtra("application_notice")
    }

    override fun onStart() {
        super.onStart()
        // Cold launch and returning from the background both restore the same sealed identity.
        restoreEpoch++
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
        focusedNotice = intent.getStringExtra("application_notice")
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
                // Public record drafts belong to the activity, not to a page or the lock gate: leaving
                // the issuance/record page or locking the app must never discard a signed record.
                val identityProposal = rememberSaveable { mutableStateOf("") }
                var identityProposalOwner by rememberSaveable { mutableStateOf("") }
                val businessDraftState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                var firstRun by remember { mutableStateOf(!vault.hasIdentity()) }
                var startupEpoch by remember { mutableStateOf(0L) }
                var connectionEpoch by remember { mutableStateOf(0L) }
                var startupSession by remember { mutableStateOf(StartupSession()) }
                var identityState by remember { mutableStateOf(IdentityState()) }
                var identityFacts by remember { mutableStateOf<JSONObject?>(null) }
                var state by remember { mutableStateOf<JSONObject?>(null) }
                var busy by remember { mutableStateOf(false) }
                var operationToken by remember { mutableStateOf(0L) }
                val autoCheck = remember { ConnectionAutoCheck() }
                var pushReady by remember { mutableStateOf<Boolean?>(null) }
                var message by remember { mutableStateOf("") }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var vaultUnavailable by remember { mutableStateOf(false) }
                var restoringSession by remember { mutableStateOf(false) }
                var league by rememberSaveable { mutableStateOf("") }
                var seats by rememberSaveable { mutableStateOf("4") }
                var crossClub by rememberSaveable { mutableStateOf(false) }
                var host by rememberSaveable { mutableStateOf("") }
                var scorer by rememberSaveable { mutableStateOf("") }
                var leagueDraftOwner by rememberSaveable { mutableStateOf("") }
                val topBackStack = rememberNavBackStack(WorkspaceRoot)
                fun navigateRoot(destination: WorkspaceDestination) {
                    topBackStack.clear()
                    topBackStack.add(when (destination) {
                        WorkspaceDestination.Home -> WorkspaceRoot
                        WorkspaceDestination.Activities -> ActivitiesRoot
                        WorkspaceDestination.Tasks -> TasksRoot
                        WorkspaceDestination.Settings -> AccountRoot
                    })
                }
                fun beginOperation(): Long {
                    operationToken += 1
                    busy = true
                    return operationToken
                }
                // A stale operation may finish without clearing the busy state of a newer one.
                fun finishOperation(token: Long, epoch: Long) {
                    if (operationToken == token && lockEpoch == epoch && unlocked) busy = false
                }
                // A target or identity change discards the old business pages; a temporary device
                // lock does not, so an interrupted public draft can resume after re-verification.
                fun resetBusinessStack() {
                    if (topBackStack.size > 1) {
                        topBackStack.clear()
                        topBackStack.add(WorkspaceRoot)
                    }
                }
                fun invalidateConnection() {
                    connectionEpoch += 1
                    startupSession = startupSession.connectionInvalidated()
                }
                fun currentConnectionTarget(): ConnectionTarget = ConnectionTarget(address, peer, genesisHash)
                fun openAccount(destination: NavKey = AccountRoot) {
                    topBackStack.openAccount(destination)
                }
                LaunchedEffect(lockEpoch) {
                    state = null; busy = false; identity = ""; publicIdentity = ""; errorMessage = null
                    operationToken += 1
                    startupEpoch += 1
                    connectionEpoch += 1
                    startupSession = startupSession.locked()
                    identityState = IdentityState()
                    identityFacts = null
                }
                fun keepProposalFor(device: String, configuration: String) {
                    val owner = identityProposalScope(device, configuration)
                    if (identityProposalOwner == owner) return
                    identityProposalOwner = owner
                    identityProposal.value = ""
                }
                // A cold launch or a foreground return restores the same identity from the sealed
                // session, then the existing identity sync and saved-business check run on their own.
                // Nothing here creates a second identity or substitutes a cached authority result.
                LaunchedEffect(restoreEpoch) {
                    if (unlocked || !vault.hasRestorableSession()) return@LaunchedEffect
                    val epoch = lockEpoch
                    restoringSession = true
                    val secret = withContext(Dispatchers.IO) { vault.restoreSession() }
                    if (secret == null) {
                        restoringSession = false
                        if (lockEpoch == epoch && vault.hasRememberedSession()) {
                            message = getString(R.string.vault_session_unavailable)
                        }
                        return@LaunchedEffect
                    }
                    try {
                        if (lockEpoch != epoch) return@LaunchedEffect
                        identity = UnionNative.peerId(secret)
                        publicIdentity = devicePublicIdentity(secret)
                        keepProposalFor(identity, identityConfiguration)
                        firstRun = false
                        unlocked = true
                        busy = false
                        message = ""
                        errorMessage = null
                        vaultUnavailable = false
                        startupEpoch += 1
                        connectionEpoch += 1
                        startupSession = startupSession.unlocked()
                        identityState = IdentityState()
                        identityFacts = null
                    } finally {
                        secret.fill(0)
                        restoringSession = false
                    }
                }
                // League creation fields live on the activity so the lock gate keeps them. They
                // are cleared when the device, identity configuration or League target really
                // changes, never by a lock or an identity re-check of the same scope.
                fun keepLeagueDraftFor(scope: String) {
                    if (leagueDraftOwner == scope) return
                    leagueDraftOwner = scope
                    league = ""
                    seats = "4"
                    crossClub = false
                    host = ""
                    scorer = ""
                }
                fun obtainSecret(
                    title: String,
                    remembered: Boolean,
                    success: (ByteArray) -> Unit,
                    failure: (String) -> Unit,
                ) {
                    if (!remembered) {
                        vault.authenticate(title, success, failure)
                        return
                    }
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val secret = withContext(Dispatchers.IO) { vault.restoreSession() }
                        if (secret == null) {
                            failure(getString(R.string.vault_session_unavailable))
                            return@launch
                        }
                        try { success(secret) } finally { secret.fill(0) }
                    }
                }
                fun unlock() {
                    if (busy) return
                    // A configured installation must restore or recover the existing identity;
                    // unlock never silently creates a second one.
                    if (identityConfiguration.isNotBlank() && !vault.hasIdentity()) {
                        message = getString(R.string.vault_recovery_required)
                        return
                    }
                    operationToken += 1
                    busy = true
                    vault.authenticate(getString(R.string.unlock_management), { secret ->
                        identity = UnionNative.peerId(secret)
                        publicIdentity = devicePublicIdentity(secret)
                        keepProposalFor(identity, identityConfiguration)
                        firstRun = false
                        unlocked = true
                        busy = false
                        message = ""
                        vaultUnavailable = false
                        startupEpoch += 1
                        connectionEpoch += 1
                        startupSession = startupSession.unlocked()
                        identityState = IdentityState()
                        identityFacts = null
                    }, { message = it; busy = false }, { vaultUnavailable = true })
                }
                fun request(
                    action: JSONObject? = null,
                    onCommitted: ((JSONObject) -> Unit)? = null,
                    onFailed: ((String?) -> Unit)? = null,
                ) {
                    if (busy || !unlocked) { onFailed?.invoke(null); return }
                    if (identityConfiguration.isBlank()) {
                        val message = getString(R.string.identity_configuration_required)
                        errorMessage = message
                        onFailed?.invoke(message)
                        return
                    }
                    val requestGenesis = genesisHash
                    if (address.isBlank() || peer.isBlank() || requestGenesis.isBlank()) { onFailed?.invoke(null); return }
                    val requestEpoch = lockEpoch
                    val requestScope = ConnectionScope(
                        startup = StartupScope(startupEpoch, identityConfiguration, identity),
                        generation = connectionEpoch,
                        target = ConnectionTarget(address, peer, requestGenesis),
                    )
                    val connecting = action == null && startupSession.stage == StartupStage.Connection
                    var requestAddress = address
                    var requestPeer = peer
                    val input = JSONObject().put("address", requestAddress).put("peer", requestPeer).put("genesis_hash", requestGenesis)
                    if (identityConfiguration.isNotBlank()) input.put("identity", identityConfig(identityConfiguration, identityDirectory))
                    state?.let { input.put("revision", it.getLong("revision")) }
                    action?.let { input.put("action", it) }
                    errorMessage = null
                    if (action == null) autoCheck.markAttempted(startupEpoch)
                    val token = beginOperation()
                    // A reply is usable only while the selected business, its complete target, and
                    // the identity generation are all still the ones this request started with.
                    val current: () -> Boolean = {
                        lockEpoch == requestEpoch && unlocked &&
                            requestScope.matches(startupEpoch, identityConfiguration, identity, connectionEpoch, currentConnectionTarget())
                    }
                    obtainSecret(
                        if (action == null) getString(R.string.sync_management) else getString(R.string.confirm_management),
                        remembered = action == null,
                        success = { secret ->
                        val material = secret.copyOf()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        val replicas = if (preferences.getString("application_nodes_genesis", "") == requestGenesis) preferences.getString("application_nodes", null)?.let { JSONObject(it) } else null
                                        val response = ApplicationConnection.execute(material, input, replicas)
                                        if (response.isNull("error")) {
                                            requestAddress = response.getString("application_address")
                                            requestPeer = response.getString("application_peer")
                                            try { response.put("push_ready", ApplicationPush.sync(this@MainActivity, material, requestAddress, requestPeer, requestGenesis)) }
                                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                            catch (_: Exception) { response.put("push_ready", false) }
                                        }
                                        response
                                    } finally { material.fill(0) }
                                }
                                if (current()) {
                                    if (!result.isNull("error")) throw NativeFailure(result.optString("error_code"), result.getString("error"))
                                    val snapshot = result.getJSONObject("state")
                                    result.optJSONObject("identity_view")?.let { projection ->
                                        snapshot.put("members", projection.getJSONObject("members")).put("admins", projection.getJSONArray("admins"))
                                    }
                                    state = snapshot
                                    // The form that started this request learns about completion only here,
                                    // after the exact target and identity scope proved the snapshot.
                                    onCommitted?.invoke(snapshot)
                                    if (connecting && startupSession.stage == StartupStage.Connection) {
                                        startupSession = startupSession.connectionSucceeded()
                                    }
                                    pushReady = result.optBoolean("push_ready")
                                    snapshot.optJSONObject("consensus_members")?.let { preferences.edit { putString("application_nodes_genesis", requestGenesis); putString("application_nodes", it.toString()) } }
                                    if (!preferences.getBoolean("notification_permission_requested", false)) {
                                        preferences.edit { putBoolean("notification_permission_requested", true) }
                                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                                    }
                                    if (host.isBlank()) host = requestPeer
                                    if (scorer.isBlank()) scorer = requestPeer
                                    address = requestAddress
                                    peer = requestPeer
                                    preferences.edit {
                                        putString("address", requestAddress)
                                        putString("peer", requestPeer)
                                        putString("genesis_hash", requestGenesis)
                                    }
                                    when (action?.optString("type")) {
                                        "create_league" -> { league = ""; message = getString(R.string.league_created) }
                                        "local.events.create" -> message = getString(R.string.office_created)
                                        else -> message = if (action == null) getString(R.string.management_synced) else getString(R.string.management_done)
                                    }
                                }
                            } catch (error: Exception) {
                                if (current()) {
                                    val message = getString(failureMessageRes(error))
                                    errorMessage = message
                                    onFailed?.invoke(message)
                                    if (action == null && startupSession.stage == StartupStage.Business) {
                                        // Keep the connection generation: a failed retry waits for the user.
                                        startupSession = startupSession.connectionInvalidated()
                                    }
                                }
                            } finally {
                                material.fill(0)
                                finishOperation(token, requestEpoch)
                            }
                        }
                    },
                        failure = {
                            if (current()) {
                                errorMessage = it
                                onFailed?.invoke(it)
                            }
                            finishOperation(token, requestEpoch)
                        },
                    )
                }
                // Apply a complete connection target and start exactly one check against it. The
                // account editor only emits this on an explicit check, so partial typing never
                // replaces the committed target or invalidates the current connection.
                fun applyConnectionTarget(target: ConnectionTarget) {
                    genesisHash = target.genesisHash
                    address = target.address
                    peer = target.peer
                    // The old snapshot and its revision belong to the previous target, never to the
                    // check that is about to run against the new one.
                    state = null
                    invalidateConnection()
                    request()
                }
                fun fetchGenesis(input: JSONObject, reply: (JSONObject) -> Unit) {
                    if (busy || !unlocked) return
                    val epoch = lockEpoch
                    val token = beginOperation()
                    val operationScope = StartupScope(startupEpoch, identityConfiguration, identity)
                    val current: () -> Boolean = { lockEpoch == epoch && unlocked && operationScope.matches(startupEpoch, identityConfiguration, identity) }
                    obtainSecret(
                        getString(R.string.identity_authenticate),
                        remembered = true,
                        success = { secret ->
                        val material = secret.copyOf()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    try { JSONObject(UnionNative.fetchGenesis(material, input.toString())) }
                                    finally { material.fill(0) }
                                }
                                if (current()) reply(response)
                            } catch (_: Exception) {
                                if (current()) reply(JSONObject().put("error", true))
                            } finally {
                                material.fill(0)
                                finishOperation(token, epoch)
                            }
                        }
                    },
                        failure = {
                            if (current()) reply(JSONObject().put("error", true))
                            finishOperation(token, epoch)
                        },
                    )
                }
                fun discoverServers(reply: (JSONObject) -> Unit) {
                    if (busy || !unlocked) { reply(JSONObject().put("error", true)); return }
                    val epoch = lockEpoch
                    val token = beginOperation()
                    val operationScope = StartupScope(startupEpoch, identityConfiguration, identity)
                    val current: () -> Boolean = { lockEpoch == epoch && unlocked && operationScope.matches(startupEpoch, identityConfiguration, identity) }
                    obtainSecret(
                        getString(R.string.connection_discover),
                        remembered = true,
                        success = { secret ->
                        val material = secret.copyOf()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    try { JSONObject(UnionNative.discover(material, "{}")) }
                                    finally { material.fill(0) }
                                }
                                if (current()) reply(response)
                            } catch (_: Exception) {
                                if (current()) reply(JSONObject().put("error", true))
                            } finally {
                                material.fill(0)
                                finishOperation(token, epoch)
                            }
                        }
                    },
                        failure = {
                            if (current()) reply(JSONObject().put("error", true))
                            finishOperation(token, epoch)
                        },
                    )
                }
                fun executeOps(input: JSONObject, reply: (JSONObject) -> Unit, identityOperation: Boolean = false, operationScope: StartupScope? = null) {
                    if (busy || !unlocked) return
                    val epoch = lockEpoch
                    val token = beginOperation()
                    val current: () -> Boolean = {
                        lockEpoch == epoch && unlocked &&
                            (operationScope == null || operationScope.matches(startupEpoch, identityConfiguration, identity))
                    }
                    obtainSecret(
                        getString(if (identityOperation) R.string.identity_authenticate else R.string.ops_authenticate),
                        remembered = identityOperation && usesRememberedSession(input.optString("operation")),
                        success = { secret ->
                        val material = secret.copyOf()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    try {
                                        JSONObject(
                                            if (identityOperation && input.optString("operation") == "sign_offline") {
                                                UnionNative.signIdentityOffline(material, JSONObject().put("config", input.getJSONObject("config")).put("record", input.getJSONObject("record")).toString())
                                            } else if (identityOperation) UnionNative.executeIdentity(material, input.toString())
                                            else UnionNative.executeOps(material, input.toString()),
                                        )
                                    } finally { material.fill(0) }
                                }
                                if (current()) reply(response)
                            } catch (_: Exception) {
                                if (current()) reply(JSONObject().put("error", true).put("truncated", true))
                            } finally {
                                material.fill(0)
                                finishOperation(token, epoch)
                            }
                        }
                    },
                        failure = {
                            if (current()) reply(JSONObject().put("error", true))
                            finishOperation(token, epoch)
                        },
                    )
                }
                fun saveIdentityConfiguration(value: String) {
                    identityConfiguration = value
                    keepProposalFor(identity, value)
                    resetBusinessStack()
                    preferences.edit { putString("identity_configuration", value) }
                    state = null
                    pushReady = null
                    identityState = IdentityState()
                    identityFacts = null
                    startupEpoch += 1
                    connectionEpoch += 1
                    startupSession = startupSession.configurationChanged()
                    busy = false
                    message = getString(R.string.identity_saved)
                }
                fun applyIdentityState(operationScope: StartupScope, next: IdentityState, nextFacts: JSONObject?) {
                    if (!operationScope.matches(startupEpoch, identityConfiguration, identity)) return
                    val previousTarget = currentConnectionTarget()
                    // A known identity that changes or loses its proof discards the old pages. The first
                    // sync after a temporary lock has no previous member and keeps the restored draft.
                    val previousMember = identityState.member?.memberId
                    if (previousMember != null && (!next.isVerifiedIdentity() || next.member?.memberId != previousMember)) {
                        resetBusinessStack()
                    }
                    identityState = next
                    identityFacts = nextFacts
                    if (next.isVerifiedIdentity()) {
                        runCatching { JSONObject(identityConfiguration) }.getOrNull()?.let { config ->
                            if (address.isBlank()) address = config.optString("address")
                            if (peer.isBlank()) peer = config.optString("peer")
                        }
                    }
                    val previous = startupSession
                    startupSession = startupSession.identityResult(next)
                    val targetChanged = currentConnectionTarget() != previousTarget
                    if (startupSession.connectionVerified != previous.connectionVerified || targetChanged) {
                        // Identity facts or the target changed: the old business check no longer applies.
                        // A new identity generation also re-arms the automatic connection check.
                        startupEpoch += 1
                        connectionEpoch += 1
                        state = null
                        pushReady = null
                        if (targetChanged && startupSession.stage == StartupStage.Business) {
                            startupSession = startupSession.connectionInvalidated()
                        }
                    }
                }
                fun applyBusinessGeneses(pins: JSONObject) {
                    val pin = pins.optJSONObject("application")?.optString("genesis_hash").orEmpty()
                    if (GENESIS_HASH_PATTERN.matches(pin) && pin != genesisHash) {
                        genesisHash = pin
                        preferences.edit { putString("genesis_hash", pin) }
                        state = null
                        invalidateConnection()
                    }
                }
                val accountPage = topBackStack.lastOrNull()?.isAccountPage() == true
                // Shared submit channel for the forms whose pages may only close after the server
                // accepted their exact write; the activity owns the scope check.
                val formSender = FormSender { action, committed, failed ->
                    request(action, onCommitted = committed, onFailed = failed)
                }
                // Never show a cached business snapshot whose target has not passed the gate.
                LaunchedEffect(unlocked, startupSession.stage, startupSession.connectionVerified, state) {
                    if (unlocked && startupSession.stage == StartupStage.Business &&
                        (!startupSession.connectionVerified || state == null)
                    ) {
                        invalidateConnection()
                    }
                }
                // After identity passes and the app is idle, check a saved complete target once.
                // A failure leaves the connection step for an explicit retry; target edits only
                // invalidate the old reply and do not re-arm an automatic check. A new identity or
                // configuration generation is what allows the next automatic attempt.
                LaunchedEffect(
                    unlocked, busy, connectionEpoch, startupEpoch, startupSession.stage,
                    startupSession.identityVerified, identityConfiguration, identity,
                    address, peer, genesisHash,
                ) {
                    val complete = identityConfiguration.isNotBlank() && address.isNotBlank() &&
                        peer.isNotBlank() && GENESIS_HASH_PATTERN.matches(genesisHash)
                    val ready = unlocked && !busy && startupSession.stage == StartupStage.Connection &&
                        startupSession.identityVerified && complete
                    if (autoCheck.shouldCheck(startupEpoch, ready)) request()
                }
                // A push notice opens the workflow destination once the business gate is open.
                LaunchedEffect(focusedNotice, unlocked, startupSession.effectiveStage()) {
                    if (focusedNotice != null && unlocked && startupSession.effectiveStage() == StartupStage.Business) {
                        navigateRoot(WorkspaceDestination.Tasks)
                        topBackStack.add(WorkflowRoot)
                    }
                }
                // Public creation drafts follow the single application target. Navigating among
                // activities and settings preserves them; a device or target change discards them.
                val leagueDraftScope = businessDraftScope(identity, identityConfiguration, currentConnectionTarget())
                LaunchedEffect(leagueDraftScope, unlocked) {
                    if (unlocked && identity.isNotBlank()) keepLeagueDraftFor(leagueDraftScope)
                }
                val seatsValid = (seats.toIntOrNull() ?: 0) > 0
                Surface(Modifier.fillMaxSize()) {
                    if (!unlocked || startupSession.effectiveStage() == StartupStage.Locked) {
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
                                    stringResource(if (restoringSession) R.string.vault_restoring else R.string.management_locked),
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
                                    DetailSurface {
                                        Text(
                                            message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                Button(
                                    modifier = Modifier.fillMaxWidth().padding(top = SPACING_S).heightIn(min = MIN_TOUCH_TARGET),
                                    onClick = { unlock() },
                                    enabled = !busy && !restoringSession,
                                ) { Text(stringResource(R.string.management_unlock)) }
                                if (vaultUnavailable) TextButton(
                                    onClick = { startActivity(Intent(Settings.ACTION_BIOMETRIC_ENROLL)) },
                                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                                ) { Text(stringResource(R.string.vault_open_settings)) }
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
                            StartupGate(
                                session = startupSession,
                                identity = {
                                    val identityScope = StartupScope(startupEpoch, identityConfiguration, identity)
                                    StartupIdentityStage(
                                        configuration = identityConfiguration,
                                        device = identity,
                                        publicIdentity = publicIdentity,
                                        identityState = identityState,
                                        facts = identityFacts,
                                        busy = busy,
                                        directory = identityDirectory,
                                        onConfig = { value -> saveIdentityConfiguration(value) },
                                        fetch = { input, reply -> fetchGenesis(input, reply) },
                                        discover = { reply -> discoverServers(reply) },
                                        onBusinessGeneses = { pins -> applyBusinessGeneses(pins) },
                                        onFetchFailed = { errorMessage = getString(R.string.identity_fetch_failed) },
                                        execute = { input, reply -> executeOps(input, reply, true, identityScope) },
                                        onStateChange = { next, facts -> applyIdentityState(identityScope, next, facts) },
                                        proposal = identityProposal,
                                    )
                                },
                                connection = {
                                    var organizationIdentityOpen by rememberSaveable { mutableStateOf(false) }
                                    val canManageOrganizationIdentity = startupSession.identityVerified &&
                                        identityState.member?.chairOf?.isNotEmpty() == true
                                    LaunchedEffect(canManageOrganizationIdentity) {
                                        if (!canManageOrganizationIdentity) organizationIdentityOpen = false
                                    }
                                    if (organizationIdentityOpen && canManageOrganizationIdentity) {
                                        val identityScope = StartupScope(startupEpoch, identityConfiguration, identity)
                                        MainIdentityPage(
                                            configuration = identityConfiguration,
                                            device = identity,
                                            busy = busy,
                                            directory = identityDirectory,
                                            publicIdentity = publicIdentity,
                                            state = identityState,
                                            facts = identityFacts,
                                            identityScope = identityScope,
                                            onConfig = { value -> saveIdentityConfiguration(value) },
                                            fetch = { input, reply -> fetchGenesis(input, reply) },
                                            discover = { reply -> discoverServers(reply) },
                                            onBusinessGeneses = { pins -> applyBusinessGeneses(pins) },
                                            onFetchFailed = { errorMessage = getString(R.string.identity_fetch_failed) },
                                            executeOps = { input, reply, identityOperation, operationScope ->
                                                executeOps(input, reply, identityOperation, operationScope)
                                            },
                                            onIdentityState = { operationScope, next, facts ->
                                                applyIdentityState(operationScope, next, facts)
                                            },
                                            onBack = { organizationIdentityOpen = false },
                                            proposal = identityProposal,
                                        )
                                    } else StartupConnectionStage(
                                        identity = identityState.member,
                                        identityReady = startupSession.identityVerified,
                                        address = address,
                                        peer = peer,
                                        genesisHash = genesisHash,
                                        busy = busy,
                                        failure = errorMessage,
                                        onAddressChange = { address = it; state = null; invalidateConnection() },
                                        onPeerChange = { peer = it; state = null; invalidateConnection() },
                                        onGenesisChange = { value ->
                                            genesisHash = value.trim()
                                            state = null
                                            invalidateConnection()
                                        },
                                        onConnect = { request() },
                                        onLink = { target -> applyConnectionTarget(target) },
                                        discover = { reply -> discoverServers(reply) },
                                        onRecheckIdentity = {
                                            resetBusinessStack()
                                                                                            state = null
                                            pushReady = null
                                            identityState = IdentityState()
                                            identityFacts = null
                                            startupEpoch += 1
                                            connectionEpoch += 1
                                            startupSession = startupSession.configurationChanged()
                                        },
                                        onOpenIdentitySetup = if (canManageOrganizationIdentity) {
                                            { organizationIdentityOpen = true }
                                        } else null,
                                    )
                                },
                                business = {
                                    // Chair authority comes from the current identity facts and the
                                    // personnel slot from the snapshot's admin list; both are read
                                    // here, before any page provider, so role-specific entries never
                                    // come from a stale closure after a seat or snapshot change.
                                    // Public business drafts and the current step survive the lock gate.
                                    // Their key is the complete business target, so another server can
                                    // never restore them. The account root is its own provider: its
                                    // connection editor must keep its step while the target is typed.
                                    businessDraftState.SaveableStateProvider(
                                        if (accountPage) accountPageScope(identity, identityConfiguration)
                                        else businessDraftScope(identity, identityConfiguration, currentConnectionTarget()),
                                    ) {
                                    WorkspaceNavigation(
                                        destination = when (topBackStack.firstOrNull()) {
                                            ActivitiesRoot -> WorkspaceDestination.Activities
                                            TasksRoot -> WorkspaceDestination.Tasks
                                            AccountRoot -> WorkspaceDestination.Settings
                                            else -> WorkspaceDestination.Home
                                        },
                                        busy = busy,
                                        onNavigate = { navigateRoot(it) },
                                    ) {
                                        val detailBack: () -> Unit = {
                                            if (topBackStack.size > 1) topBackStack.removeAt(topBackStack.lastIndex)
                                            else topBackStack.leaveAccount()
                                        }
                                        val startLeague: (String) -> Unit = { leagueId ->
                                            val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
                                            request(JSONObject().put("type", "start").put("league", leagueId).put("seed", JSONArray(seed.map { it.toInt() and 255 })))
                                        }
                                        fun openActivity(eventId: String) {
                                            val kind = state?.optJSONObject("organization")?.optJSONObject("plans")?.optJSONObject(eventId)?.optString("kind")
                                            topBackStack.add(if (kind != null && kind in setOf("online", "meeting")) OfficeWorkflowEvent(kind, eventId) else OfficeEventDetail(eventId))
                                        }
                                        fun openCompetition(competitionId: String) {
                                            val plans = state?.optJSONObject("organization")?.optJSONObject("plans")
                                            val parent = plans?.keys()?.asSequence()?.firstOrNull { eventId ->
                                                val games = plans.optJSONObject(eventId)?.optJSONObject("games")
                                                games?.keys()?.asSequence()?.any { games.optJSONObject(it)?.optString("league") == competitionId } == true
                                            }
                                            if (parent != null) openActivity(parent)
                                            topBackStack.add(LeagueDetail(competitionId))
                                        }
                                        fun openCreation() {
                                            if (topBackStack.firstOrNull() != ActivitiesRoot) navigateRoot(WorkspaceDestination.Activities)
                                            topBackStack.add(ActivityCreate)
                                        }
                                        NavDisplay(
                                            backStack = topBackStack,
                                            modifier = Modifier.fillMaxSize(),
                                            onBack = detailBack,
                                            entryProvider = entryProvider {
                                                entry<WorkspaceRoot> {
                                                    WorkspaceDashboard(state, identity, busy,
                                                        onCreateActivity = { openCreation() },
                                                        onMembershipIssuance = { openAccount(AccountIssuance) },
                                                        onPersonnel = { openAccount(AccountPersonnel) },
                                                        onOpenActivities = { navigateRoot(WorkspaceDestination.Activities) },
                                                        onOpenApprovals = { navigateRoot(WorkspaceDestination.Tasks) },
                                                        onOpenReports = { navigateRoot(WorkspaceDestination.Activities); topBackStack.add(OfficeReports) },
                                                        onOpenResources = { openAccount(); topBackStack.add(OfficeWorkflowSetup) },
                                                    )
                                                }
                                                entry<ActivitiesRoot> {
                                                    ActivityCatalog(state, identity, busy,
                                                        onCreate = { openCreation() },
                                                        onOpenEvent = { openActivity(it) },
                                                        onOpenCompetition = { openCompetition(it) },
                                                    )
                                                }
                                                entry<TasksRoot> {
                                                    TaskInbox(state, identity, busy, onOpenFlow = { topBackStack.add(OfficeFlowDetail(it)) })
                                                }
                                                entry<ActivityCreate> {
                                                    ActivityCreateMenu(state, identity, busy,
                                                        onCreateDraft = { topBackStack.add(OfficeDraftCreate) },
                                                        onCreateOnline = { topBackStack.add(OfficeWorkflowEvents("online")) },
                                                        onCreateMeeting = { topBackStack.add(OfficeWorkflowEvents("meeting")) },
                                                        onCreateCompetition = { topBackStack.add(LeagueCreate) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<LeagueCreate> {
                                                    LeagueCreateFlow(
                                                        league = league,
                                                        seats = seats,
                                                        crossClub = crossClub,
                                                        host = host,
                                                        scorer = scorer,
                                                        connected = state != null,
                                                        busy = busy,
                                                        seatsValid = seatsValid,
                                                        nameTaken = state?.optJSONObject("leagues")?.has(league.trim()) == true,
                                                        identityOptions = listOfNotNull(peer.takeIf { it.isNotBlank() }, identity.takeIf { it.isNotBlank() }).distinct(),
                                                        onLeagueChange = { league = it },
                                                        onSeatsChange = { seats = it },
                                                        onCrossClubChange = { crossClub = it },
                                                        onHostChange = { host = it },
                                                        onScorerChange = { scorer = it },
                                                        send = formSender,
                                                        onCreated = {
                                                            val competitionId = league.trim()
                                                            league = ""
                                                            navigateRoot(WorkspaceDestination.Activities)
                                                            topBackStack.add(LeagueDetail(competitionId))
                                                        },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<LeagueDetail> { key ->
                                                    LeagueSummaryPage(
                                                        state = state,
                                                        leagueId = key.leagueId,
                                                        busy = busy,
                                                        onStart = startLeague,
                                                        onOpenTeams = { topBackStack.add(LeagueTeams(key.leagueId)) },
                                                        onOpenDisputes = { topBackStack.add(LeagueDisputes(key.leagueId)) },
                                                        onOpenSeason = { topBackStack.add(LeagueSeason(key.leagueId)) },
                                                        onOpenOps = { topBackStack.add(LeagueOps) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<LeagueTeams> { key ->
                                                    LeagueTeamsPage(state, key.leagueId, busy, startLeague, detailBack)
                                                }
                                                entry<LeagueDisputes> {
                                                    LeagueDisputesPage(state, busy, { topBackStack.add(LeagueDispute(it)) }, detailBack)
                                                }
                                                entry<LeagueDispute> { key ->
                                                    LeagueDisputeDetailPage(state, key.disputeId, busy, { request(it) }, detailBack)
                                                }
                                                entry<LeagueSeason> { key ->
                                                    LeagueSeasonPage(
                                                        state = state,
                                                        identity = identity,
                                                        busy = busy,
                                                        onCreateSeason = { topBackStack.add(LeagueSeasonCreate) },
                                                        onSchedule = { topBackStack.add(LeagueSchedule) },
                                                        request = { request(it) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<LeagueSeasonCreate> {
                                                    LeagueSeasonCreatePage(state, busy, { request(it) }, detailBack)
                                                }
                                                entry<LeagueSchedule> {
                                                    LeagueSchedulePage(state, busy, { request(it) }, detailBack)
                                                }
                                                entry<LeagueOps> {
                                                    LeagueOpsFlow(execute = { input, reply -> executeOps(input, reply) }, onBack = detailBack)
                                                }
                                                entry<WorkflowRoot> {
                                                    ActivityWorkflowHome(
                                                        state = state, busy = busy, identity = identity, focusedNotice = focusedNotice,
                                                        onOpenKind = { topBackStack.add(OfficeWorkflowEvents(it)) },
                                                        onOpenEvent = { kind, eventId -> topBackStack.add(OfficeWorkflowEvent(kind, eventId)) },
                                                        onOpenReports = { topBackStack.add(OfficeReports) },
                                                        onOpenSetup = { topBackStack.add(OfficeWorkflowSetup) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<OfficeDraftCreate> {
                                                    OfficeDraftCreateFlow(
                                                        state, busy, canCreate = officeActor(state, identity).canCreateDraft,
                                                        onBack = detailBack,
                                                        onCreated = { eventId -> navigateRoot(WorkspaceDestination.Activities); topBackStack.add(OfficeEventDetail(eventId)) },
                                                        send = formSender,
                                                    )
                                                }
                                                entry<OfficeEventDetail> { key ->
                                                    val event = state?.optJSONObject("events")?.optJSONObject(key.eventId)
                                                    TaskPage(event?.optString("title") ?: stringResource(R.string.office_title), busy, detailBack) {
                                                        OfficeEventDetailScreen(
                                                            state = state,
                                                            busy = busy,
                                                            device = identity,
                                                            eventId = key.eventId,
                                                            onEdit = { topBackStack.add(OfficeEventEdit(key.eventId)) },
                                                            send = { request(it) },
                                                        )
                                                    }
                                                }
                                                entry<OfficeEventEdit> { key ->
                                                    OfficeEventEditScreen(state, busy, identity, key.eventId, detailBack, formSender)
                                                }
                                                entry<OfficeFlowDetail> { key ->
                                                    TaskPage(stringResource(R.string.office_flows), busy, detailBack) {
                                                        OfficeFlowDetailScreen(
                                                            state = state,
                                                            busy = busy,
                                                            device = identity,
                                                            flowId = key.flowId,
                                                            onDecision = { topBackStack.add(OfficeFlowDecision(key.flowId)) },
                                                            send = { request(it) },
                                                        )
                                                    }
                                                }
                                                entry<OfficeFlowDecision> { key ->
                                                    OfficeFlowDecisionScreen(state, busy, identity, key.flowId, detailBack, { request(it) })
                                                }
                                                entry<OfficeWorkflowEvents> { key ->
                                                    OfficeWorkflowEventsPage(
                                                        state = state,
                                                        kind = key.kind,
                                                        busy = busy,
                                                        onOpenEvent = { topBackStack.add(OfficeWorkflowEvent(key.kind, it)) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<OfficeWorkflowEvent> { key ->
                                                    OfficeWorkflowEventPage(
                                                        state = state,
                                                        kind = key.kind,
                                                        eventId = key.eventId,
                                                        busy = busy,
                                                        identity = identity,
                                                        onOpenCompetition = { topBackStack.add(LeagueDetail(it)) },
                                                        send = formSender,
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<OfficeReports> {
                                                    OfficeReportsPage(
                                                        state = state,
                                                        identity = identity,
                                                        busy = busy,
                                                        onCreate = { topBackStack.add(OfficeReportCreate) },
                                                        onOpenReport = { topBackStack.add(OfficeReportDetail(it)) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<OfficeReportCreate> {
                                                    OfficeReportCreatePage(state, identity, busy, { request(it) }, detailBack)
                                                }
                                                entry<OfficeReportDetail> { key ->
                                                    OfficeReportDetailPage(state, identity, key.reportId, busy, { request(it) }, detailBack)
                                                }
                                                entry<OfficeWorkflowSetup> {
                                                    OfficeWorkflowSetupHome(
                                                        state = state,
                                                        identity = identity,
                                                        busy = busy,
                                                        onOpenAutomation = { topBackStack.add(OfficeWorkflowAutomation) },
                                                        onOpenSchool = { topBackStack.add(OfficeWorkflowSchool) },
                                                        onOpenResource = { topBackStack.add(OfficeWorkflowResource) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<OfficeWorkflowAutomation> {
                                                    WorkflowAutomationPage(state, identity, busy, { request(it) }, detailBack)
                                                }
                                                entry<OfficeWorkflowSchool> {
                                                    WorkflowSchoolPage(state, identity, busy, formSender, detailBack)
                                                }
                                                entry<OfficeWorkflowResource> {
                                                    WorkflowResourcePage(state, identity, busy, formSender, detailBack)
                                                }
                                                entry<AccountRoot> {
                                                    AccountHomeNav(
                                                        state = state,
                                                        busy = busy,
                                                        identity = identity,
                                                        configured = identityConfiguration.isNotBlank(),
                                                        pushReady = pushReady,
                                                        onOpenIdentity = { topBackStack.add(AccountIdentity()) },
                                                        onOpenIssuance = { topBackStack.add(AccountIssuance) },
                                                        onOpenPersonnel = { topBackStack.add(AccountPersonnel) },
                                                        onOpenConnection = { topBackStack.add(AccountConnection) },
                                                        onOpenDevice = { topBackStack.add(AccountDevice) },
                                                        onOpenAdmin = { topBackStack.add(AccountAdmin) },
                                                        onOpenAudit = { topBackStack.add(AccountAudit) },
                                                        onOpenHelp = { topBackStack.add(AccountHelp) },
                                                    )
                                                }
                                                entry<AccountIdentity> { identityTarget ->
                                                    val identityScope = StartupScope(startupEpoch, identityConfiguration, identity)
                                                    MainIdentityPage(
                                                        configuration = identityConfiguration,
                                                        device = identity,
                                                        busy = busy,
                                                        directory = identityDirectory,
                                                        publicIdentity = publicIdentity,
                                                        state = identityState,
                                                        facts = identityFacts,
                                                        identityScope = identityScope,
                                                        onConfig = { value -> saveIdentityConfiguration(value) },
                                                        fetch = { input, reply -> fetchGenesis(input, reply) },
                                                        discover = { reply -> discoverServers(reply) },
                                                        onBusinessGeneses = { pins -> applyBusinessGeneses(pins) },
                                                        onFetchFailed = { errorMessage = getString(R.string.identity_fetch_failed) },
                                                        executeOps = { input, reply, identityOperation, operationScope ->
                                                            executeOps(input, reply, identityOperation, operationScope)
                                                        },
                                                        onIdentityState = { operationScope, next, facts ->
                                                            applyIdentityState(operationScope, next, facts)
                                                        },
                                                        onBack = detailBack,
                                                        proposal = identityProposal,
                                                        openRecordsInitially = identityTarget.openRecords,
                                                    )
                                                }
                                                entry<AccountIssuance> {
                                                    val identityScope = StartupScope(startupEpoch, identityConfiguration, identity)
                                                    AccountIssuancePage(
                                                        config = identityConfiguration,
                                                        device = identity,
                                                        busy = busy,
                                                        directory = identityDirectory,
                                                        state = identityState,
                                                        facts = identityFacts,
                                                        knownMembers = knownMemberIds(identityFacts),
                                                        current = identityState.availability == IdentityAvailability.Current,
                                                        configured = identityConfiguration.isNotBlank(),
                                                        execute = { input, reply -> executeOps(input, reply, true, identityScope) },
                                                        onStateChange = { next, facts -> applyIdentityState(identityScope, next, facts) },
                                                        onBack = detailBack,
                                                        proposalState = identityProposal,
                                                    )
                                                }
                                                entry<AccountConnection> {
                                                    AccountConnectionPage(
                                                        state = state,
                                                        identity = identity,
                                                        member = identityState.member,
                                                        identityReady = startupSession.identityVerified,
                                                                        address = address,
                                                        peer = peer,
                                                        genesisHash = genesisHash,
                                                        connected = state != null,
                                                        busy = busy,
                                                        failure = errorMessage,
                                                        onConnectTarget = { target -> applyConnectionTarget(target) },
                                                        onLink = { target -> applyConnectionTarget(target) },
                                                        discover = { reply -> discoverServers(reply) },
                                                        onBack = detailBack,
                                                    )
                                                }
                                                entry<AccountDevice> {
                                                    AccountDevicePage(publicIdentity, busy, detailBack)
                                                }
                                                entry<AccountAdmin> {
                                                    AccountAdminPage(state, identity, busy, detailBack) { request(it) }
                                                }
                                                entry<AccountPersonnel> {
                                                    PersonnelFlowHost(
                                                        state, identity, busy, formSender, detailBack,
                                                        facts = identityFacts,
                                                        // Opens the shared identity action page on its record actions.
                                                        onOpenTransfer = { topBackStack.add(AccountIdentity(openRecords = true)) },
                                                    )
                                                }
                                                entry<AccountAudit> {
                                                    AccountAuditPage(state, identity, busy, detailBack) { request(it) }
                                                }
                                                entry<AccountHelp> {
                                                    AccountHelpPage(busy, detailBack)
                                                }
                                            },
                                        )
                                    }
                                    }
                                },
                            )
                            Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                                errorMessage?.let { error ->
                                    ErrorBanner(error, Modifier.padding(horizontal = SPACING_L)) { errorMessage = null }
                                }
                                SnackbarHost(snackbar)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One Main-owned renderer for account identity and the pre-connection chair setup. */
@Composable
internal fun MainIdentityPage(
    configuration: String,
    device: String,
    busy: Boolean,
    directory: java.io.File,
    publicIdentity: String,
    state: IdentityState,
    facts: JSONObject?,
    identityScope: StartupScope,
    onConfig: (String) -> Unit,
    fetch: (JSONObject, (JSONObject) -> Unit) -> Unit,
    discover: ((JSONObject) -> Unit) -> Unit,
    onBusinessGeneses: (JSONObject) -> Unit,
    onFetchFailed: () -> Unit,
    executeOps: (JSONObject, (JSONObject) -> Unit, Boolean, StartupScope?) -> Unit,
    onIdentityState: (StartupScope, IdentityState, JSONObject?) -> Unit,
    onBack: () -> Unit,
    proposal: androidx.compose.runtime.MutableState<String>,
    openRecordsInitially: Boolean = false,
) {
    IdentityFlowHost(
        mode = IdentityFlowMode.Account,
        config = configuration,
        device = device,
        busy = busy,
        directory = directory,
        publicIdentity = publicIdentity,
        state = state,
        facts = facts,
        members = knownMemberIds(facts),
        onConfig = onConfig,
        fetch = fetch,
        discover = discover,
        onBusinessGeneses = onBusinessGeneses,
        onFetchFailed = onFetchFailed,
        execute = { input, reply -> executeOps(input, reply, true, identityScope) },
        onStateChange = { next, nextFacts -> onIdentityState(identityScope, next, nextFacts) },
        onBack = onBack,
        proposal = proposal,
        openRecordsInitially = openRecordsInitially,
    )
}

@Serializable internal data object WorkspaceRoot : NavKey
@Serializable internal data object ActivitiesRoot : NavKey
@Serializable internal data object ActivityCreate : NavKey
@Serializable internal data object TasksRoot : NavKey
@Serializable internal data object LeagueCreate : NavKey
@Serializable internal data class LeagueDetail(val leagueId: String) : NavKey
@Serializable internal data class LeagueTeams(val leagueId: String) : NavKey
@Serializable internal data class LeagueDisputes(val leagueId: String) : NavKey
@Serializable internal data class LeagueDispute(val disputeId: String) : NavKey
@Serializable internal data class LeagueSeason(val leagueId: String) : NavKey
@Serializable internal data object LeagueSeasonCreate : NavKey
@Serializable internal data object LeagueSchedule : NavKey
@Serializable internal data object LeagueOps : NavKey
@Serializable internal data object WorkflowRoot : NavKey
@Serializable internal data object OfficeDraftCreate : NavKey
@Serializable internal data class OfficeEventDetail(val eventId: String) : NavKey
@Serializable internal data class OfficeEventEdit(val eventId: String) : NavKey
@Serializable internal data class OfficeFlowDetail(val flowId: String) : NavKey
@Serializable internal data class OfficeFlowDecision(val flowId: String) : NavKey
@Serializable internal data class OfficeWorkflowEvents(val kind: String) : NavKey
@Serializable internal data class OfficeWorkflowEvent(val kind: String, val eventId: String) : NavKey
@Serializable internal data object OfficeReports : NavKey
@Serializable internal data object OfficeReportCreate : NavKey
@Serializable internal data class OfficeReportDetail(val reportId: String) : NavKey
@Serializable internal data object OfficeWorkflowSetup : NavKey
@Serializable internal data object OfficeWorkflowAutomation : NavKey
@Serializable internal data object OfficeWorkflowSchool : NavKey
@Serializable internal data object OfficeWorkflowResource : NavKey
@Serializable internal data object AccountRoot : NavKey
@Serializable internal data class AccountIdentity(val openRecords: Boolean = false) : NavKey
@Serializable internal data object AccountIssuance : NavKey
@Serializable internal data object AccountConnection : NavKey
@Serializable internal data object AccountDevice : NavKey
@Serializable internal data object AccountAdmin : NavKey
@Serializable internal data object AccountPersonnel : NavKey
@Serializable internal data object AccountAudit : NavKey
@Serializable internal data object AccountHelp : NavKey

/** The account root replaces the visible management stack without changing the application connection. */
internal fun MutableList<NavKey>.openAccount(destination: NavKey = AccountRoot) {
    clear()
    add(AccountRoot)
    if (destination != AccountRoot) add(destination)
}

/** The account root returns to the unified workspace. */
internal fun MutableList<NavKey>.leaveAccount() {
    if (lastOrNull() != AccountRoot) return
    clear()
    add(WorkspaceRoot)
}

/** True while the visible page belongs to the account root or one of its subpages. */
internal fun NavKey.isAccountPage(): Boolean = when (this) {
    AccountRoot, is AccountIdentity, AccountIssuance, AccountConnection,
    AccountDevice, AccountAdmin, AccountPersonnel, AccountAudit, AccountHelp -> true
    else -> false
}

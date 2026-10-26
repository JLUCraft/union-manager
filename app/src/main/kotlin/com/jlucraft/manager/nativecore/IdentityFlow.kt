package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Serializable internal data object IdentityFlowHome : NavKey
@Serializable internal data object IdentityFlowImport : NavKey
@Serializable internal data object IdentityFlowTechnical : NavKey
@Serializable internal data object IdentityFlowFacts : NavKey
@Serializable internal data object IdentityFlowRecords : NavKey
@Serializable internal data object IdentityFlowShare : NavKey
@Serializable internal data object IdentityFlowEnrollment : NavKey
@Serializable internal data object IdentityFlowDiscover : NavKey
@Serializable internal data class IdentityFlowOrganization(val address: String, val peer: String) : NavKey
@Serializable internal data class IdentityFlowTrust(val address: String, val peer: String) : NavKey
@Serializable internal data object IdentityFlowTrustFacts : NavKey

internal enum class IdentityFlowMode { Startup, Account }

/** Fetched organization material together with the exact server it came from. */
private data class FetchedOrganization(val address: String, val peer: String, val result: JSONObject)

/**
 * The identity business as a stack of single-task pages. The startup gate and the account
 * "我的身份" entry share this host, so import, trust, verification and enrollment never need a
 * second implementation. State and the one automatic sync live here; pages stay stateless.
 */
@Composable
internal fun IdentityFlowHost(
    mode: IdentityFlowMode,
    config: String,
    device: String,
    busy: Boolean,
    directory: File,
    publicIdentity: String,
    state: IdentityState,
    facts: JSONObject?,
    members: List<String> = emptyList(),
    onConfig: (String) -> Unit,
    fetch: (JSONObject, (JSONObject) -> Unit) -> Unit,
    discover: ((JSONObject) -> Unit) -> Unit,
    onBusinessGeneses: (JSONObject) -> Unit,
    onFetchFailed: () -> Unit,
    execute: (JSONObject, (JSONObject) -> Unit) -> Unit,
    onStateChange: (IdentityState, JSONObject?) -> Unit,
    onBack: () -> Unit,
    autoSync: Boolean = false,
    /** The activity may own the public record draft so leaving this page never drops a signed record. */
    proposal: androidx.compose.runtime.MutableState<String>? = null,
    review: (String) -> String = { text -> UnionNative.management(text) },
    /** Open on the record action page instead of the identity summary (used by the personnel entry). */
    openRecordsInitially: Boolean = false,
) {
    val latestExecute by rememberUpdatedState(execute)
    val latestStateChange by rememberUpdatedState(onStateChange)
    val latestConfig by rememberUpdatedState(onConfig)
    val latestBusiness by rememberUpdatedState(onBusinessGeneses)
    val latestFetchFailed by rememberUpdatedState(onFetchFailed)
    val latestFetch by rememberUpdatedState(fetch)
    val proposalState = proposal ?: rememberSaveable(config, device) { mutableStateOf("") }
    val ops = remember(config, device) {
        IdentityOps(
            config = config,
            device = device,
            directory = directory,
            review = review,
            execute = { input, reply -> latestExecute(input, reply) },
            onConfig = { value -> latestConfig(value) },
            onStateChange = { next, nextFacts -> latestStateChange(next, nextFacts) },
            onBusinessGeneses = { pins -> latestBusiness(pins) },
            onFetchFailed = { latestFetchFailed() },
            initialState = state,
            initialFacts = facts,
            proposalState = proposalState,
        )
    }
    val latestBusy by rememberUpdatedState(busy)
    val autoSyncLatch = remember(config, device) { IdentityAutoSync() }
    // One automatic check per configuration; a failure waits for the explicit retry on the page.
    LaunchedEffect(config, device, autoSync) {
        if (!autoSync || config.isBlank()) return@LaunchedEffect
        snapshotFlow { latestBusy }.first { !it }
        if (autoSyncLatch.shouldStart(enabled = true, idle = true)) ops.run("sync")
    }
    var fetched by remember { mutableStateOf<FetchedOrganization?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var fetchFailure by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var discoverFailure by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf("") }
    val initialStack = remember(openRecordsInitially) {
        if (openRecordsInitially) arrayOf<NavKey>(IdentityFlowHome, IdentityFlowRecords) else arrayOf<NavKey>(IdentityFlowHome)
    }
    val backStack = rememberNavBackStack(*initialStack)
    val pop: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack()
    }
    // A reply may only open the trust page for the organization the user has open.
    // Back, cancel, another organization or leaving this host makes the attempt stale.
    var fetchGeneration by remember { mutableStateOf(0L) }
    var hostAttached by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { hostAttached = false; fetchGeneration += 1 } }
    fun cancelFetch() {
        fetchGeneration += 1
        fetching = false
        fetchFailure = false
    }
    fun fetchOrganization(address: String, peer: String) {
        fetchGeneration += 1
        val generation = fetchGeneration
        fetching = true
        fetchFailure = false
        latestFetch(JSONObject().put("address", address).put("peer", peer)) { result ->
            val target = IdentityFlowOrganization(address, peer)
            if (hostAttached && generation == fetchGeneration && backStack.lastOrNull() == target) {
                fetching = false
                if (result.has("error")) fetchFailure = true
                else {
                    fetched = FetchedOrganization(address, peer, result)
                    backStack.add(IdentityFlowTrust(address, peer))
                }
            }
        }
    }
    // Read the observable state before the navigation graph is built so the pages below bind to
    // the current value instead of a controller captured behind a stable ops reference.
    val currentState = ops.state
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<IdentityFlowHome> {
                if (mode == IdentityFlowMode.Startup) IdentityHomePage(
                    ops = ops,
                    state = currentState,
                    busy = busy,
                    publicIdentity = publicIdentity,
                    onOpenImport = { backStack.add(IdentityFlowImport) },
                    onOpenDiscover = { backStack.add(IdentityFlowDiscover) },
                    onOpenShare = { backStack.add(IdentityFlowShare) },
                    onOpenEnrollment = { backStack.add(IdentityFlowEnrollment) },
                ) else IdentitySummaryPage(
                    ops = ops,
                    state = currentState,
                    busy = busy,
                    onBack = pop,
                    onOpenTechnical = { backStack.add(IdentityFlowTechnical) },
                    onOpenFacts = { backStack.add(IdentityFlowFacts) },
                    onOpenRecords = { backStack.add(IdentityFlowRecords) },
                )
            }
            entry<IdentityFlowImport> {
                IdentityImportPage(
                    ops = ops,
                    busy = busy,
                    onSaved = { pop() },
                    onBack = pop,
                    onOpenTechnical = { backStack.add(IdentityFlowTechnical) },
                )
            }
            entry<IdentityFlowTechnical> {
                IdentityTechnicalPage(
                    ops = ops,
                    busy = busy,
                    onBack = pop,
                    onOpenFacts = { backStack.add(IdentityFlowFacts) },
                )
            }
            entry<IdentityFlowFacts> { IdentityFactsPage(ops, busy, onBack = pop) }
            entry<IdentityFlowRecords> { IdentityRecordsPage(ops, busy, members = members, onBack = pop) }
            entry<IdentityFlowShare> { IdentitySharePage(publicIdentity, busy, onBack = pop) }
            entry<IdentityFlowEnrollment> { IdentityEnrollmentPage(ops, busy, onBack = pop) }
            entry<IdentityFlowDiscover> {
                DiscoverOrganizationsPage(
                    busy = busy,
                    discovering = discovering,
                    failed = discoverFailure,
                    serversJson = discovered,
                    onDiscover = {
                        discovering = true
                        discoverFailure = false
                        discovered = ""
                        discover { result ->
                            discovering = false
                            if (result.has("error")) discoverFailure = true
                            else discovered = (result.optJSONArray("servers") ?: JSONArray()).toString()
                        }
                    },
                    onOpen = { address, peer -> backStack.add(IdentityFlowOrganization(address, peer)) },
                    onBack = pop,
                )
            }
            entry<IdentityFlowOrganization> { key ->
                // Leaving this page (button or system back) makes any in-flight fetch stale.
                DisposableEffect(key) { onDispose { cancelFetch() } }
                OrganizationPage(
                    address = key.address,
                    peer = key.peer,
                    busy = busy,
                    fetching = fetching,
                    failed = fetchFailure,
                    onFetch = { fetchOrganization(key.address, key.peer) },
                    onBack = { cancelFetch(); pop() },
                )
            }
            entry<IdentityFlowTrust> { key ->
                val result = fetched?.takeIf { it.address == key.address && it.peer == key.peer }?.result
                if (result == null) TrustMissingPage(
                    onRetry = {
                        // Reopen the fetch step for this exact organization instead of trusting
                        // whatever material happened to be in memory.
                        val organization = IdentityFlowOrganization(key.address, key.peer)
                        if (backStack.lastOrNull() == IdentityFlowTrust(key.address, key.peer)) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                        if (backStack.lastOrNull() != organization) backStack.add(organization)
                    },
                ) else TrustPage(
                    result = result,
                    busy = busy,
                    onOpenDetails = { backStack.add(IdentityFlowTrustFacts) },
                    onConfirm = {
                        if (ops.confirmFetched(result, key.address, key.peer)) {
                            fetched = null
                            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        }
                    },
                    onBack = { cancelFetch(); pop() },
                )
            }
            entry<IdentityFlowTrustFacts> {
                val result = fetched?.result
                if (result == null) TrustMissingPage(onRetry = pop) else GenesisFactsPage(result, onBack = pop)
            }
        },
    )
}

/** Discovered hosts are untrusted hints; choosing one still opens its material for review. */
@Composable
private fun DiscoverOrganizationsPage(
    busy: Boolean,
    discovering: Boolean,
    failed: Boolean,
    serversJson: String,
    onDiscover: () -> Unit,
    onOpen: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.startup_identity_find_org), busy, onBack) {
        Text(
            stringResource(R.string.startup_identity_find_org_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionButton(R.string.startup_identity_find_org_action, !busy && !discovering) { onDiscover() }
        if (discovering) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.connection_discover_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        val servers = runCatching { JSONArray(serversJson) }.getOrNull()
        if (servers != null && servers.length() == 0) Text(
            stringResource(R.string.connection_discover_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        servers?.let { values ->
            for (index in 0 until values.length()) {
                val server = values.optJSONObject(index) ?: continue
                val serverAddress = server.optString("address")
                val serverPeer = server.optString("peer")
                if (serverAddress.isBlank() || serverPeer.isBlank()) continue
                key(serverAddress, serverPeer) {
                    MenuEntry(
                        label = server.optString("federation").ifBlank { shortIdentifier(serverPeer) },
                        help = stringResource(R.string.startup_identity_organization_help, shortIdentifier(serverPeer)),
                        enabled = !busy,
                        onClick = { onOpen(serverAddress, serverPeer) },
                    )
                }
            }
        }
    }
}

/** One discovered organization: fetch its material instead of silently filling a hidden address. */
@Composable
private fun OrganizationPage(
    address: String,
    peer: String,
    busy: Boolean,
    fetching: Boolean,
    failed: Boolean,
    onFetch: () -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.startup_identity_organization_title), busy, onBack) {
        ManagerCard(
            title = shortIdentifier(peer),
            helper = stringResource(R.string.startup_identity_organization_helper),
            emphasized = false,
        ) {
            Text(stringResource(R.string.startup_identity_organization_address, address), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton(R.string.startup_identity_organization_fetch, !busy && !fetching) { onFetch() }
            if (fetching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.identity_fetch_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** First-trust review: organization, founders and quorum, with the raw JSON on its own page. */
@Composable
private fun TrustPage(
    result: JSONObject,
    busy: Boolean,
    onOpenDetails: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.startup_identity_trust_title), busy, onBack) {
        ManagerCard(
            title = stringResource(R.string.startup_identity_trust_title),
            helper = stringResource(R.string.startup_identity_trust_body),
        ) {
            if (!GenesisTrustSummary(result)) {
                Text(stringResource(R.string.identity_fetch_details_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            MenuEntry(
                label = stringResource(R.string.startup_identity_trust_details),
                help = stringResource(R.string.startup_identity_trust_details_help),
                enabled = !busy,
                onClick = onOpenDetails,
            )
            ActionButton(R.string.startup_identity_trust_confirm, !busy) { onConfirm() }
            TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    }
}

/** The fetched material is gone (for example after a configuration change); go back and fetch again. */
@Composable
private fun TrustMissingPage(onRetry: () -> Unit) {
    TaskPage(stringResource(R.string.startup_identity_trust_title), false, onRetry) {
        Text(
            stringResource(R.string.identity_fetch_details_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionButton(R.string.startup_identity_organization_fetch, true) { onRetry() }
    }
}

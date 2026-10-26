package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.File

/** Renders only the stage the session may currently expose; locked content is owned by the activity. */
@Composable
internal fun StartupGate(
    session: StartupSession,
    identity: @Composable () -> Unit,
    connection: @Composable () -> Unit,
    business: @Composable () -> Unit,
) {
    when (session.effectiveStage()) {
        StartupStage.Identity -> identity()
        StartupStage.Connection -> connection()
        StartupStage.Business -> business()
        StartupStage.Locked -> Unit
    }
}

/**
 * First gate. Device unlock already happened; this is the member identity step. Every page below is
 * one task, and the one automatic check runs here so navigating between the pages never repeats it.
 */
@Composable
internal fun StartupIdentityStage(
    configuration: String,
    device: String,
    publicIdentity: String,
    identityState: IdentityState,
    facts: JSONObject?,
    busy: Boolean,
    directory: File,
    onConfig: (String) -> Unit,
    fetch: (JSONObject, (JSONObject) -> Unit) -> Unit,
    discover: ((JSONObject) -> Unit) -> Unit,
    onBusinessGeneses: (JSONObject) -> Unit,
    onFetchFailed: () -> Unit,
    execute: (JSONObject, (JSONObject) -> Unit) -> Unit,
    onStateChange: (IdentityState, JSONObject?) -> Unit,
    proposal: androidx.compose.runtime.MutableState<String>? = null,
    review: (String) -> String = { text -> UnionNative.management(text) },
) {
    IdentityFlowHost(
        mode = IdentityFlowMode.Startup,
        config = configuration,
        device = device,
        busy = busy,
        directory = directory,
        publicIdentity = publicIdentity,
        state = identityState,
        facts = facts,
        onConfig = onConfig,
        fetch = fetch,
        discover = discover,
        onBusinessGeneses = onBusinessGeneses,
        onFetchFailed = onFetchFailed,
        execute = execute,
        onStateChange = onStateChange,
        onBack = {},
        autoSync = true,
        proposal = proposal,
        review = review,
    )
}

@Serializable internal data object ConnectionMethod : NavKey
@Serializable internal data object ConnectionDiscovery : NavKey
@Serializable internal data object ConnectionManual : NavKey
@Serializable internal data object ConnectionLinkStep : NavKey
@Serializable internal data object ConnectionCheck : NavKey

/**
 * Second gate: reach the one organization, then read one explicit check result. An incomplete
 * target starts at the method step; a complete target opens the check page directly.
 */
@Composable
internal fun StartupConnectionStage(
    identity: MemberIdentityView?,
    identityReady: Boolean,
    address: String,
    peer: String,
    genesisHash: String,
    busy: Boolean,
    failure: String? = null,
    onAddressChange: (String) -> Unit,
    onPeerChange: (String) -> Unit,
    onGenesisChange: (String) -> Unit,
    onConnect: () -> Unit,
    onLink: (ConnectionTarget) -> Unit,
    discover: ((JSONObject) -> Unit) -> Unit,
    onRecheckIdentity: () -> Unit,
    onOpenIdentitySetup: (() -> Unit)? = null,
) {
    ConnectionFlow(
        identity = identity,
        identityReady = identityReady,
        address = address,
        peer = peer,
        genesisHash = genesisHash,
        busy = busy,
        failure = failure,
        connected = false,
        onAddressChange = onAddressChange,
        onPeerChange = onPeerChange,
        onGenesisChange = onGenesisChange,
        onConnect = onConnect,
        onLink = onLink,
        discover = discover,
        onRecheckIdentity = onRecheckIdentity,
        onExit = {},
        onOpenIdentitySetup = onOpenIdentitySetup,
    )
}

/**
 * Shared connection steps: method, discovery/manual target, then one check. The account settings
 * and the launch gate use the same pages; only the exit differs.
 */
@Composable
internal fun ConnectionFlow(
    identity: MemberIdentityView?,
    identityReady: Boolean,
    address: String,
    peer: String,
    genesisHash: String,
    busy: Boolean,
    failure: String?,
    connected: Boolean,
    onAddressChange: (String) -> Unit,
    onPeerChange: (String) -> Unit,
    onGenesisChange: (String) -> Unit,
    onConnect: () -> Unit,
    onLink: (ConnectionTarget) -> Unit,
    discover: ((JSONObject) -> Unit) -> Unit,
    onRecheckIdentity: () -> Unit,
    onExit: () -> Unit,
    onOpenIdentitySetup: (() -> Unit)? = null,
) {
    val complete = identityReady && address.isNotBlank() && peer.isNotBlank() && GENESIS_HASH_PATTERN.matches(genesisHash)
    val chairCanManageIdentity = identityReady && identity != null && identity.chairOf.isNotEmpty()
    val backStack = rememberNavBackStack(if (complete) ConnectionCheck else ConnectionMethod)
    val pop: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onExit()
    }
    val changeTarget: () -> Unit = {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (backStack.lastOrNull() != ConnectionMethod) backStack.add(ConnectionMethod)
    }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<ConnectionMethod> {
                ConnectionPage(
                    title = stringResource(R.string.startup_connection_method_title),
                    busy = busy,
                    onBack = onExit,
                ) {
                    Text(
                        stringResource(R.string.startup_connection_method_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OrganizationIdentitySetupEntry(chairCanManageIdentity, busy, onOpenIdentitySetup)
                    MenuEntry(
                        label = stringResource(R.string.startup_connection_method_discovery),
                        help = stringResource(R.string.startup_connection_method_discovery_help),
                        enabled = !busy,
                        onClick = { backStack.add(ConnectionDiscovery) },
                    )
                    MenuEntry(
                        label = stringResource(R.string.startup_connection_method_manual),
                        help = stringResource(R.string.startup_connection_method_manual_help),
                        enabled = !busy,
                        onClick = { backStack.add(ConnectionManual) },
                    )
                    MenuEntry(
                        label = stringResource(R.string.connection_link),
                        help = stringResource(R.string.connection_link_help),
                        enabled = !busy,
                        onClick = { backStack.add(ConnectionLinkStep) },
                    )
                    ConnectionTargetSummary(address = address, peer = peer, genesisHash = genesisHash)
                    ActionButton(R.string.startup_connection_check, !busy && complete) {
                        onConnect()
                        backStack.add(ConnectionCheck)
                    }
                }
            }
            entry<ConnectionDiscovery> {
                ConnectionPage(
                    title = stringResource(R.string.startup_connection_discovery_title),
                    busy = busy,
                    onBack = pop,
                ) {
                    Text(
                        stringResource(R.string.startup_connection_discovery_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ServerDiscovery(busy, discover) { target ->
                        onAddressChange(target.address)
                        onPeerChange(target.peer)
                        onGenesisChange(target.genesisHash)
                        pop()
                    }
                }
            }
            entry<ConnectionManual> {
                ConnectionPage(
                    title = stringResource(R.string.startup_connection_manual_title),
                    busy = busy,
                    onBack = pop,
                ) {
                    ConnectionTargetFields(
                        address = address,
                        peer = peer,
                        genesisHash = genesisHash,
                        busy = busy,
                        onAddressChange = onAddressChange,
                        onPeerChange = onPeerChange,
                        onGenesisChange = onGenesisChange,
                    )
                    val completeHere = address.isNotBlank() && peer.isNotBlank() && GENESIS_HASH_PATTERN.matches(genesisHash)
                    ActionButton(R.string.startup_connection_check, !busy && completeHere) {
                        onConnect()
                        backStack.add(ConnectionCheck)
                    }
                }
            }
            entry<ConnectionLinkStep> {
                var link by rememberSaveable { mutableStateOf("") }
                var linkInvalid by rememberSaveable { mutableStateOf(false) }
                ConnectionPage(
                    title = stringResource(R.string.connection_link),
                    busy = busy,
                    onBack = pop,
                ) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it; linkInvalid = false },
                        label = { Text(stringResource(R.string.connection_link)) },
                        placeholder = { Text(stringResource(R.string.connection_link_placeholder)) },
                        supportingText = { Text(stringResource(if (linkInvalid) R.string.connection_link_invalid else R.string.connection_link_help)) },
                        isError = linkInvalid,
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(R.string.connection_link_connect, !busy && link.isNotBlank()) {
                        val target = parseConnectionLink(link)
                        linkInvalid = target == null
                        if (target != null) {
                            onLink(target)
                            backStack.add(ConnectionCheck)
                        }
                    }
                }
            }
            entry<ConnectionCheck> {
                ConnectionPage(
                    title = stringResource(R.string.startup_connection_check_title),
                    busy = busy,
                    onBack = if (backStack.size > 1) pop else changeTarget,
                ) {
                    ConnectionTargetSummary(address = address, peer = peer, genesisHash = genesisHash)
                    OrganizationIdentitySetupEntry(chairCanManageIdentity, busy, onOpenIdentitySetup)
                    when {
                        busy -> {
                            Text(stringResource(R.string.startup_connection_checking), style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        connected && failure == null -> {
                            Text(stringResource(R.string.startup_connection_success), style = MaterialTheme.typography.bodyMedium)
                            ActionButton(R.string.startup_connection_done, true) { onExit() }
                        }
                        failure != null -> {
                            Text(stringResource(R.string.startup_connection_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Text(failure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ActionButton(R.string.startup_connection_retry, true) { onConnect() }
                            ActionButton(R.string.startup_connection_change, true, kind = ActionKind.Secondary) { changeTarget() }
                        }
                        else -> {
                            Text(
                                stringResource(R.string.startup_connection_not_checked),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ActionButton(R.string.startup_connection_check, complete) { onConnect() }
                            ActionButton(R.string.startup_connection_change, true, kind = ActionKind.Secondary) { changeTarget() }
                        }
                    }
                }
            }
        },
    )
}

/** Chair-only identity work that must stay reachable before the organization connection exists. */
@Composable
private fun OrganizationIdentitySetupEntry(
    chairCanManageIdentity: Boolean,
    busy: Boolean,
    onOpen: (() -> Unit)?,
) {
    if (!chairCanManageIdentity || onOpen == null) return
    MenuEntry(
        label = stringResource(R.string.startup_connection_identity_manage),
        help = stringResource(R.string.startup_connection_identity_manage_help),
        enabled = !busy,
        onClick = onOpen,
    )
}

/** Shared shell for the connection steps: one task per page with a visible way back. */
@Composable
private fun ConnectionPage(
    title: String,
    busy: Boolean,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    ManagerScreenScaffold(title, busy, navigationIcon = { if (onBack != null) BackIcon(onBack) }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(SPACING_L),
                verticalArrangement = Arrangement.spacedBy(SPACING_L),
                content = content,
            )
        }
    }
}

@Composable
private fun IdentityReadyCard(identity: MemberIdentityView?, onRecheckIdentity: () -> Unit) {
    ManagerCard(
        title = stringResource(R.string.startup_connection_card_title),
        helper = stringResource(R.string.startup_connection_helper),
        statusText = stringResource(R.string.identity_current),
        statusActive = identity != null,
        emphasized = false,
    ) {
        if (identity != null) Text(
            stringResource(
                R.string.startup_connection_identity,
                identity.memberId,
                identity.clubs.joinToString(stringResource(R.string.governance_separator)),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRecheckIdentity, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.startup_connection_recheck_identity))
        }
    }
}

/** Selected business plus a short target summary; the full address/peer stay on the manual page. */
@Composable
private fun ConnectionTargetSummary(address: String, peer: String, genesisHash: String) {
    ManagerCard(
        title = stringResource(R.string.startup_connection_target_title),
        helper = stringResource(R.string.startup_connection_target_helper),
        statusActive = address.isNotBlank() && peer.isNotBlank() && GENESIS_HASH_PATTERN.matches(genesisHash),
        emphasized = false,
    ) {
        if (address.isBlank() || peer.isBlank() || !GENESIS_HASH_PATTERN.matches(genesisHash)) {
            Text(
                stringResource(R.string.startup_connection_target_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.startup_connection_target_peer, shortIdentifier(peer)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.startup_connection_target_ready), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}

@Composable
private fun ConnectionTargetFields(
    address: String,
    peer: String,
    genesisHash: String,
    busy: Boolean,
    onAddressChange: (String) -> Unit,
    onPeerChange: (String) -> Unit,
    onGenesisChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = address,
        onValueChange = onAddressChange,
        label = { Text(stringResource(R.string.connection_address)) },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = peer,
        onValueChange = onPeerChange,
        label = { Text(stringResource(R.string.connection_peer)) },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    val genesisInvalid = genesisHash.isNotBlank() && !GENESIS_HASH_PATTERN.matches(genesisHash)
    // The genesis hash is the longest field on this page; wrapping it keeps the tail visible.
    ChecksumField(
        value = genesisHash,
        label = stringResource(R.string.genesis_hash_label),
        enabled = !busy,
        onValueChange = onGenesisChange,
    )
    if (genesisInvalid) Text(
        stringResource(R.string.connection_genesis_invalid),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

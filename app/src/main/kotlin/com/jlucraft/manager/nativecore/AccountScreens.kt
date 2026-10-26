package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * Account root. It stays a short menu: every business below has its own page, so no
 * screen mixes identity, connection, administration and technical material.
 */
@Composable
internal fun AccountHomeNav(
    state: JSONObject?,
    busy: Boolean,
    identity: String,
    configured: Boolean,
    pushReady: Boolean?,
    onOpenIdentity: () -> Unit,
    onOpenIssuance: () -> Unit,
    onOpenPersonnel: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    val actor = officeActor(state, identity)
    val status = when {
        state != null -> R.string.office_workbench_status_connected
        configured -> R.string.office_workbench_status_disconnected
        else -> R.string.office_workbench_status_unconfigured
    }
    ManagerScreenScaffold(stringResource(R.string.account_title), busy) {
        // Settings is the second primary destination: the account status and the identity tasks sit
        // in the leading pane, the connection and technical tools in the trailing one. A compact
        // window keeps the previous stacked order through the shared master/detail helper.
        MasterDetailPage(
            master = {
                ManagerCard(
                    helper = stringResource(R.string.account_helper),
                    statusText = stringResource(status),
                    statusActive = state != null,
                    emphasized = false,
                ) {
                    Text(
                        if (actor.bound) stringResource(R.string.account_identity_line, actor.member) else stringResource(R.string.office_role_unbound),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MenuEntry(stringResource(R.string.account_menu_identity), stringResource(R.string.account_menu_identity_help), onClick = onOpenIdentity)
                MenuEntry(stringResource(R.string.identity_issuance_title), stringResource(R.string.account_menu_issuance_help), onClick = onOpenIssuance)
                MenuEntry(
                    stringResource(R.string.personnel_title),
                    stringResource(if (actor.admin) R.string.account_menu_personnel_help else R.string.personnel_restricted),
                    enabled = actor.admin,
                    onClick = onOpenPersonnel,
                )
            },
            detail = {
                MenuEntry(stringResource(R.string.connection_title), stringResource(R.string.account_menu_connection_help), onClick = onOpenConnection)
                MenuEntry(stringResource(R.string.device_identity_title), stringResource(R.string.account_menu_device_help), onClick = onOpenDevice)
                MenuEntry(
                    stringResource(R.string.business_admin_title),
                    stringResource(if (actor.admin) R.string.account_menu_admin_help else R.string.account_admin_restricted),
                    enabled = actor.admin,
                    onClick = onOpenAdmin,
                )
                MenuEntry(stringResource(R.string.audit_anchor_title), stringResource(R.string.account_menu_audit_help), enabled = state != null, onClick = onOpenAudit)
                MenuEntry(stringResource(R.string.help_who_title), stringResource(R.string.account_menu_help_help), onClick = onOpenHelp)
                pushReady?.let {
                    Text(
                        stringResource(if (it) R.string.office_push_ready else R.string.office_push_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/** The chair's issuance flow, with its own proposal state so target/review/import never mix. */
@Composable
internal fun AccountIssuancePage(
    config: String,
    device: String,
    busy: Boolean,
    directory: java.io.File,
    state: IdentityState,
    facts: JSONObject?,
    knownMembers: List<String>,
    current: Boolean,
    configured: Boolean,
    execute: (JSONObject, (JSONObject) -> Unit) -> Unit,
    onStateChange: (IdentityState, JSONObject?) -> Unit,
    onBack: () -> Unit,
    proposalState: androidx.compose.runtime.MutableState<String>? = null,
) {
    val latestExecute by rememberUpdatedState(execute)
    val latestStateChange by rememberUpdatedState(onStateChange)
    val proposal = proposalState
        ?: androidx.compose.runtime.saveable.rememberSaveable(config, device) { androidx.compose.runtime.mutableStateOf("") }
    val ops = remember(config, device) {
        IdentityOps(
            config = config,
            device = device,
            directory = directory,
            review = { text -> UnionNative.management(text) },
            execute = { input, reply -> latestExecute(input, reply) },
            onConfig = {},
            onStateChange = { next, nextFacts -> latestStateChange(next, nextFacts) },
            onBusinessGeneses = {},
            onFetchFailed = {},
            initialState = state,
            initialFacts = facts,
            proposalState = proposal,
        )
    }
    IssuanceFlowHost(ops, busy, current, configured, facts, knownMembers, onBack)
}

@Serializable internal data object AccountConnectionHome : NavKey
@Serializable internal data object AccountConnectionEdit : NavKey

/** Saved fields of a connection target; the page owns the candidate until an explicit check. */
private val ConnectionTargetSaver = listSaver<ConnectionTarget, String>(
    save = { listOf(it.address, it.peer, it.genesisHash) },
    restore = { ConnectionTarget(it[0], it[1], it[2]) },
)

private val OptionalConnectionTargetSaver = listSaver<ConnectionTarget?, String>(
    save = { target -> target?.let { listOf(it.address, it.peer, it.genesisHash) }.orEmpty() },
    restore = { values -> values.takeIf { it.size == 3 }?.let { ConnectionTarget(it[0], it[1], it[2]) } },
)

/**
 * Organization connection: a summary card first, then the same step-by-step connection host the startup
 * gate uses. The account page owns a saveable candidate: typing, discovery and link parsing only
 * change the candidate, so the committed connection and its snapshot stay untouched until an
 * explicit check applies the complete target once.
 */
@Composable
internal fun AccountConnectionPage(
    state: JSONObject?,
    identity: String,
    member: MemberIdentityView?,
    identityReady: Boolean,
    address: String,
    peer: String,
    genesisHash: String,
    connected: Boolean,
    busy: Boolean,
    failure: String?,
    onConnectTarget: (ConnectionTarget) -> Unit,
    onLink: (ConnectionTarget) -> Unit,
    discover: ((JSONObject) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val backStack = rememberNavBackStack(AccountConnectionHome)
    val candidate = rememberSaveable(stateSaver = ConnectionTargetSaver) {
        mutableStateOf(ConnectionTarget(address, peer, genesisHash))
    }
    // Only a target the member selected may show the connected result; an old snapshot
    // from another target never stands in for a candidate that was never checked.
    val checkedTarget = rememberSaveable(stateSaver = OptionalConnectionTargetSaver) {
        mutableStateOf<ConnectionTarget?>(null)
    }
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    val check: () -> Unit = {
        val target = candidate.value
        checkedTarget.value = target
        onConnectTarget(target)
    }
    val link: (ConnectionTarget) -> Unit = { target ->
        candidate.value = target
        checkedTarget.value = target
        onLink(target)
    }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<AccountConnectionHome> {
                TaskPage(stringResource(R.string.connection_title), busy, pop) {
                    state?.let { ProfileOverviewCard(it, identity) }
                    ManagerCard(
                        title = stringResource(R.string.connection_title),
                        helper = null,
                        statusText = stringResource(if (connected) R.string.connection_connected else R.string.connection_disconnected),
                        statusActive = connected,
                        emphasized = false,
                    ) {
                        if (connected) {
                            Text(stringResource(R.string.startup_connection_target_peer, shortIdentifier(peer)), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.connection_target_current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MenuEntry(
                            label = stringResource(R.string.account_connection_change),
                            help = stringResource(R.string.account_connection_change_help),
                            enabled = !busy,
                            onClick = { backStack.add(AccountConnectionEdit) },
                        )
                    }
                }
            }
            entry<AccountConnectionEdit> {
                ConnectionFlow(
                    identity = member,
                    identityReady = identityReady,
                    address = candidate.value.address,
                    peer = candidate.value.peer,
                    genesisHash = candidate.value.genesisHash,
                    busy = busy,
                    failure = failure,
                    connected = connected && checkedTarget.value == candidate.value,
                    onAddressChange = { candidate.value = candidate.value.copy(address = it); checkedTarget.value = null },
                    onPeerChange = { candidate.value = candidate.value.copy(peer = it); checkedTarget.value = null },
                    onGenesisChange = { candidate.value = candidate.value.copy(genesisHash = it); checkedTarget.value = null },
                    onConnect = check,
                    onLink = link,
                    discover = discover,
                    onRecheckIdentity = {},
                    onExit = pop,
                )
            }
        },
    )
}

/** Public device material: share, export and technical details only. */
@Composable
internal fun AccountDevicePage(publicIdentity: String, busy: Boolean, onBack: () -> Unit) {
    TaskPage(stringResource(R.string.device_identity_title), busy, onBack) {
        DeviceIdentityCard(publicIdentity)
    }
}

/**
 * Server administration is a write page; it never renders its forms without the admin slot.
 *
 * This is also the page whose submit button was hidden behind the software keyboard: the card body
 * scrolls inside the page, and the confirm-and-submit action is pinned above the keyboard and the
 * gesture bar.
 */
@Composable
internal fun AccountAdminPage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onBack: () -> Unit,
    submit: (JSONObject) -> Unit,
) {
    val admin = state?.optJSONArray("admins")?.let { admins ->
        (0 until admins.length()).any { index -> admins.optString(index) == identity }
    } == true
    val bottomAction = rememberBottomActionSlot()
    TaskPage(
        title = stringResource(R.string.business_admin_title),
        busy = busy,
        onBack = onBack,
        bottomBar = { bottomAction.action?.let { action -> BottomActionBar { action() } } },
    ) {
        if (!admin) AccountRestricted(R.string.account_admin_restricted)
        else BusinessAdminCard(state = state, device = identity, busy = busy, bottomAction = bottomAction, submit = submit)
    }
}

/** Audit anchors are readable with a connection; the submit form stays admin-only inside the card. */
@Composable
internal fun AccountAuditPage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onBack: () -> Unit,
    submit: (JSONObject) -> Unit,
) {
    TaskPage(stringResource(R.string.audit_anchor_title), busy, onBack) {
        if (state == null) AccountRestricted(R.string.audit_anchor_disconnected)
        else AuditAnchorCard(state, identity, busy, submit)
    }
}

/** Who to contact for a missing link, server or recovery. */
@Composable
internal fun AccountHelpPage(busy: Boolean, onBack: () -> Unit) {
    TaskPage(stringResource(R.string.help_who_title), busy, onBack) {
        // Three labelled groups instead of one undifferentiated block of lines: the reader can go
        // straight to the situation they are in.
        listOf(
            R.string.help_who_group_connection to listOf(
                R.string.help_who_need_link to R.string.help_who_contact_organizer,
            ),
            R.string.help_who_group_identity to listOf(
                R.string.help_who_need_server to R.string.help_who_contact_server_admin,
            ),
            R.string.help_who_group_device to listOf(
                R.string.help_who_need_recovery to R.string.help_who_contact_union_admin,
            ),
        ).forEach { (group, entries) ->
            SectionHeader(stringResource(group))
            ManagerCard(emphasized = false) {
                entries.forEachIndexed { index, (need, contact) ->
                    if (index > 0) HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
                        Text(stringResource(need), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(contact),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRestricted(text: Int) {
    ManagerCard(emphasized = false) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

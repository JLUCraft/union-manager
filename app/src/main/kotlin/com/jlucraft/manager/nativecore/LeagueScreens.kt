package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.serialization.Serializable
import org.json.JSONObject

/** League summary with one entry per business; no other league task is rendered on this page. */
@Composable
internal fun LeagueSummaryPage(
    state: JSONObject?,
    leagueId: String,
    busy: Boolean,
    onStart: (String) -> Unit,
    onOpenTeams: () -> Unit,
    onOpenDisputes: () -> Unit,
    onOpenSeason: () -> Unit,
    onOpenOps: () -> Unit,
    onBack: () -> Unit,
) {
    val item = state?.optJSONObject("leagues")?.optJSONObject(leagueId)
    if (item == null) {
        TaskPage(leagueId, busy, onBack) {
            Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val phase = item.getString("phase")
    val (phaseLabel, phaseActive) = phaseStatus(phase)
    val rules = item.getJSONObject("rules")
    val pendingDisputes = state?.optJSONObject("disputes")?.length() ?: 0
    TaskPage(leagueId, busy, onBack, scrollable = !isWideLayout()) {
        MasterDetailPage(
            master = {
        ManagerCard(
            title = leagueId,
            helper = stringResource(
                R.string.governance_rules,
                rules.optInt("seats"),
                stringResource(if (rules.optBoolean("cross_club")) R.string.league_cross_club else R.string.league_same_school),
            ),
            statusText = phaseLabel,
            statusActive = phaseActive,
        ) {
            Text(
                stringResource(R.string.league_team_count, item.optJSONObject("teams")?.length() ?: 0),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.league_dispute_pending_count, pendingDisputes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // An unresolved dispute blocks a clean draw, so its handling entry is the promoted action and
        // the irreversible lock-and-draw moves below the routine entries and stays confirmed.
        if (pendingDisputes > 0) ManagerCard(
            title = stringResource(R.string.league_disputes_need_attention),
            helper = stringResource(R.string.league_disputes_pending_help),
            emphasized = false,
        ) {
            ActionButton(R.string.league_handle_disputes, !busy, kind = ActionKind.Primary) { onOpenDisputes() }
        }
            },
            detail = {
        ManagerCard(emphasized = false) {
            MenuEntry(stringResource(R.string.league_manage_teams), enabled = !busy, onClick = onOpenTeams)
            if (pendingDisputes == 0) {
                MenuEntry(stringResource(R.string.league_manage_disputes), enabled = !busy, onClick = onOpenDisputes)
            }
            MenuEntry(stringResource(R.string.league_manage_season), enabled = !busy, onClick = onOpenSeason)
            MenuEntry(stringResource(R.string.league_manage_ops), stringResource(R.string.league_manage_ops_help), enabled = !busy, onClick = onOpenOps)
        }
        if (phase == "Registration") ManagerCard(
            title = stringResource(R.string.league_lock_title),
            helper = stringResource(R.string.league_lock_help),
            emphasized = false,
        ) {
            if (pendingDisputes > 0) Text(
                stringResource(R.string.league_lock_blocked_disputes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            ActionButton(
                label = R.string.league_lock_and_start,
                enabled = !busy && pendingDisputes == 0,
                kind = ActionKind.Confirmed,
                confirmText = R.string.league_lock_confirm,
            ) { onStart(leagueId) }
        }
            },
        )
    }
}

/** Teams and both scoreboards, with the registration lock for a league that is still filling. */
@Composable
internal fun LeagueTeamsPage(
    state: JSONObject?,
    leagueId: String,
    busy: Boolean,
    onStart: (String) -> Unit,
    onBack: () -> Unit,
) {
    val item = state?.optJSONObject("leagues")?.optJSONObject(leagueId)
    if (item == null) {
        TaskPage(stringResource(R.string.league_teams_title), busy, onBack) {
            Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    TaskPage(stringResource(R.string.league_teams_title), busy, onBack) {
        Text(stringResource(R.string.league_teams_title), style = MaterialTheme.typography.titleSmall)
        val teams = item.getJSONObject("teams")
        if (teams.length() == 0) {
            Text(
                stringResource(R.string.league_no_teams),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            teams.keys().asSequence().toList().sorted().forEach { team ->
                key(team) { TeamRow(team = team, roster = teams.getJSONObject(team)) }
            }
        }
        if (item.getString("phase") == "Registration") {
            ActionButton(
                label = R.string.league_lock_and_start,
                enabled = !busy,
                kind = ActionKind.Confirmed,
                confirmText = R.string.league_lock_confirm,
            ) { onStart(leagueId) }
        }
        HorizontalDivider()
        Scoreboard(stringResource(R.string.league_team_scoreboard), item.getJSONObject("team_scores"))
        Scoreboard(stringResource(R.string.league_player_scoreboard), item.getJSONObject("player_scores"))
    }
}

/** Dispute summaries; each row opens the dedicated resolution page. */
@Composable
internal fun LeagueDisputesPage(
    state: JSONObject?,
    busy: Boolean,
    onOpenDispute: (String) -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.league_manage_disputes), busy, onBack) {
        if (state == null) {
            Text(stringResource(R.string.league_list_disconnected), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        DisputeListCard(state, busy, onOpenDispute)
    }
}

/** One dispute: reason, current resolution or the resolution form. */
@Composable
internal fun LeagueDisputeDetailPage(
    state: JSONObject?,
    disputeId: String,
    busy: Boolean,
    request: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.league_dispute_title), busy, onBack) {
        if (state == null) {
            Text(stringResource(R.string.league_list_disconnected), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        DisputeDetailCard(state, disputeId, busy, request)
    }
}

/** Season summary plus the two explicit season tasks; the dispute list is its own page. */
@Composable
internal fun LeagueSeasonPage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onCreateSeason: () -> Unit,
    onSchedule: () -> Unit,
    request: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.league_manage_season), busy, onBack) {
        if (state == null) {
            Text(stringResource(R.string.league_list_disconnected), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        SeasonCard(
            state = state,
            identity = identity,
            busy = busy,
            onOpenCreateSeason = onCreateSeason,
            onOpenSchedule = onSchedule,
            request = request,
        )
    }
}

/** Create one season; no schedule fields here. */
@Composable
internal fun LeagueSeasonCreatePage(
    state: JSONObject?,
    busy: Boolean,
    request: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    var season by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    // The season id is authored here, not selected from a list: this page creates a new season, so a
    // picker over the existing ids would only ever offer ids that already exist.
    val seasonTaken = state?.optJSONObject("seasons")?.has(season.trim()) == true
    TaskPage(stringResource(R.string.governance_create_season), busy, onBack) {
        OutlinedTextField(
            value = season,
            onValueChange = { season = it },
            label = { Text(stringResource(R.string.governance_season_id)) },
            enabled = !busy,
            singleLine = true,
            isError = seasonTaken,
            supportingText = if (seasonTaken) ({ Text(stringResource(R.string.governance_season_id_taken)) }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.governance_season_name)) },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ActionButton(
            label = R.string.governance_create_season,
            enabled = !busy && season.isNotBlank() && title.isNotBlank() && !seasonTaken,
        ) { request(JSONObject().put("type", "create_season").put("id", season.trim()).put("title", title)) }
    }
}

/** Schedule one league inside a season; the date order is validated before the action is offered. */
@Composable
internal fun LeagueSchedulePage(
    state: JSONObject?,
    busy: Boolean,
    request: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    var season by remember { mutableStateOf("") }
    var league by remember { mutableStateOf("") }
    var opens by remember { mutableStateOf("") }
    var closes by remember { mutableStateOf("") }
    var starts by remember { mutableStateOf("") }
    val seasonIds = state?.optJSONObject("seasons")?.keys()?.asSequence()?.toList().orEmpty()
    val leagueIds = state?.optJSONObject("leagues")?.keys()?.asSequence()?.toList().orEmpty()
    val opensTime = parseLocalDateTime(opens)
    val closesTime = parseLocalDateTime(closes)
    val startsTime = parseLocalDateTime(starts)
    TaskPage(stringResource(R.string.governance_schedule_league), busy, onBack) {
        // Both fields choose an existing record, so both read as a selection of that record.
        ChoiceField(R.string.governance_schedule_season, season, seasonIds, !busy) { season = it }
        ChoiceField(R.string.governance_schedule_league_select, league, leagueIds, !busy) { league = it }
        DateTimeField(opens, stringResource(R.string.governance_registration_opens), !busy) { opens = it }
        DateTimeField(closes, stringResource(R.string.governance_registration_closes), !busy) { closes = it }
        DateTimeField(starts, stringResource(R.string.governance_match_starts), !busy) { starts = it }
        ActionButton(
            label = R.string.governance_schedule_league,
            enabled = !busy && season.isNotBlank() && league.isNotBlank() && opensTime != null &&
                closesTime != null && startsTime != null && opensTime < closesTime && closesTime <= startsTime,
        ) {
            request(
                JSONObject().put("type", "schedule_league").put("season", season).put("league", league)
                    .put("schedule", JSONObject().put("opens_ms", opensTime).put("closes_ms", closesTime).put("starts_ms", startsTime)),
            )
        }
    }
}

@Serializable internal data object LeagueOpsTargetKey : NavKey
@Serializable internal data object LeagueOpsActionsKey : NavKey

/** The entered target survives lock/reentry; console state, busy and replies stay in memory. */
private val GameOpsTargetSaver = listSaver<GameOpsTarget, String>(
    save = { listOf(it.address, it.peer, it.service) },
    restore = { GameOpsTarget(it[0], it[1], it[2]) },
)

/**
 * Match operations split into the connection target and the operation console. The console is
 * unreachable until the complete target is known, and every reply stays on its own page.
 */
@Composable
internal fun LeagueOpsFlow(
    execute: (JSONObject, (JSONObject) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var target by rememberSaveable(stateSaver = GameOpsTargetSaver) { mutableStateOf(GameOpsTarget()) }
    var ops by remember { mutableStateOf(GameOpsState()) }
    var busy by remember { mutableStateOf(false) }
    fun run(operation: String) {
        val request = gameOpsRequest(target, operation, ops.selected)
        ops = ops.copy(logs = null, failed = false)
        busy = true
        execute(request) { value ->
            busy = false
            ops = try {
                ops.receive(operation, gameOpsResult(value))
            } catch (_: Exception) {
                ops.copy(logs = null, failed = true)
            }
        }
    }
    val backStack = rememberNavBackStack(LeagueOpsTargetKey)
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<LeagueOpsTargetKey> {
                TaskPage(stringResource(R.string.league_ops_target), busy, pop) {
                    OutlinedTextField(
                        value = target.address,
                        onValueChange = { target = target.copy(address = it); ops = GameOpsState() },
                        label = { Text(stringResource(R.string.ops_address)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = target.peer,
                        onValueChange = { target = target.copy(peer = it); ops = GameOpsState() },
                        label = { Text(stringResource(R.string.ops_peer)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = target.service,
                        onValueChange = { target = target.copy(service = it); ops = GameOpsState() },
                        label = { Text(stringResource(R.string.assistant_service)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(R.string.ops_refresh, !busy && target.complete) { run("list_instances") }
                    if (ops.failed) Text(stringResource(R.string.ops_error), color = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.league_ops_instance_count, ops.instances.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ActionButton(R.string.league_ops_open, !busy && target.complete) { backStack.add(LeagueOpsActionsKey) }
                }
            }
            entry<LeagueOpsActionsKey> {
                TaskPage(stringResource(R.string.league_ops_operations), busy, pop) {
                    GameOpsConsole(target, ops, busy, onSelect = { ops = ops.select(it) }, onRun = { run(it) })
                }
            }
        },
    )
}

/** Instance selection and the operation buttons. */
@Composable
private fun GameOpsConsole(
    target: GameOpsTarget,
    state: GameOpsState,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onRun: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val enabled = !busy && target.complete
    if (state.instances.isEmpty() && !busy) {
        // The empty state's only useful action is the refresh, so it is promoted to the solid
        // weight instead of being the small borderless link it used to be.
        ManagerCard(
            title = stringResource(R.string.ops_empty),
            helper = stringResource(R.string.ops_empty_help),
            emphasized = false,
        ) {
            ActionButton(R.string.ops_refresh, enabled, kind = ActionKind.Primary) { onRun("list_instances") }
        }
        if (state.failed) Text(stringResource(R.string.ops_error), color = MaterialTheme.colorScheme.error)
        return
    }
    Box {
        OutlinedButton(onClick = { menu = true }, enabled = enabled && state.instances.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
            Text(state.selected.ifEmpty { stringResource(R.string.ops_select) })
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            state.instances.forEach { instance ->
                key(instance.id) {
                    DropdownMenuItem(text = { Text(instance.id) }, onClick = { onSelect(instance.id); menu = false })
                }
            }
        }
    }
    state.instances.firstOrNull { it.id == state.selected }?.let { instance ->
        val stateLabel = when (instance.state) {
            "stopped" -> R.string.ops_stopped
            "starting" -> R.string.ops_starting
            "running" -> R.string.ops_running
            "stopping" -> R.string.ops_stopping
            "busy" -> R.string.ops_busy
            else -> R.string.ops_error_state
        }
        if (instance.version.isBlank()) {
            Text(stringResource(stateLabel))
        } else {
            Text(stringResource(R.string.ops_instance_status, stringResource(stateLabel), instance.version, instance.online, instance.maximum))
        }
    }
    val actions = listOf(
        "status" to R.string.ops_status, "start" to R.string.ops_start, "stop" to R.string.ops_stop,
        "restart" to R.string.ops_restart, "kill" to R.string.ops_kill, "logs" to R.string.ops_logs,
    )
    // One filled action per card (refresh); operations stay outlined, kill is destructive with confirmation.
    actions.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (op, label) ->
                ActionButton(
                    label = label,
                    enabled = enabled && state.selected.isNotBlank(),
                    kind = if (op == "kill") ActionKind.Destructive else ActionKind.Secondary,
                    modifier = Modifier.weight(1f),
                ) { onRun(op) }
            }
        }
    }
    if (state.failed) Text(stringResource(R.string.ops_error), color = MaterialTheme.colorScheme.error)
    state.logs?.takeIf { it.isNotEmpty() }?.let { logs ->
        DetailSurface {
            SelectionContainer {
                Text(logs, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
    TextButton(onClick = { onRun("list_instances") }, enabled = enabled, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
        Text(stringResource(R.string.ops_refresh))
    }
}

/** Host/scorer identities are base58-encoded peer IDs handed out by the organizer. */
internal val IDENTITY_ID_PATTERN = Regex("[1-9A-HJ-NP-Za-km-z]{32,64}")

private const val LEAGUE_RECONNECT_MIN_MS = 15_000L
private const val LEAGUE_RECONNECT_MAX_MS = 90_000L

@Serializable internal data object LeagueCreateBasics : NavKey
@Serializable internal data object LeagueCreateHosts : NavKey
@Serializable internal data object LeagueCreateConfirm : NavKey

/**
 * New league in three pages: name and rules, host/scorer identities, then one explicit confirmation.
 * The fields are owned by the activity, so the steps only navigate and never copy the request.
 */
@Composable
internal fun LeagueCreateFlow(
    league: String,
    seats: String,
    crossClub: Boolean,
    host: String,
    scorer: String,
    connected: Boolean,
    busy: Boolean,
    seatsValid: Boolean,
    nameTaken: Boolean,
    identityOptions: List<String>,
    onLeagueChange: (String) -> Unit,
    onSeatsChange: (String) -> Unit,
    onCrossClubChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onScorerChange: (String) -> Unit,
    send: FormSender,
    onCreated: () -> Unit,
    onBack: () -> Unit,
) {
    val submission = rememberFormSubmission()
    val hostInvalid = host.isNotBlank() && !IDENTITY_ID_PATTERN.matches(host.trim())
    val scorerInvalid = scorer.isNotBlank() && !IDENTITY_ID_PATTERN.matches(scorer.trim())
    val hostsValid = host.isNotBlank() && scorer.isNotBlank() && !hostInvalid && !scorerInvalid
    val basicsValid = league.isNotBlank() && !nameTaken && seatsValid
    val backStack = rememberNavBackStack(LeagueCreateBasics)
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<LeagueCreateBasics> {
                TaskPage(stringResource(R.string.governance_create_league), busy, pop) {
                    StepProgress(step = 1, total = 3)
                    if (!connected) Text(
                        stringResource(R.string.governance_create_league_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = league,
                        onValueChange = onLeagueChange,
                        label = { Text(stringResource(R.string.governance_league_name)) },
                        singleLine = true,
                        enabled = !busy,
                        isError = nameTaken,
                        supportingText = if (nameTaken) ({ Text(stringResource(R.string.governance_league_name_taken)) }) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ChoiceField(R.string.governance_seats_label, seats, listOf("2", "3", "4", "5", "6"), !busy, onSeatsChange)
                    if (!seatsValid) Text(stringResource(R.string.governance_seats_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = crossClub, enabled = !busy, onValueChange = onCrossClubChange, role = Role.Checkbox),
                    ) {
                        Checkbox(checked = crossClub, onCheckedChange = null, enabled = !busy)
                        Spacer(modifier = Modifier.width(SPACING_S))
                        Text(stringResource(R.string.league_cross_club), style = MaterialTheme.typography.bodyLarge)
                    }
                    ActionButton(R.string.office_next, !busy && basicsValid) { backStack.add(LeagueCreateHosts) }
                }
            }
            entry<LeagueCreateHosts> {
                TaskPage(stringResource(R.string.governance_create_league), busy, pop) {
                    StepProgress(step = 2, total = 3)
                    Text(
                        stringResource(R.string.league_advanced_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceField(R.string.league_host_identity, host, identityOptions, !busy, onHostChange)
                    if (hostInvalid) Text(stringResource(R.string.league_identity_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    ChoiceField(R.string.league_scorer_identity, scorer, identityOptions, !busy, onScorerChange)
                    if (scorerInvalid) Text(stringResource(R.string.league_identity_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    // The prompt only explains what is still missing; once both identities are filled
                    // and the derived validity is true there is nothing left to ask for, so it is
                    // hidden instead of contradicting the enabled next step.
                    if (!hostsValid) Text(
                        stringResource(R.string.league_create_need_identities),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ActionButton(R.string.office_next, !busy && hostsValid) { backStack.add(LeagueCreateConfirm) }
                    TextButton(onClick = pop, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.workflow_back))
                    }
                }
            }
            entry<LeagueCreateConfirm> {
                TaskPage(stringResource(R.string.governance_create_league), busy, pop) {
                    StepProgress(step = 3, total = 3)
                    ManagerCard(
                        title = league,
                        helper = stringResource(R.string.league_create_confirm_help),
                        emphasized = false,
                    ) {
                        Text(
                            stringResource(
                                R.string.governance_rules,
                                seats.toIntOrNull() ?: 0,
                                stringResource(if (crossClub) R.string.league_cross_club else R.string.league_same_school),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(stringResource(R.string.league_host_identity), style = MaterialTheme.typography.labelMedium)
                        Text(shortIdentifier(host.trim()), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.league_scorer_identity), style = MaterialTheme.typography.labelMedium)
                        Text(shortIdentifier(scorer.trim()), style = MaterialTheme.typography.bodyMedium)
                    }
                    val request = JSONObject().put("type", "create_league")
                        .put("league", league)
                        .put("host", host)
                        .put("scorer", scorer)
                        .put(
                            "rules",
                            JSONObject().put("cross_club", crossClub)
                                .put("seats", seats.toIntOrNull() ?: 0)
                                .put("reconnect_min_ms", LEAGUE_RECONNECT_MIN_MS)
                                .put("reconnect_max_ms", LEAGUE_RECONNECT_MAX_MS),
                        )
                    ActionButton(R.string.league_create_confirm_action, !busy && connected && basicsValid && hostsValid && !submission.pending) {
                        // The page closes only when the accepted snapshot already contains this league.
                        submission.submit(send, request) { snapshot ->
                            if (snapshot.optJSONObject("leagues")?.has(league.trim()) == true) {
                                onCreated()
                                true
                            } else false
                        }
                    }
                    submission.failure?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = pop, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.workflow_back))
                    }
                }
            }
        },
    )
}

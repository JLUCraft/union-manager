package com.jlucraft.console.ui.screens.league

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.auth.AuthStateHolder
import com.jlucraft.console.data.auth.ReadOnlyMode
import com.jlucraft.console.data.model.DisputeMatch
import com.jlucraft.console.data.model.Leaderboard
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.MatchStatus
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.model.TournamentStatus
import com.jlucraft.console.data.model.statusText
import com.jlucraft.console.data.model.toShortDate
import com.jlucraft.console.data.model.truncate
import com.jlucraft.console.ui.components.statusColor
import com.jlucraft.console.ui.components.DetailRow
import com.jlucraft.console.ui.components.ReadOnlyModeBanner
import com.jlucraft.console.ui.components.StatusChip
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.*
import com.jlucraft.console.viewmodel.LeagueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(
    services: AppServices,
    viewModel: LeagueViewModel = viewModel(),
) {
    val state = viewModel.uiState.value
    val readOnlyState by AuthStateHolder.readOnlyMode.collectAsState()
    val isReadOnly = readOnlyState is ReadOnlyMode.ReadOnly

    when {
        state.selectedTournament != null -> TournamentDetailScreen(
            tournament = state.selectedTournament,
            matches = state.matches,
            teams = state.teams,
            isLoading = state.detailLoading,
            error = state.detailError,
            onBack = { viewModel.clearSelection() },
            onStatusChange = { viewModel.updateTournamentStatus(it) },
            onPauseMatch = { viewModel.pauseMatch(it) },
            onResumeMatch = { viewModel.resumeMatch(it) },
            onResetMatch = { viewModel.resetMatch(it) },
            onJudgeMatch = { matchId, winnerId, reason -> viewModel.judgeMatch(matchId, winnerId, reason) },
            disputes = state.disputes,
            disputesLoading = state.disputesLoading,
            disputesError = state.disputesError,
            onSelectDispute = { viewModel.selectDispute(it) },
            onCreateDispute = { viewModel.showCreateDisputeDialog() },
            seasons = state.seasons,
            seasonLoading = state.seasonLoading,
            seasonError = state.seasonError,
            onArchiveSeason = { viewModel.archiveSelectedSeason() },
            onCopySeasonTemplate = { viewModel.copySeasonTemplate(it) },
            onSelectSeason = { viewModel.selectSeason(it) },
            selectedSeason = state.selectedSeason,
            leaderboard = state.leaderboard,
            readOnlyMode = readOnlyState
        )
        else -> TournamentListScreen(
            state = state,
            onRefresh = { viewModel.refresh() },
            onShowCreate = { viewModel.showCreateDialog() },
            onSelectTournament = { viewModel.selectTournament(it) },
            onDismissCreate = { viewModel.dismissCreateDialog() },
            onCreate = { name, gameType, mode, maxParticipants, minMemberScore, regOpen, regClose ->
                viewModel.createTournament(name, gameType, mode, maxParticipants, minMemberScore, regOpen, regClose)
            },
            readOnlyMode = readOnlyState
        )
    }


    if (state.showCreateDisputeDialog && state.selectedTournament != null) {
        CreateDisputeDialog(
            matches = state.matches,
            onDismiss = { viewModel.dismissCreateDisputeDialog() },
            onCreate = { matchId, reason, evidence ->
                viewModel.createDispute(matchId, reason, evidence)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TournamentListScreen(
    state: com.jlucraft.console.viewmodel.LeagueUiState,
    onRefresh: () -> Unit,
    onShowCreate: () -> Unit,
    onSelectTournament: (Tournament) -> Unit,
    onDismissCreate: () -> Unit,
    onCreate: (String, String, String, Int, Int, String, String) -> Unit,
    readOnlyMode: ReadOnlyMode
) {
    val tournaments = state.tournaments
    val statusCounts = remember(tournaments) {
        tournaments.groupingBy { it.status }.eachCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("联赛系统") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { onRefresh() }) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (readOnlyMode !is ReadOnlyMode.ReadOnly) {
                FloatingActionButton(onClick = { onShowCreate() }) {
                    Icon(Icons.Default.Add, contentDescription = "创建赛事")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (readOnlyMode is ReadOnlyMode.ReadOnly) {
                item {
                    ReadOnlyModeBanner(readOnlyMode)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusChip("进行中", statusCounts[TournamentStatus.Ongoing].toString(), MaterialTheme.colorScheme.primary)
                    StatusChip("报名中", statusCounts[TournamentStatus.Registration].toString(), StatusGreen)
                    StatusChip("已结束", statusCounts[TournamentStatus.Completed].toString(), MaterialTheme.colorScheme.outline)
                    StatusChip("草稿", statusCounts[TournamentStatus.Draft].toString(), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Text(
                    text = "赛事列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            listStatePlaceholders(
                isLoading = state.isLoading,
                error = state.error,
                isEmpty = tournaments.isEmpty(),
                emptyText = "暂无赛事"
            )

            items(tournaments, key = { it.id }) { tournament ->
                TournamentCard(tournament, onClick = { onSelectTournament(tournament) })
            }
        }
    }

    if (state.showCreateDialog) {
        CreateTournamentDialog(
            onDismiss = { onDismissCreate() },
            onCreate = onCreate,
            error = state.createError
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Int, Int, String, String) -> Unit,
    error: String?
) {
    var step by rememberSaveable { mutableStateOf(1) }

    var name by rememberSaveable { mutableStateOf("") }
    var gameType by rememberSaveable { mutableStateOf("skywars") }

    var mode by rememberSaveable { mutableStateOf("solo") }

    var winScore by rememberSaveable { mutableStateOf("10") }
    var killScore by rememberSaveable { mutableStateOf("2") }
    var surviveScore by rememberSaveable { mutableStateOf("0.5") }

    var maxParticipants by rememberSaveable { mutableStateOf("32") }
    var minMemberScore by rememberSaveable { mutableStateOf("0") }
    var regOpen by rememberSaveable { mutableStateOf("") }
    var regClose by rememberSaveable { mutableStateOf("") }

    val gameTypes = remember {
        listOf(
            "skywars" to "空岛战争",
            "bedwars" to "起床战争",
            "parkour" to "跑酷",
            "build-battle" to "建筑大战"
        )
    }
    val modes = remember {
        listOf(
            "solo" to "单人",
            "duo" to "双人",
            "team-4" to "四人组队"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建赛事 — 第 $step 步") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("基本信息", "赛制", "积分", "报名", "预览").forEachIndexed { i, label ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (i + 1 <= step) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                when (step) {
                    1 -> {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("赛事名称") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        var gameTypeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = gameTypeExpanded, onExpandedChange = { gameTypeExpanded = it }) {
                            OutlinedTextField(
                                value = gameTypes.find { it.first == gameType }?.second ?: gameType,
                                onValueChange = {}, readOnly = true,
                                label = { Text("游戏类型") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(gameTypeExpanded) }
                            )
                            ExposedDropdownMenu(expanded = gameTypeExpanded, onDismissRequest = { gameTypeExpanded = false }) {
                                gameTypes.forEach { (value, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { gameType = value; gameTypeExpanded = false })
                                }
                            }
                        }
                    }
                    2 -> {
                        var modeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = it }) {
                            OutlinedTextField(
                                value = modes.find { it.first == mode }?.second ?: mode,
                                onValueChange = {}, readOnly = true,
                                label = { Text("赛制") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) }
                            )
                            ExposedDropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                                modes.forEach { (value, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { mode = value; modeExpanded = false })
                                }
                            }
                        }
                        Text("地图将在赛程生成时自动分配", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    3 -> {
                        OutlinedTextField(value = winScore, onValueChange = { s -> winScore = s.filter { it.isDigit() } },
                            label = { Text("胜利积分") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = killScore, onValueChange = { s -> killScore = s.filter { it.isDigit() } },
                            label = { Text("击杀积分") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = surviveScore, onValueChange = { s -> surviveScore = s.filter { it == '.' || it.isDigit() } },
                            label = { Text("每分钟存活积分") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    4 -> {
                        OutlinedTextField(value = maxParticipants, onValueChange = { s -> maxParticipants = s.filter { it.isDigit() } },
                            label = { Text("最大参赛人数") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = minMemberScore, onValueChange = { s -> minMemberScore = s.filter { it.isDigit() } },
                            label = { Text("最低积分门槛") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = regOpen, onValueChange = { regOpen = it },
                            label = { Text("报名开始 (YYYY-MM-DDTHH:mm:ss)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = regClose, onValueChange = { regClose = it },
                            label = { Text("报名截止 (YYYY-MM-DDTHH:mm:ss)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    5 -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("名称", style = MaterialTheme.typography.labelMedium)
                                    Text(name.ifBlank { "未填写" }, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("类型", style = MaterialTheme.typography.labelMedium)
                                    Text(gameTypes.find { it.first == gameType }?.second ?: gameType)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("赛制", style = MaterialTheme.typography.labelMedium)
                                    Text(modes.find { it.first == mode }?.second ?: mode)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("积分", style = MaterialTheme.typography.labelMedium)
                                    Text("胜${winScore} / 杀${killScore} / 存活${surviveScore}")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("参赛上限", style = MaterialTheme.typography.labelMedium)
                                    Text("$maxParticipants 人")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("积分门槛", style = MaterialTheme.typography.labelMedium)
                                    Text(minMemberScore)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("报名时间", style = MaterialTheme.typography.labelMedium)
                                    Text("${regOpen.ifBlank { "未设置" }} → ${regClose.ifBlank { "未设置" }}")
                                }
                            }
                        }
                    }
                }

                if (error != null) {
                    Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 1) {
                    TextButton(onClick = { step-- }) { Text("上一步") }
                }
                if (step < 5) {
                    TextButton(
                        onClick = { step++ },
                        enabled = when (step) {
                            1 -> name.isNotBlank()
                            4 -> regOpen.isNotBlank() && regClose.isNotBlank() && (maxParticipants.toIntOrNull() ?: 0) > 0
                            else -> true
                        }
                    ) { Text("下一步") }
                } else {
                    TextButton(
                        onClick = {
                            val max = maxParticipants.toIntOrNull() ?: 0
                            val min = minMemberScore.toIntOrNull() ?: 0
                            onCreate(name, gameType, mode, max, min, regOpen, regClose)
                        },
                        enabled = name.isNotBlank() && regOpen.isNotBlank() && regClose.isNotBlank() && (maxParticipants.toIntOrNull() ?: 0) > 0
                    ) { Text("创建") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TournamentDetailScreen(
    tournament: Tournament,
    matches: List<Match>,
    teams: List<com.jlucraft.console.data.model.Team>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStatusChange: (TournamentStatus) -> Unit,
    onPauseMatch: (String) -> Unit = {},
    onResumeMatch: (String) -> Unit = {},
    onResetMatch: (String) -> Unit = {},
    onJudgeMatch: (matchId: String, winnerId: String, reason: String) -> Unit = { _, _, _ -> },
    disputes: List<DisputeMatch> = emptyList(),
    disputesLoading: Boolean = false,
    disputesError: String? = null,
    onSelectDispute: (DisputeMatch) -> Unit = {},
    onCreateDispute: () -> Unit = {},
    seasons: List<Season> = emptyList(),
    seasonLoading: Boolean = false,
    seasonError: String? = null,
    onArchiveSeason: () -> Unit = {},
    onCopySeasonTemplate: (String) -> Unit = {},
    onSelectSeason: (Season) -> Unit = {},
    selectedSeason: Season? = null,
    leaderboard: Leaderboard? = null,
    readOnlyMode: ReadOnlyMode
) {
    val isReadOnly = readOnlyMode is ReadOnlyMode.ReadOnly
    val nextStatus = when (tournament.status) {
        TournamentStatus.Draft -> TournamentStatus.Registration to "开放报名"
        TournamentStatus.Registration -> TournamentStatus.Ongoing to "开始比赛"
        TournamentStatus.Ongoing -> TournamentStatus.Completed to "结束赛事"
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tournament.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isReadOnly) {
                item {
                    ReadOnlyModeBanner(readOnlyMode)
                }
            }


            item {
                val statusColor = tournament.statusColor()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "赛事",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tournament.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = tournament.statusText(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        DetailRow("游戏类型", tournament.game_type)
                        DetailRow("赛制", tournament.mode)
                        DetailRow("报名人数", "${tournament.participant_count}/${tournament.max_participants}")
                        DetailRow("最低积分", tournament.min_member_score.toString())
                        DetailRow("创建时间", tournament.created_at.toShortDate())

                        if (nextStatus != null && !isReadOnly) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onStatusChange(nextStatus.first) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(nextStatus.second)
                            }
                        }

                        if (error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }


            if (matches.isNotEmpty()) {
                item {
                    Text(
                        text = "比赛 (${matches.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(matches, key = { it.id }) { match ->
                    MatchOperationsCard(
                        match = match,
                        readOnly = isReadOnly,
                        onPause = onPauseMatch,
                        onResume = onResumeMatch,
                        onReset = onResetMatch,
                        onJudge = onJudgeMatch
                    )
                }
            }


            item {
                DisputeListSection(
                    disputes = disputes,
                    isLoading = disputesLoading,
                    error = disputesError,
                    onSelect = onSelectDispute,
                    onCreateDispute = onCreateDispute,
                    readOnly = isReadOnly
                )
            }


            item {
                SeasonManagementSection(
                    seasons = seasons,
                    isLoading = seasonLoading,
                    error = seasonError,
                    selectedSeason = selectedSeason,
                    leaderboard = leaderboard,
                    onArchive = onArchiveSeason,
                    onCopyTemplate = onCopySeasonTemplate,
                    onSelectSeason = onSelectSeason,
                    readOnly = isReadOnly
                )
            }


            if (teams.isNotEmpty()) {
                item {
                    Text(
                        text = "队伍 (${teams.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(teams, key = { it.id }) { team ->
                    TeamCard(team)
                }
            }
        }
    }
}



@Composable
fun DisputeListSection(
    disputes: List<DisputeMatch>,
    isLoading: Boolean,
    error: String?,
    onSelect: (DisputeMatch) -> Unit,
    onCreateDispute: () -> Unit,
    readOnly: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "争议 (${disputes.size})",
                style = MaterialTheme.typography.titleMedium
            )
            if (!readOnly) {
                TextButton(onClick = onCreateDispute) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("提交争议")
                }
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Text(
                    text = "加载争议失败: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            disputes.isEmpty() -> {
                Text(
                    text = "暂无争议",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            else -> {
                disputes.forEach { dispute ->
                    DisputeCard(dispute, onSelect)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DisputeCard(dispute: DisputeMatch, onSelect: (DisputeMatch) -> Unit) {
    val statusColor = when (dispute.status) {
        "open" -> StatusAmber
        "under_review" -> MaterialTheme.colorScheme.primary
        "resolved" -> StatusGreen
        "dismissed" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = { onSelect(dispute) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "比赛 ${dispute.matchId.truncate(12)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = dispute.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "原因: ${dispute.reason}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "提出者: ${dispute.raisedBy.truncate(16)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDisputeDialog(
    matches: List<Match>,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>) -> Unit
) {
    var selectedMatchId by rememberSaveable { mutableStateOf(matches.firstOrNull()?.id ?: "") }
    var reason by rememberSaveable { mutableStateOf("") }
    var evidence by rememberSaveable { mutableStateOf("") }
    var matchExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交争议") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = matchExpanded,
                    onExpandedChange = { matchExpanded = it }
                ) {
                    OutlinedTextField(
                        value = matches.find { it.id == selectedMatchId }?.let { "第${it.round}轮" } ?: "选择比赛",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("比赛") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(matchExpanded) }
                    )
                    ExposedDropdownMenu(expanded = matchExpanded, onDismissRequest = { matchExpanded = false }) {
                        matches.forEach { match ->
                            DropdownMenuItem(
                                text = { Text("第${match.round}轮 · ${match.statusText()}") },
                                onClick = { selectedMatchId = match.id; matchExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("争议原因") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = evidence,
                    onValueChange = { evidence = it },
                    label = { Text("证据（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val evidenceList = if (evidence.isNotBlank()) listOf(evidence) else emptyList()
                    onCreate(selectedMatchId, reason, evidenceList)
                },
                enabled = selectedMatchId.isNotBlank() && reason.isNotBlank()
            ) {
                Text("提交")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun MatchOperationsCard(
    match: Match,
    readOnly: Boolean,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onReset: (String) -> Unit,
    onJudge: (matchId: String, winnerId: String, reason: String) -> Unit
) {
    var showJudgeDialog by remember { mutableStateOf(false) }

    if (showJudgeDialog) {
        JudgeMatchDialog(
            match = match,
            onDismiss = { showJudgeDialog = false },
            onConfirm = { winnerId, reason ->
                onJudge(match.id, winnerId, reason)
                showJudgeDialog = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("第 ${match.round} 轮", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "状态: ${match.statusText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "时间: ${match.scheduled_at.toShortDate()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "参赛者: ${match.participants.size} 人",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!readOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (match.status == MatchStatus.Live) {
                        OutlinedButton(
                            onClick = { onPause(match.id) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("暂停", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (match.status == MatchStatus.Scheduled || match.status == MatchStatus.Live) {
                        OutlinedButton(
                            onClick = { onResume(match.id) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("恢复", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (match.status == MatchStatus.Live || match.status == MatchStatus.Finished) {
                        OutlinedButton(
                            onClick = { onReset(match.id) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("重置", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (match.status == MatchStatus.Disputed || match.status == MatchStatus.Live || match.status == MatchStatus.Finished) {
                        OutlinedButton(
                            onClick = { showJudgeDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber)
                        ) {
                            Text("判定", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JudgeMatchDialog(
    match: Match,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var winnerId by rememberSaveable { mutableStateOf(match.participants.firstOrNull() ?: "") }
    var reason by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("判定胜负 — 第 ${match.round} 轮") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("人工判定胜负是争议处理的兜底手段，需至少两名管理员签名并进入治理流程。")
                OutlinedTextField(
                    value = winnerId,
                    onValueChange = { winnerId = it },
                    label = { Text("胜者 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("判定理由（必填，用于审计）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(winnerId, reason) },
                enabled = winnerId.isNotBlank() && reason.isNotBlank()
            ) {
                Text("确认判定", color = StatusAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}



@Composable
private fun SeasonManagementSection(
    seasons: List<Season>,
    isLoading: Boolean,
    error: String?,
    selectedSeason: Season?,
    leaderboard: Leaderboard?,
    onArchive: () -> Unit,
    onCopyTemplate: (String) -> Unit,
    onSelectSeason: (Season) -> Unit,
    readOnly: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "赛季管理",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            error != null -> {
                Text(
                    text = "加载赛季失败: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            seasons.isEmpty() -> {
                Text(
                    text = "暂无赛季数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            else -> {
                seasons.forEach { season ->
                    val isSelected = selectedSeason?.id == season.id
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { onSelectSeason(season) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = season.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = season.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${season.start_date}${season.end_date?.let { " → $it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (season.tournament_ids.isNotEmpty()) {
                                Text(
                                    text = "${season.tournament_ids.size} 个赛事",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!readOnly) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (season.status != "archived") {
                                        OutlinedButton(
                                            onClick = onArchive,
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            enabled = selectedSeason?.id == season.id
                                        ) {
                                            Text("归档", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onCopyTemplate(season.id) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("复制模板", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }


                if (selectedSeason != null && leaderboard != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "排行榜 — ${selectedSeason.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (leaderboard.solo.isNotEmpty()) {
                        Text(
                            text = "个人",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        leaderboard.solo.take(5).forEachIndexed { index, entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${index + 1}. ${entry.player_id.truncate(16)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${entry.total_score} 分 · ${entry.tournaments_played} 场",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamCard(team: com.jlucraft.console.data.model.Team) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(team.name, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "成员: ${team.members.size} 人 · 积分: ${team.total_score}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TournamentCard(tournament: Tournament, onClick: () -> Unit) {
    val statusColor = tournament.statusColor()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "赛事",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tournament.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = tournament.statusText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "赛程",
                        modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = tournament.created_at.toShortDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${tournament.game_type} · ${tournament.mode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${tournament.participant_count}/${tournament.max_participants} 人报名 · 最低积分 ${tournament.min_member_score}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }
    }
}

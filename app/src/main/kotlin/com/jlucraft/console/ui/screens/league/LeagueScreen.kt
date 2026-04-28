package com.jlucraft.console.ui.screens.league

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.model.statusColor
import com.jlucraft.console.data.model.statusText
import com.jlucraft.console.data.model.toShortDate
import com.jlucraft.console.ui.components.DetailRow
import com.jlucraft.console.ui.components.StatusChip
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.*
import com.jlucraft.console.viewmodel.LeagueViewModel
import com.jlucraft.console.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(viewModel: LeagueViewModel = viewModel(factory = ViewModelFactory())) {
    val state = viewModel.uiState.value

    when {
        state.selectedTournament != null -> TournamentDetailScreen(
            tournament = state.selectedTournament,
            matches = state.matches,
            teams = state.teams,
            isLoading = state.detailLoading,
            error = state.detailError,
            onBack = { viewModel.clearSelection() },
            onStatusChange = { viewModel.updateTournamentStatus(it) }
        )
        else -> TournamentListScreen(
            state = state,
            onRefresh = { viewModel.refresh() },
            onShowCreate = { viewModel.showCreateDialog() },
            onSelectTournament = { viewModel.selectTournament(it) },
            onDismissCreate = { viewModel.dismissCreateDialog() },
            onCreate = { name, gameType, mode, maxParticipants, minMemberScore, regOpen, regClose ->
                viewModel.createTournament(name, gameType, mode, maxParticipants, minMemberScore, regOpen, regClose)
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
    onCreate: (String, String, String, Int, Int, String, String) -> Unit
) {
    val tournaments = state.tournaments
    val statusCounts = remember(tournaments) {
        tournaments.groupingBy { it.status.lowercase() }.eachCount()
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
            FloatingActionButton(onClick = { onShowCreate() }) {
                Icon(Icons.Default.Add, contentDescription = "创建赛事")
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusChip("进行中", statusCounts["ongoing"].toString(), MaterialTheme.colorScheme.primary)
                    StatusChip("报名中", statusCounts["registration"].toString(), StatusGreen)
                    StatusChip("已结束", statusCounts["completed"].toString(), MaterialTheme.colorScheme.outline)
                    StatusChip("草稿", statusCounts["draft"].toString(), MaterialTheme.colorScheme.onSurfaceVariant)
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
    onStatusChange: (String) -> Unit
) {
    val nextStatus = when (tournament.status) {
        "draft" -> "registration" to "开放报名"
        "registration" -> "ongoing" to "开始比赛"
        "ongoing" -> "completed" to "结束赛事"
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

                        if (nextStatus != null) {
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
                    MatchCard(match)
                }
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
private fun MatchCard(match: Match) {
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

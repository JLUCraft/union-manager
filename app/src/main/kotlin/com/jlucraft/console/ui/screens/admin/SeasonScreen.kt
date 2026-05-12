package com.jlucraft.console.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.StatusBlue
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.SeasonViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonScreen(
    services: AppServices,
    viewModel: SeasonViewModel = viewModel(),
) {
    val state = viewModel.uiState.value
    var showCurrentOnly by remember { mutableStateOf(false) }

    val displayedSeasons = remember(state.seasons, showCurrentOnly) {
        if (showCurrentOnly) {
            state.currentSeason?.let { listOf(it) } ?: emptyList()
        } else {
            state.seasons
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("赛季") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    TextButton(onClick = {
                        showCurrentOnly = !showCurrentOnly
                        if (showCurrentOnly) viewModel.loadCurrentSeason()
                        else viewModel.loadSeasons()
                    }) {
                        Text(if (showCurrentOnly) "全部" else "当前")
                    }
                    IconButton(onClick = { viewModel.loadSeasons() }) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
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
                state.currentSeason?.let { current ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StatusGreen.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "当前赛季",
                                style = MaterialTheme.typography.labelMedium,
                                color = StatusGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = current.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "状态: ${current.status} · 赛事: ${current.tournament_ids.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            listStatePlaceholders(
                isLoading = state.isLoading,
                error = state.error,
                isEmpty = displayedSeasons.isEmpty(),
                emptyText = "暂无赛季数据"
            )

            items(displayedSeasons, key = { it.id }) { season ->
                SeasonCard(
                    season = season,
                    isSelected = state.selectedSeason?.id == season.id,
                    onClick = { viewModel.selectSeason(season) }
                )
            }


            state.selectedSeason?.let { season ->
                item {
                    Text(
                        text = "${season.name} 排行榜",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (state.leaderboardLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.selectedLeaderboard != null) {
                    val lb = state.selectedLeaderboard
                    if (lb.solo.isNotEmpty()) {
                        item {
                            Text(
                                text = "个人排行",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(lb.solo.take(20)) { entry ->
                            LeaderboardEntryRow(
                                rank = lb.solo.indexOf(entry) + 1,
                                playerId = entry.player_id,
                                score = entry.total_score,
                                played = entry.tournaments_played
                            )
                        }
                    }
                    if (lb.team.isNotEmpty()) {
                        item {
                            Text(
                                text = "团队排行",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(lb.team.take(20)) { entry ->
                            LeaderboardEntryRow(
                                rank = lb.team.indexOf(entry) + 1,
                                playerId = entry.team_name,
                                score = entry.total_score,
                                played = entry.tournaments_played
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(
    season: Season,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) StatusBlue else MaterialTheme.colorScheme.outlineVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder().let { border ->
            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                StatusBlue.copy(alpha = 0.05f)
            else
                MaterialTheme.colorScheme.surfaceVariant
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
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = season.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (season.status) {
                        "active" -> StatusGreen
                        "archived" -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "开始: ${season.start_date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${season.tournament_ids.size} 赛事",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            season.end_date?.let { endDate ->
                Text(
                    text = "结束: $endDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LeaderboardEntryRow(
    rank: Int,
    playerId: String,
    score: Double,
    played: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = playerId.take(24),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.1f".format(score),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${played}场",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

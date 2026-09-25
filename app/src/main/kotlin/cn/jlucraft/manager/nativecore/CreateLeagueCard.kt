package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.jlucraft.manager.R


@Composable
fun CreateLeagueCard(
    league: String,
    seats: String,
    crossClub: Boolean,
    host: String,
    scorer: String,
    connected: Boolean,
    busy: Boolean,
    seatsValid: Boolean,
    onLeagueChange: (String) -> Unit,
    onSeatsChange: (String) -> Unit,
    onCrossClubChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onScorerChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(host.isBlank() || scorer.isBlank()) }
    ManagerCard(
        title = stringResource(R.string.governance_create_league),
        helper = if (connected) null else stringResource(R.string.governance_create_league_helper),
    ) {
        OutlinedTextField(
            value = league,
            onValueChange = onLeagueChange,
            label = { Text(stringResource(R.string.governance_league_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = seats,
            onValueChange = onSeatsChange,
            label = { Text(stringResource(R.string.governance_seats_label)) },
            singleLine = true,
            supportingText = if (seatsValid) null else ({ Text(stringResource(R.string.governance_seats_invalid)) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = crossClub, onValueChange = onCrossClubChange, role = Role.Checkbox),
        ) {
            Checkbox(checked = crossClub, onCheckedChange = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.league_cross_club), style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(if (showAdvanced) R.string.league_advanced_hide else R.string.league_advanced_show))

        }
        if (showAdvanced) {
            Text(
                stringResource(R.string.league_advanced_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.league_host_identity)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scorer,
                onValueChange = onScorerChange,
                label = { Text(stringResource(R.string.league_scorer_identity)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = onCreate,
            enabled = !busy && connected && seatsValid && league.isNotBlank() && host.isNotBlank() && scorer.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        ) {
            Text(stringResource(R.string.governance_create_league))
        }
    }
}

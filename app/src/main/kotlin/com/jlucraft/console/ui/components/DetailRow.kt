package com.jlucraft.console.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DetailRow(label: String, value: String, compact: Boolean = false) {
    val typography = MaterialTheme.typography
    val labelStyle = if (compact) typography.bodySmall else typography.bodyMedium
    val valueStyle = if (compact) typography.bodySmall else typography.bodyMedium
    val valueWeight = if (compact) FontWeight.Normal else FontWeight.Medium
    val verticalPadding = if (compact) 0.dp else 4.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = valueStyle,
            fontWeight = valueWeight
        )
    }
}

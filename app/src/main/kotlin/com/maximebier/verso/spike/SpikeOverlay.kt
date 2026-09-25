package com.maximebier.verso.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Phases de mesure : chaque RELEASE du journal porte la phase choisie ici. */
val SpikePhases = listOf("lecture", "fling", "saut")

@Composable
fun SpikeOverlay(
    visible: Boolean,
    info: String,
    phase: String,
    onPhase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(info, color = Color.White, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpikePhases.forEach { candidate ->
                FilterChip(
                    selected = phase == candidate,
                    onClick = { onPhase(candidate) },
                    label = { Text(candidate, color = Color.White) },
                )
            }
        }
    }
}

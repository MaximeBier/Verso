package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/** Snackbar inverse : message sur 1 ou 2 lignes, action en inverseAccent, annoncée par TalkBack. */
@Composable
fun VersoSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(elevation = 8.dp, shape = VersoShapes.small)
            .clip(VersoShapes.small)
            .background(colors.inverse)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 20.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.visuals.message,
            style = VersoTypography.body.copy(lineHeight = VersoTypography.body.fontSize * 1.4f),
            color = colors.onInverse,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        data.visuals.actionLabel?.let { label ->
            VersoTextButton(text = label, onClick = { data.performAction() }, color = colors.inverseAccent, horizontalPadding = 16.dp)
        }
    }
}

/** Hôte placé en bas d'écran : 16 dp des bords, 24 dp du bas + barre de gestes. */
@Composable
fun VersoSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) { data -> VersoSnackbar(data) }
}

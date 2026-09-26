package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/** Au-delà de cette part de la largeur, l'action passe sous le message (texte système agrandi). */
private const val ACTION_MAX_WIDTH_FRACTION = 0.4f

private val MessageActionGap = 12.dp

/** Marge droite du message quand l'action est dessous (le conteneur n'a que 4 dp, prévus pour le bouton). */
private val StackedMessageEndPadding = 16.dp

/**
 * Snackbar inverse : message sur 1 ou 2 lignes, action en inverseAccent à droite, annoncée par TalkBack. Si
 * l'action prend trop de largeur (texte à 200 %), elle passe sous le message, alignée à droite, pour que le
 * message garde toute la largeur.
 */
@Composable
fun VersoSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Layout(
        content = {
            Text(
                text = data.visuals.message,
                style = VersoTypography.body.copy(lineHeight = VersoTypography.body.fontSize * 1.4f),
                color = colors.onInverse,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            data.visuals.actionLabel?.let { label ->
                VersoTextButton(text = label, onClick = { data.performAction() }, color = colors.inverseAccent, horizontalPadding = 16.dp)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(elevation = 8.dp, shape = VersoShapes.small)
            .clip(VersoShapes.small)
            .background(colors.inverse)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 20.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val action = measurables.getOrNull(1)?.measure(loose)
        val stacked = action != null && action.width > width * ACTION_MAX_WIDTH_FRACTION
        val messageWidth = when {
            action == null -> width
            stacked -> width - StackedMessageEndPadding.roundToPx()
            else -> width - action.width - MessageActionGap.roundToPx()
        }
        val message = measurables[0].measure(loose.copy(maxWidth = messageWidth.coerceAtLeast(0)))
        if (action != null && stacked) {
            val height = maxOf(constraints.minHeight, message.height + action.height)
            layout(width, height) {
                message.placeRelative(0, 0)
                action.placeRelative(width - action.width, message.height)
            }
        } else {
            val height = maxOf(constraints.minHeight, message.height, action?.height ?: 0)
            layout(width, height) {
                message.placeRelative(0, (height - message.height) / 2)
                action?.placeRelative(width - action.width, (height - action.height) / 2)
            }
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

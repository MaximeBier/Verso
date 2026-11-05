package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Carte de retour (1.14) : rayon 20 (`VersoShapes.card`), pas de minuterie ni de balayage pour la fermer. En clair et
 * en sépia, fond `inverse` (sombre) comme la maquette ; en sombre et en nuit, carte sombre surélevée (`surfaceHigh`,
 * filet `outline`, bouton accent) plutôt qu’un bloc clair éblouissant (étape 34). Annoncée poliment par TalkBack à
 * son apparition.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReturnCard(
    location: String,
    percent: Int,
    onStayHere: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    val shape = VersoShapes.card
    val palette = if (VersoTheme.isDark) {
        ReturnCardPalette(colors.surfaceHigh, colors.text, colors.accent, colors.onAccent, border = colors.outline)
    } else {
        ReturnCardPalette(colors.inverse, colors.onInverse, colors.inverseAccent, colors.inverse, border = null)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = shape)
            .then(if (palette.border != null) Modifier.border(1.dp, palette.border, shape) else Modifier)
            .background(palette.container, shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = VersoIcons.Bookmark,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.return_card_title),
                    style = VersoTheme.typography.bookTitleStrong,
                    color = palette.content,
                )
                Text(
                    text = stringResource(R.string.return_card_position, location, percent),
                    style = VersoTheme.typography.caption,
                    color = palette.content,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            // Bouton texte (§3.8) : composant partagé, couleur du texte de la carte, padding 16.
            VersoTextButton(
                text = stringResource(R.string.return_card_stay),
                onClick = onStayHere,
                color = palette.content,
                horizontalPadding = 16.dp,
            )
            // « Revenir », plein à l’accent de la carte (§3.8) : aucun composant partagé ne porte ces couleurs.
            Button(
                onClick = onGoBack,
                modifier = Modifier.heightIn(min = VersoDimens.controlMin),
                shape = VersoShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
            ) {
                Icon(VersoIcons.Undo, contentDescription = null, modifier = Modifier.size(VersoDimens.iconSmall))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.return_card_go_back), style = VersoTheme.typography.button)
            }
        }
    }
}

/** Couleurs de la carte : `inverse` en clair et en sépia, surface surélevée avec filet en sombre et en nuit. */
private data class ReturnCardPalette(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color,
    val border: Color?,
)

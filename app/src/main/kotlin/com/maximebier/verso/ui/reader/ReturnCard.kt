package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
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
import com.maximebier.verso.ui.theme.VersoTypography

/**
 * Carte de retour (1.14) : fond `inverse`, rayon 20 (`VersoShapes.card`), pas de minuterie ni de
 * balayage pour la fermer. Annoncée poliment par TalkBack à son apparition.
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = shape)
            .background(colors.inverse, shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = VersoIcons.Bookmark,
                contentDescription = null,
                tint = colors.inverseAccent,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.return_card_title),
                    style = VersoTypography.bookTitleStrong,
                    color = colors.onInverse,
                )
                Text(
                    text = stringResource(R.string.return_card_position, location, percent),
                    style = VersoTypography.caption,
                    color = colors.onInverse,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            // Bouton texte sur inverse (§3.8) : composant partagé, couleur onInverse, padding 16.
            VersoTextButton(
                text = stringResource(R.string.return_card_stay),
                onClick = onStayHere,
                color = colors.onInverse,
                horizontalPadding = 16.dp,
            )
            // « Revenir », plein sur inverse (§3.8) : aucun composant partagé ne porte ces couleurs.
            Button(
                onClick = onGoBack,
                modifier = Modifier.heightIn(min = VersoDimens.controlMin),
                shape = VersoShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = colors.inverseAccent, contentColor = colors.inverse),
                contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
            ) {
                Icon(VersoIcons.Undo, contentDescription = null, modifier = Modifier.size(VersoDimens.iconSmall))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.return_card_go_back), style = VersoTypography.button)
            }
        }
    }
}

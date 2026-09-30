package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/** Bouton à icône seule : cible 48 × 48, cercle, intitulé TalkBack obligatoire. */
@Composable
fun VersoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = VersoTheme.colors.text,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier.size(VersoDimens.controlMin), enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else VersoTheme.colors.outline,
            modifier = Modifier.size(VersoDimens.icon),
        )
    }
}

/**
 * Barre de la bibliothèque : logotype, Paramètres, et « Importer » sauf bibliothèque vide. À 100 %, les trois
 * tiennent sur une ligne. À grande taille de texte système, `FlowRow` passe le groupe Paramètres/Importer à la
 * ligne plutôt que de le couper (contrainte « texte à 200 % sans coupure ») ; le logotype garde sa taille en dp.
 */
@Composable
fun LibraryTopBar(
    showImport: Boolean,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = VersoDimens.topBarLibrary)
            .padding(start = 24.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VersoLogotype(Modifier.align(Alignment.CenterVertically))
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersoIconButton(icon = VersoIcons.Settings, contentDescription = stringResource(R.string.library_settings), onClick = onOpenSettings)
            if (showImport) {
                OutlinedPillButton(text = stringResource(R.string.library_import), onClick = onImport, icon = VersoIcons.Plus)
            }
        }
    }
}

/**
 * Barre des écrans de détail (fiche, paramètres, licences) : retour + titre 22 sp. Le titre, toujours un libellé
 * de l’app, tient sur une ligne à 100 % et passe à la ligne au lieu d’être tronqué quand le texte système grossit.
 */
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backContentDescription: String = stringResource(R.string.common_back),
) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = VersoDimens.topBarDetail)
            // Marge verticale sans effet à 100 % (hauteur minimale) ; elle décolle le titre des bords quand il passe à la ligne.
            .padding(start = 4.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersoIconButton(icon = VersoIcons.ArrowLeft, contentDescription = backContentDescription, onClick = onBack)
        Text(
            text = title,
            style = VersoTheme.typography.screenTitle,
            color = colors.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

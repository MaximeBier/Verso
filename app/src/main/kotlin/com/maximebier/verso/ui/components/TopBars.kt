package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/** Bouton à icône seule : cible 48 × 48, cercle, intitulé TalkBack obligatoire. */
@Composable
fun VersoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = VersoTheme.colors.text,
) {
    IconButton(onClick = onClick, modifier = modifier.size(VersoDimens.controlMin)) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(VersoDimens.icon))
    }
}

/** Barre de la bibliothèque : « Verso » (texte, pas le logo), Paramètres, et « Importer » sauf bibliothèque vide. */
@Composable
fun LibraryTopBar(
    showImport: Boolean,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = VersoDimens.topBarLibrary)
            .padding(start = 24.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = VersoTypography.logo,
            color = colors.text,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            VersoIconButton(icon = VersoIcons.Settings, contentDescription = stringResource(R.string.library_settings), onClick = onOpenSettings)
            if (showImport) {
                OutlinedPillButton(text = stringResource(R.string.library_import), onClick = onImport, icon = VersoIcons.Plus)
            }
        }
    }
}

/** Barre des écrans de détail (fiche, paramètres, licences) : retour + titre 22 sp sur une ligne. */
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
            .padding(start = 4.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersoIconButton(icon = VersoIcons.ArrowLeft, contentDescription = backContentDescription, onClick = onBack)
        Text(
            text = title,
            style = VersoTypography.screenTitle,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

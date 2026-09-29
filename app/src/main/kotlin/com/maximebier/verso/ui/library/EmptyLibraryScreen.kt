package com.maximebier.verso.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.AppIcon
import com.maximebier.verso.ui.components.LibraryTopBar
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoTheme

/** Premier lancement : barre sans « Importer », un seul bouton principal de 56 dp. */
@Composable
fun EmptyLibraryScreen(onOpenSettings: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        LibraryTopBar(showImport = false, onOpenSettings = onOpenSettings, onImport = onImport)
        EmptyLibraryContent(onImport = onImport, modifier = Modifier.weight(1f))
    }
}

/**
 * Bloc centré verticalement sous la barre, 88 dp de marge basse (centre optique relevé),
 * défilable pour le texte à 200 %.
 */
@Composable
fun EmptyLibraryContent(onImport: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = available)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(start = 32.dp, end = 32.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            AppIcon(size = 64.dp)
            Text(
                text = stringResource(R.string.library_empty_title),
                style = VersoTheme.typography.display,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.library_empty_body),
                style = VersoTheme.typography.emptyBody,
                color = colors.textSecondary,
            )
            PrimaryButton(
                text = stringResource(R.string.library_empty_import),
                onClick = onImport,
                icon = VersoIcons.Plus,
                large = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.library_empty_open_with_hint),
                style = VersoTheme.typography.emptyHint,
                color = colors.textSecondary,
            )
        }
    }
}

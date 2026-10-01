package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Barre « Texte sélectionné » (3.04) : début du passage, puis Surligner, Note, Copier en colonnes égales de 64 dp.
 * Remplace le menu d’Android ; posée en bas de l’écran, par-dessus le texte. [onNote] null : bouton absent.
 */
@Composable
fun SelectionBar(
    selectionText: String,
    onHighlight: () -> Unit,
    onNote: (() -> Unit)?,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    val toolbarLabel = stringResource(R.string.selection_toolbar)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(20.dp, VersoShapes.sheetTop, clip = false)
            .clip(VersoShapes.sheetTop)
            .background(colors.surface)
            .border(1.dp, colors.divider, VersoShapes.sheetTop)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp)
            .semantics {
                isTraversalGroup = true
                contentDescription = toolbarLabel
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.selection_label, selectionText),
            style = VersoTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        val tools = buildList {
            add(ReaderTool(stringResource(R.string.selection_highlight), ToolGlyph.Icon(VersoIcons.Highlighter), onHighlight))
            if (onNote != null) add(ReaderTool(stringResource(R.string.selection_note), ToolGlyph.Icon(VersoIcons.Note), onNote))
            add(ReaderTool(stringResource(R.string.selection_copy), ToolGlyph.Icon(VersoIcons.Copy), onCopy))
        }
        // Même rangée adaptative que la barre de lecture (2.01) : colonnes égales, deux puis une à 200 %.
        ReaderToolbar(tools)
    }
}

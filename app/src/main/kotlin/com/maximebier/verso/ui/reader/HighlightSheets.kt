package com.maximebier.verso.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.ActionRow
import com.maximebier.verso.ui.components.MarkedText
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Surlignage touché dans le texte : passage, note, puis « Modifier la note » (« Ajouter une note » sans note),
 * « Supprimer », « Copier ». [onEditNote] et [onDelete] null : lignes absentes.
 */
@Composable
fun HighlightActionsSheet(
    state: HighlightActionsState,
    onCopy: () -> Unit,
    onEditNote: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    VersoBottomSheet(
        title = stringResource(R.string.highlight_sheet_title),
        subtitle = state.location,
        onDismissRequest = onDismiss,
        fitContent = true,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            MarkedText(
                text = state.passage,
                style = VersoTheme.typography.body,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            state.note?.let { note ->
                Text(
                    text = note,
                    style = VersoTheme.typography.body,
                    color = VersoTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            onEditNote?.let {
                ActionRow(
                    title = stringResource(if (state.note == null) R.string.highlight_add_note else R.string.highlight_edit_note),
                    summary = null,
                    icon = VersoIcons.Note,
                    onClick = it,
                )
            }
            onDelete?.let {
                ActionRow(
                    title = stringResource(R.string.highlight_delete),
                    summary = null,
                    icon = VersoIcons.Trash,
                    onClick = it,
                    iconTint = VersoTheme.colors.danger,
                )
            }
            ActionRow(title = stringResource(R.string.highlight_action_copy), summary = null, icon = VersoIcons.Copy, onClick = onCopy)
        }
    }
}

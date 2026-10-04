package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.ActionRow
import com.maximebier.verso.ui.components.MarkedText
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Note touchée dans le texte : passage, note, puis « Modifier la note »,
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
                    title = stringResource(R.string.highlight_edit_note),
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

/**
 * « Ajouter une note » (3.05) : passage surligné sur fond de page, champ « Votre note » (bord accent 2 dp), puis
 * Annuler et Enregistrer à droite. La croix et le voile annulent. Le champ prend le focus à l’ouverture.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteSheet(
    state: NoteSheetState,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = VersoTheme.colors
    val focus = remember { FocusRequester() }
    // Curseur à la fin de la note existante (« Modifier la note »), pas au début.
    var field by remember { mutableStateOf(TextFieldValue(state.note, TextRange(state.note.length))) }
    VersoBottomSheet(
        title = stringResource(if (state.editing) R.string.note_sheet_title_edit else R.string.note_sheet_title),
        subtitle = null,
        onDismissRequest = onCancel,
        fitContent = true,
    ) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MarkedText(
                text = state.passage,
                // 16 sp, interligne 1,55 (3.05).
                style = VersoTheme.typography.body.copy(lineHeight = 24.8.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(VersoShapes.small)
                    .background(colors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val label = stringResource(R.string.note_field_label)
                Text(text = label, style = VersoTheme.typography.captionBold, color = colors.textSecondary)
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onNoteChange(it.text)
                    },
                    // 16 sp, interligne 1,45 (3.05).
                    textStyle = VersoTheme.typography.body.copy(lineHeight = 23.2.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = label }
                        .clip(VersoShapes.small)
                        .background(colors.background)
                        .border(2.dp, colors.accent, VersoShapes.small)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                )
            }
            // FlowRow : à 200 %, « Enregistrer » passe sous « Annuler » au lieu d’être coupé en plein mot.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VersoTextButton(text = stringResource(R.string.note_cancel), onClick = onCancel)
                // Une note vide n’en est pas une : rien à enregistrer.
                PrimaryButton(text = stringResource(R.string.note_save), onClick = onSave, enabled = state.note.isNotBlank())
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

package com.maximebier.verso.ui.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.MarkedText
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.library.BookOptionsMenu
import com.maximebier.verso.ui.library.BookOptionsMenuItem
import com.maximebier.verso.ui.reader.NoteSheet
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/** Actions de l’écran « Notes et surlignages » (3.06). */
class NotesActions(
    val onBack: () -> Unit,
    /** Ouvre le sélecteur d’Android (voir [rememberNotesExport]). */
    val onExport: () -> Unit,
    /** Toucher l’élément, ou « Aller au passage ». */
    val onOpen: (id: Long) -> Unit,
    val onEditNote: (id: Long) -> Unit,
    val onDelete: (id: Long) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSaveNote: () -> Unit,
    val onCancelNote: () -> Unit,
)

/**
 * « Notes et surlignages » d’un livre (3.06) : barre de titre avec « Exporter », phrase d’aide, éléments dans l’ordre
 * du livre (passage surligné, note, emplacement, menu ⋮). Sans surlignage : phrase d’état, pas d’« Exporter ».
 */
@Composable
fun NotesScreen(state: NotesUiState, actions: NotesActions, modifier: Modifier = Modifier, snackbarHost: @Composable () -> Unit = {}) {
    val colors = VersoTheme.colors
    Box(modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
            item { NotesHeader(state, actions) }
            if (state.items.isEmpty()) {
                if (state.loaded) {
                    item {
                        Text(
                            text = stringResource(R.string.notes_empty),
                            style = VersoTheme.typography.body,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.notes_export_hint),
                        // 14 sp, interligne 1,45 (3.06).
                        style = VersoTheme.typography.caption.copy(lineHeight = 20.3.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 12.dp),
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                items(state.items, key = { it.id }) { item -> NoteRow(item, actions) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
    }
    state.noteSheet?.let { NoteSheet(it, actions.onNoteChange, actions.onSaveNote, actions.onCancelNote) }
}

@Composable
private fun NotesHeader(state: NotesUiState, actions: NotesActions) {
    val colors = VersoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersoIconButton(icon = VersoIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back), onClick = actions.onBack)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(R.string.notes_title),
                style = VersoTheme.typography.screenTitle,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            if (state.bookTitle.isNotEmpty()) {
                val count = state.items.size
                Text(
                    text = stringResource(R.string.notes_subtitle, state.bookTitle, pluralStringResource(R.plurals.notes_item_count, count, count)),
                    // 14 sp, interligne 1,3 (3.06).
                    style = VersoTheme.typography.caption.copy(lineHeight = 18.2.sp),
                    color = colors.textSecondary,
                )
            }
        }
        if (state.items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .heightIn(min = VersoDimens.controlMin)
                    .clip(VersoShapes.pill)
                    .clickable(role = Role.Button, onClick = actions.onExport)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(VersoIcons.Download, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.notes_export), style = VersoTheme.typography.button, color = colors.text)
            }
        }
    }
}

@Composable
private fun NoteRow(item: NoteItem, actions: NotesActions) {
    val colors = VersoTheme.colors
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button) { actions.onOpen(item.id) }
                .padding(start = 24.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 18 sp, interligne 1,55 (3.06).
            MarkedText(item.text, style = VersoTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 27.9.sp))
            item.note?.let { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        VersoIcons.Note,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.padding(top = 3.dp).size(18.dp),
                    )
                    Text(note, style = VersoTheme.typography.body, color = colors.textSecondary)
                }
            }
            Text(item.location, style = VersoTheme.typography.caption, color = colors.textSecondary)
        }
        Box(Modifier.padding(top = 8.dp)) { NoteOptions(item, actions) }
    }
    HorizontalDivider(thickness = 1.dp, color = colors.divider)
}

/** Menu ⋮ d’un élément (3.06) : « Aller au passage », « Modifier la note » ou « Ajouter une note », « Supprimer ». */
@Composable
private fun NoteOptions(item: NoteItem, actions: NotesActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        VersoIconButton(
            icon = VersoIcons.MoreVertical,
            contentDescription = stringResource(R.string.notes_item_options),
            onClick = { expanded = true },
            tint = VersoTheme.colors.textSecondary,
        )
        BookOptionsMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookOptionsMenuItem(
                text = stringResource(R.string.notes_go_to_passage),
                icon = VersoIcons.ArrowRight,
                onClick = {
                    expanded = false
                    actions.onOpen(item.id)
                },
            )
            BookOptionsMenuItem(
                text = stringResource(if (item.note == null) R.string.notes_add_note else R.string.notes_edit_note),
                icon = VersoIcons.Note,
                onClick = {
                    expanded = false
                    actions.onEditNote(item.id)
                },
            )
            BookOptionsMenuItem(
                text = stringResource(R.string.notes_delete),
                icon = VersoIcons.Trash,
                destructive = true,
                onClick = {
                    expanded = false
                    actions.onDelete(item.id)
                },
            )
        }
    }
}

/** Sélecteur « Enregistrer sous » d’Android pour le fichier Markdown ; renvoie la fonction qui l’ouvre. */
@Composable
fun rememberNotesExport(fileName: String, onChosen: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) onChosen(uri)
    }
    val currentName by rememberUpdatedState(fileName)
    return { launcher.launch(currentName) }
}

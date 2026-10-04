package com.maximebier.verso.ui.notes

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.R
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.ui.common.notesTexts
import com.maximebier.verso.ui.components.VersoSnackbarHost

/** « Notes » ouvert depuis la fiche (route `NotesRoute`) ; aller au passage ouvre le lecteur. */
class NotesViewModel(model: (ViewModel) -> NotesListModel) : ViewModel() {
    val list: NotesListModel = model(this)

    companion object {
        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication
                val container = app.container
                NotesViewModel { owner ->
                    NotesListModel(
                        bookId = bookId,
                        highlights = container.highlights,
                        books = container.books,
                        scope = owner.viewModelScope,
                        texts = app.resources.notesTexts(),
                        writeText = container.documents::writeText,
                        clock = container.clock,
                    )
                }
            }
        }
    }
}

/** Point d’entrée de `NotesRoute`. */
@Composable
fun NotesDestination(bookId: Long, onBack: () -> Unit, onOpenPassage: (bookId: Long, highlightId: Long) -> Unit) {
    val viewModel: NotesViewModel = viewModel(key = "notes-$bookId", factory = NotesViewModel.factory(bookId))
    val list = viewModel.list
    val state by list.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.highlight_deleted)
    val undoLabel = stringResource(R.string.highlight_deleted_undo)
    val exportedMessage = stringResource(R.string.notes_exported)
    val exportFailedMessage = stringResource(R.string.notes_export_failed)
    LaunchedEffect(list) {
        list.events.collect { event ->
            when (event) {
                is NotesEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) list.undoDelete(event.row)
                }
                NotesEvent.Exported -> snackbarHostState.showSnackbar(exportedMessage)
                NotesEvent.ExportFailed -> snackbarHostState.showSnackbar(exportFailedMessage, duration = SnackbarDuration.Long)
            }
        }
    }
    val export = rememberNotesExport(state.exportFileName, list::export)
    NotesScreen(
        state = state,
        actions = NotesActions(
            onBack = onBack,
            onExport = export,
            onOpen = { id -> onOpenPassage(bookId, id) },
            onEditNote = list::editNote,
            onDelete = list::delete,
            onNoteChange = list::onNoteChange,
            onSaveNote = list::saveNote,
            onCancelNote = list::cancelNote,
        ),
        snackbarHost = { VersoSnackbarHost(snackbarHostState, Modifier.windowInsetsPadding(WindowInsets.navigationBars)) },
    )
}

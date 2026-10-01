package com.maximebier.verso.screenshots.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.maximebier.verso.ui.reader.NoteSheet
import com.maximebier.verso.ui.reader.NoteSheetState
import com.maximebier.verso.ui.reader.SelectionBar
import com.maximebier.verso.ui.theme.VersoTheme

/** 3.04 : texte de lecture, barre « Texte sélectionné » en bas. */
@Composable
internal fun SelectionBarSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
        SelectionBar(
            selectionText = "la campagne ainsi ressemble à un grand manteau…",
            onHighlight = {},
            onNote = {},
            onCopy = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 3.05 : feuille « Ajouter une note » au-dessus du texte. */
@Composable
internal fun NoteSheetSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) { SampleReadingText() }
    NoteSheet(
        state = NoteSheetState(
            passage = "la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert, bordé d’un galon d’argent.",
            note = "Image du manteau : la prairie verte, bordée par la rivière.",
            editing = false,
        ),
        onNoteChange = {},
        onSave = {},
        onCancel = {},
    )
}

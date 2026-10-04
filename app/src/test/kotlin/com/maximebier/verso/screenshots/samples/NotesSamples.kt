package com.maximebier.verso.screenshots.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.maximebier.verso.core.translation.TranslationResult
import com.maximebier.verso.ui.notes.NoteItem
import com.maximebier.verso.ui.notes.NotesActions
import com.maximebier.verso.ui.notes.NotesScreen
import com.maximebier.verso.ui.notes.NotesUiState
import com.maximebier.verso.ui.reader.NoteSheet
import com.maximebier.verso.ui.reader.NoteSheetState
import com.maximebier.verso.ui.reader.SelectionBar
import com.maximebier.verso.ui.reader.TranslationSheet
import com.maximebier.verso.ui.reader.TranslationSheetState
import com.maximebier.verso.ui.theme.VersoTheme

/** 3.04 : texte de lecture, barre « Texte sélectionné » en bas. */
@Composable
internal fun SelectionBarSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
        SelectionBar(
            selectionText = "la campagne ainsi ressemble à un grand manteau…",
            onTranslate = {},
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

/** 3.06 : « Notes » de Madame Bovary, deux notes. */
internal val notesSampleState = NotesUiState(
    bookTitle = "Madame Bovary",
    loaded = true,
    items = listOf(
        NoteItem(1, "Il avait les cheveux coupés droit sur le front, comme un chantre de village, l’air raisonnable et fort embarrassé.", "Premier portrait de Charles.", "Première partie, chap. I · 1 %"),
        NoteItem(2, "la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert, bordé d’un galon d’argent.", "Image du manteau : la prairie verte, bordée par la rivière.", "Deuxième partie, chap. I · 30 %"),
    ),
)

private val noActions = NotesActions({}, {}, {}, {}, {}, {}, {}, {})

@Composable
internal fun NotesSample() = NotesScreen(notesSampleState, noActions)

@Composable
internal fun NotesEmptySample() = NotesScreen(notesSampleState.copy(items = emptyList()), noActions)

/** 3.08 : « acknowledged » traduit, trois autres sens. */
@Composable
internal fun TranslationWordSample() = TranslationSample(
    TranslationSheetState("acknowledged", short = true, TranslationResult.Word("reconnu", listOf("admis", "avoué", "accepté"))),
)

/** 3.09 : la première phrase de Pride and Prejudice traduite. */
@Composable
internal fun TranslationPassageSample() = TranslationSample(
    TranslationSheetState(
        "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
        short = false,
        TranslationResult.Passage("C’est une vérité universellement reconnue qu’un homme célibataire pourvu d’une belle fortune doit être en quête d’une épouse."),
    ),
)

/** 3.10 : « Pas de connexion » avec « Réessayer ». */
@Composable
internal fun TranslationOfflineSample() = TranslationSample(TranslationSheetState("acknowledged", short = true, TranslationResult.Offline))

/** Chargement (pas de maquette) : le mot et un indicateur. */
@Composable
internal fun TranslationLoadingSample() = TranslationSample(TranslationSheetState("acknowledged", short = true, result = null))

@Composable
private fun TranslationSample(state: TranslationSheetState) {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
        TranslationSheet(state = state, onRetry = {}, onDismiss = {}, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

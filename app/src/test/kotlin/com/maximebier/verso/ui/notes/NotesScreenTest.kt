package com.maximebier.verso.ui.notes

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesScreenTest {
    @get:Rule val rule = createComposeRule()
    private val calls = mutableListOf<String>()
    private val actions = NotesActions(
        onBack = { calls += "retour" }, onExport = { calls += "exporter" }, onOpen = { calls += "ouvrir $it" },
        onEditNote = { calls += "note $it" }, onDelete = { calls += "supprimer $it" },
        onNoteChange = {}, onSaveNote = {}, onCancelNote = {},
    )
    private val state = NotesUiState(
        bookTitle = "Madame Bovary",
        loaded = true,
        items = listOf(
            NoteItem(1, "Il avait les cheveux coupés droit", "Premier portrait de Charles.", "Première partie, chap. I · 1 %"),
            NoteItem(2, "On l’aperçoit de loin", null, "Deuxième partie, chap. I · 31 %"),
        ),
    )

    private fun show(s: NotesUiState = state) = rule.setContent {
        VersoTheme(theme = AppTheme.LIGHT) { NotesScreen(s, actions) }
    }

    @Test fun headerListAndMenu() {
        show()
        rule.onNodeWithText("Notes et surlignages").assertExists()
        rule.onNodeWithText("Madame Bovary · 2\u00A0éléments").assertExists()
        rule.onNodeWithText("L’export crée un fichier Markdown à garder ou à partager.").assertExists()
        rule.onNodeWithText("Exporter").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Il avait les cheveux coupés droit").performClick()
        rule.onAllNodesWithContentDescription("Options de cet élément")[1].assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Ajouter une note").performClick()   // élément 2 : sans note
        rule.onAllNodesWithContentDescription("Options de cet élément")[0].performClick()
        rule.onNodeWithText("Aller au passage").performClick()
        rule.onAllNodesWithContentDescription("Options de cet élément")[0].performClick()
        rule.onNodeWithText("Supprimer").performClick()
        assertThat(calls).containsExactly("exporter", "ouvrir 1", "note 2", "ouvrir 1", "supprimer 1").inOrder()
    }

    @Test fun emptyBookHidesExport() {
        show(state.copy(items = emptyList()))
        rule.onNodeWithText("Exporter").assertDoesNotExist()
        rule.onNodeWithText("Aucun surlignage pour l’instant. Sélectionnez un passage pendant la lecture pour le surligner.").assertExists()
    }
}


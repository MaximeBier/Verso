package com.maximebier.verso.ui.reader

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun showsThePassageAndSavesTheTypedNote() {
        var note = ""
        var saved = 0
        var cancelled = 0
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                NoteSheet(
                    state = NoteSheetState("la campagne ainsi ressemble", note, editing = false),
                    onNoteChange = { note = it },
                    onSave = { saved++ },
                    onCancel = { cancelled++ },
                )
            }
        }
        rule.onNodeWithText("Ajouter une note").assertExists()
        rule.onNodeWithText("la campagne ainsi ressemble").assertExists()
        rule.onNode(hasSetTextAction()).performTextReplacement("Image du manteau")
        assertThat(note).isEqualTo("Image du manteau")
        rule.onNodeWithText("Enregistrer").performClick()
        rule.onNodeWithText("Annuler").performClick()
        rule.onNodeWithContentDescription("Fermer").performClick()
        assertThat(saved).isEqualTo(1)
        assertThat(cancelled).isEqualTo(2)
    }

    @Test
    fun editingTitle() {
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                NoteSheet(NoteSheetState("passage", "note", editing = true), {}, {}, {})
            }
        }
        rule.onNodeWithText("Modifier la note").assertExists()
    }
}

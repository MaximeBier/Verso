package com.maximebier.verso.ui.reader

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
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
class SelectionBarTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun showsTheStartOfTheSelectionAndThreeActions() {
        val clicks = mutableListOf<String>()
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                SelectionBar(
                    selectionText = "la campagne ainsi ressemble à un grand manteau…",
                    onHighlight = { clicks += "surligner" },
                    onNote = { clicks += "note" },
                    onCopy = { clicks += "copier" },
                )
            }
        }
        rule.onNodeWithText("Sélection : « la campagne ainsi ressemble à un grand manteau… »").assertExists()
        listOf("Surligner", "Note", "Copier").forEach {
            rule.onNodeWithText(it).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        }
        assertThat(clicks).containsExactly("surligner", "note", "copier").inOrder()
    }

    @Test
    fun noteIsHiddenUntilItsSheetExists() {
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                SelectionBar(selectionText = "passage", onHighlight = {}, onNote = null, onCopy = {})
            }
        }
        rule.onNodeWithText("Note").assertDoesNotExist()
    }
}

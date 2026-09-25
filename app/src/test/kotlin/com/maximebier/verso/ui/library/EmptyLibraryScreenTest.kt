package com.maximebier.verso.ui.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyLibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsASingle56dpImportButtonAndNoHeaderImport() {
        var imports = 0
        compose.setContent {
            VersoTheme(darkTheme = false) { EmptyLibraryScreen(onOpenSettings = {}, onImport = { imports++ }) }
        }
        compose.onNodeWithText("Votre bibliothèque est vide").assertIsDisplayed()
        compose.onNode(hasText("Importer un EPUB") and hasClickAction())
            .assertHeightIsEqualTo(56.dp)
            .performClick()
        assertThat(imports).isEqualTo(1)
        compose.onAllNodesWithText("Importer").assertCountEquals(0)
    }

    @Test
    fun settingsButtonIsLabelledAndClickable() {
        var settings = 0
        compose.setContent {
            VersoTheme(darkTheme = false) { EmptyLibraryScreen(onOpenSettings = { settings++ }, onImport = {}) }
        }
        compose.onNodeWithContentDescription("Paramètres").performClick()
        assertThat(settings).isEqualTo(1)
    }
}

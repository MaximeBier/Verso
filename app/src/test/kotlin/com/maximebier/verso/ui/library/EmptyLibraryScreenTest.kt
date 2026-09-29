package com.maximebier.verso.ui.library

import com.maximebier.verso.data.AppTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
            VersoTheme(theme = AppTheme.LIGHT) { EmptyLibraryScreen(onOpenSettings = {}, onImport = { imports++ }) }
        }
        compose.onNodeWithText("Votre bibliothèque est vide").assertIsDisplayed()
        compose.onNode(hasText("Importer un EPUB") and hasClickAction())
            .assertHeightIsEqualTo(56.dp)
            .performClick()
        assertThat(imports).isEqualTo(1)
        compose.onAllNodesWithText("Importer").assertCountEquals(0)
    }

    /** En-tête : le logotype est une image lue « Verso », titre de l'écran ; l'icône de l'app (1.01) est décorative. */
    @Test
    fun logotypeIsReadAsVersoAndTheAppIconIsDecorative() {
        compose.setContent {
            VersoTheme(theme = AppTheme.NIGHT) { EmptyLibraryScreen(onOpenSettings = {}, onImport = {}) }
        }
        compose.onNodeWithContentDescription("Verso")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(21.16.dp)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(2) // logotype et bouton Paramètres
    }

    @Test
    fun settingsButtonIsLabelledAndClickable() {
        var settings = 0
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) { EmptyLibraryScreen(onOpenSettings = { settings++ }, onImport = {}) }
        }
        compose.onNodeWithContentDescription("Paramètres").performClick()
        assertThat(settings).isEqualTo(1)
    }
}

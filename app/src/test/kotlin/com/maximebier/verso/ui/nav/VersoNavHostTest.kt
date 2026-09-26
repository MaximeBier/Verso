package com.maximebier.verso.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VersoNavHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun startsOnTheLibraryAndOpensSettings() {
        compose.setContent { VersoTheme(darkTheme = false) { VersoNavHost() } }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Votre bibliothèque est vide").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Votre bibliothèque est vide").assertIsDisplayed()
        compose.onNodeWithContentDescription("Paramètres").performClick()
        compose.onNodeWithText("Paramètres").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retour").performClick()
        compose.onNodeWithText("Votre bibliothèque est vide").assertIsDisplayed()
    }
}

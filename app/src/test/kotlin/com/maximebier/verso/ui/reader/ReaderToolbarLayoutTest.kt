package com.maximebier.verso.ui.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rangées de la barre de lecture, mesurées avec le vrai rendu du texte, sur l’écran du téléphone (390 dp). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class ReaderToolbarLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val tools = setOf("Sommaire", "Rechercher", "Notes", "Réglages")

    private var font by mutableStateOf(ReadingFont.LITERATA)
    private var fontScale by mutableFloatStateOf(1f)

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VersoTheme(theme = AppTheme.LIGHT, font = font) {
                    ReaderBars(
                        visible = true,
                        state = ReaderBarsState("Madame Bovary", listOf("Deuxième partie", "Chapitre I"), 31, 0.31f, 330),
                        onBack = {},
                        onTocClick = {},
                        onSearchClick = {},
                        onSettingsClick = {},
                    )
                }
            }
        }
    }

    /** Nombre de rangées occupées par les quatre outils. */
    private fun toolRows(): Int {
        compose.waitForIdle()
        return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString() in tools }
            .map { it.boundsInRoot.top }
            .distinct()
            .size
    }

    @Test
    fun fourToolsOnOneRowInEveryFontAtNormalSize() {
        show()
        ReadingFont.entries.forEach { choice ->
            font = choice
            assertWithMessage(choice.name).that(toolRows()).isEqualTo(1)
        }
    }

    @Test
    fun enlargedTextFallsBackToTwoColumnsThenOne() {
        show()
        fontScale = 1.5f
        assertWithMessage("150 %").that(toolRows()).isEqualTo(2)
        fontScale = 2f
        assertWithMessage("200 %").that(toolRows()).isIn(listOf(2, 4))
    }
}

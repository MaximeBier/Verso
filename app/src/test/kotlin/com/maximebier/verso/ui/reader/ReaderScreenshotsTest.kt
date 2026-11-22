package com.maximebier.verso.ui.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.screenshots.samples.ReaderBarsSample
import com.maximebier.verso.screenshots.samples.TocSheetSample
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Roborazzi (390 × 844 dp, xxhdpi) de la barre de lecture et du sommaire, en clair et en sombre.
 * Thème : celui du système (qualificatif `+night` pour le sombre).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class ReaderScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun readerBarsLight() = captureBars("1.11-lecture-barre-affichee-clair")

    @Test
    @Config(qualifiers = "+night")
    fun readerBarsDark() = captureBars("1.11-lecture-barre-affichee-sombre")

    @Test
    fun tocLight() = captureToc("1.12-sommaire-clair")

    @Test
    @Config(qualifiers = "+night")
    fun tocDark() = captureToc("1.12-sommaire-sombre")

    private fun captureBars(file: String) {
        compose.setContent { VersoTheme { ReaderBarsSample() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$file.png")
    }

    private fun captureToc(file: String) {
        compose.setContent { VersoTheme { TocSheetSample() } }
        compose.waitForIdle()
        // La feuille modale est une fenêtre à part : capture de tout l’écran, voile compris.
        captureScreenRoboImage("build/outputs/roborazzi/$file.png")
    }
}

package com.maximebier.verso.ui.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.screenshots.samples.JournalSample
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capture de toutes les fenêtres : la feuille modale est dans la sienne, au-dessus de l'écran. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class JournalSheetScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun journalLight() {
        composeRule.setContent { VersoTheme { JournalSample() } }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/1.13-journal-de-lecture-clair.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun journalDark() {
        composeRule.setContent { VersoTheme { JournalSample() } }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/1.13-journal-de-lecture-sombre.png")
    }
}

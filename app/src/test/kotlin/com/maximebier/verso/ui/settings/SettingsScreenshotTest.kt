package com.maximebier.verso.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.screenshots.samples.LicensesSample
import com.maximebier.verso.screenshots.samples.SettingsClearJournalDialogSample
import com.maximebier.verso.screenshots.samples.SettingsSample
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Convention 2.2 : captureRoboImage pour un écran seul ; captureScreenRoboImage quand le dialogue (autre fenêtre) doit y être. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class SettingsScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun settingsLight() {
        captureRoboImage("build/outputs/roborazzi/1.09-parametres-clair.png") {
            VersoTheme { SettingsSample() }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun settingsDark() {
        captureRoboImage("build/outputs/roborazzi/1.09-parametres-sombre.png") {
            VersoTheme { SettingsSample() }
        }
    }

    @Test
    fun clearJournalDialog() {
        composeRule.setContent { VersoTheme { SettingsClearJournalDialogSample() } }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/1.09-effacer-le-journal-clair.png")
    }

    @Test
    fun licenses() {
        captureRoboImage("build/outputs/roborazzi/1.09-licences-clair.png") {
            VersoTheme { LicensesSample() }
        }
    }
}

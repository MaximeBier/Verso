package com.maximebier.verso.ui.details

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.screenshots.samples.DeleteBookDialogSample
import com.maximebier.verso.screenshots.samples.DetailsSample
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class DetailsScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun details() {
        compose.setContent { VersoTheme { DetailsSample() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.07-details-du-livre.png")
    }

    @Test
    fun delete_dialog() {
        compose.setContent { VersoTheme { DeleteBookDialogSample() } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/1.08-supprimer-un-livre.png")
    }
}

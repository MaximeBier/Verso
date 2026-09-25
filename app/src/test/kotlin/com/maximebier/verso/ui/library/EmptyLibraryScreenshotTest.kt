package com.maximebier.verso.ui.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class EmptyLibraryScreenshotTest {

    @Test
    fun emptyLibraryLight() {
        captureRoboImage("build/outputs/roborazzi/1.01-premier-lancement-clair.png") {
            VersoTheme { EmptyLibraryScreen(onOpenSettings = {}, onImport = {}) }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun emptyLibraryDark() {
        captureRoboImage("build/outputs/roborazzi/1.01-premier-lancement-sombre.png") {
            VersoTheme { EmptyLibraryScreen(onOpenSettings = {}, onImport = {}) }
        }
    }
}

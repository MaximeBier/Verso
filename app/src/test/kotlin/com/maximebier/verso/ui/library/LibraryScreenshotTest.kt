package com.maximebier.verso.ui.library

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.R
import com.maximebier.verso.screenshots.samples.ImportDuplicateSample
import com.maximebier.verso.screenshots.samples.ImportRejectedSample
import com.maximebier.verso.screenshots.samples.ImportSuccessSample
import com.maximebier.verso.screenshots.samples.LibraryBookMenuSample
import com.maximebier.verso.screenshots.samples.LibraryGridSample
import com.maximebier.verso.screenshots.samples.LibraryListSample
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class LibraryScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun show(sample: @Composable () -> Unit) {
        compose.setContent { VersoTheme { sample() } }
    }

    @Test
    fun library_list() {
        show { LibraryListSample() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.02-bibliotheque-liste.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun library_list_dark() {
        show { LibraryListSample() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.02-bibliotheque-sombre.png")
    }

    @Test
    fun library_menu() {
        show { LibraryBookMenuSample() }
        compose.onNodeWithContentDescription(context.getString(R.string.library_book_options, "Vingt mille lieues sous les mers")).performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/1.02b-menu-dun-livre.png")
    }

    @Test
    fun library_grid() {
        show { LibraryGridSample() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.03-bibliotheque-grille.png")
    }

    @Test
    fun import_success_snackbar() {
        compose.mainClock.autoAdvance = false
        show { ImportSuccessSample() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.04-import-reussi.png")
    }

    @Test
    fun duplicate_dialog() {
        show { ImportDuplicateSample() }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/1.05-livre-deja-importe.png")
    }

    @Test
    fun rejected_dialog() {
        show { ImportRejectedSample() }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/1.06-fichier-refuse.png")
    }

    // Vérification « texte à 200 % sans coupure » (fix round 1) : en-tête et pourcentage de statut.
    @Test
    @Config(qualifiers = "+w390dp-h844dp-xxhdpi")
    fun library_list_font_scale_200() {
        RuntimeEnvironment.setFontScale(2f)
        show { LibraryListSample() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.02-bibliotheque-liste-200pc.png")
        // Défile jusqu'à une ligne de livre « en cours » pour vérifier aussi le pourcentage de BookStatusLine.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(4)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.02-bibliotheque-liste-200pc-defile.png")
    }

    @Test
    @Config(qualifiers = "+w390dp-h844dp-xxhdpi")
    fun library_grid_font_scale_200() {
        RuntimeEnvironment.setFontScale(2f)
        show { LibraryGridSample() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.03-bibliotheque-grille-200pc.png")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(5)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.03-bibliotheque-grille-200pc-defile.png")
    }
}

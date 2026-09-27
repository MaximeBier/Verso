package com.maximebier.verso.ui.reader

import com.maximebier.verso.data.AppTheme
import android.content.Context
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.screenshots.samples.ReturnCardSample
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class ReturnCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun show(dark: Boolean = false, onStay: () -> Unit = {}, onGoBack: () -> Unit = {}) {
        compose.setContent {
            VersoTheme(theme = if (dark) AppTheme.DARK else AppTheme.LIGHT) { ReturnCardSample(onStayHere = onStay, onGoBack = onGoBack) }
        }
    }

    @Test
    fun showsTitleAndReadingPosition() {
        show()

        compose.onNodeWithText(context.getString(R.string.return_card_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.return_card_position, "Partie II, chap. I", 31)).assertIsDisplayed()
    }

    @Test
    fun buttonsInvokeCallbacksAndAreAtLeast48Dp() {
        var stay = 0
        var back = 0
        show(onStay = { stay++ }, onGoBack = { back++ })

        compose.onNodeWithText(context.getString(R.string.return_card_stay)).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(context.getString(R.string.return_card_go_back)).assertHeightIsAtLeast(48.dp).performClick()

        assertThat(stay).isEqualTo(1)
        assertThat(back).isEqualTo(1)
    }

    @Test
    fun cardIsAnnouncedPolitelyByTalkBack() {
        show()

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
    }

    @Test
    fun screenshotLight() {
        show()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.14-retour-a-votre-lecture-clair.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun screenshotDark() {
        show(dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1.14-retour-a-votre-lecture-sombre.png")
    }
}

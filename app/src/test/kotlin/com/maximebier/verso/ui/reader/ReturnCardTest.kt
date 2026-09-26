package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            VersoTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
                    ReturnCard(
                        location = "Partie II, chap. I",
                        percent = 31,
                        onStayHere = onStay,
                        onGoBack = onGoBack,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    )
                }
            }
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

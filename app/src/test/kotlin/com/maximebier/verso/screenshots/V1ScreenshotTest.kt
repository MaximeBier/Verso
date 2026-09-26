package com.maximebier.verso.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Convention 2.2 (390 × 844 dp, xxhdpi, sortie dans build/outputs/roborazzi) ; captureScreenRoboImage plutôt que
 * captureRoboImage parce que menus, dialogues et feuilles modales sont dans leur propre fenêtre.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class V1ScreenshotTest(private val fixture: ScreenFixture, private val variant: ScreenVariant) {

    private val composeRule = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(VariantRule(variant)).around(composeRule)

    @Test
    fun capture() {
        fixture.show(composeRule) { content -> VersoTheme { content() } }
        captureScreenRoboImage("build/outputs/roborazzi/v1/${fixture.id}-${variant.suffix}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun parameters(): List<Array<Any>> =
            V1ScreenCatalog.fixtures.flatMap { fixture -> ScreenVariant.entries.map { arrayOf<Any>(fixture, it) } }
    }
}

package com.maximebier.verso.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures V2 : chaque écran dans chacune de ses palettes, plus le texte Android à 200 % dans la palette claire
 * (ou la première de la liste). Même convention que V1ScreenshotTest (390 × 844 dp, xxhdpi).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class V2ScreenshotTest(
    private val fixture: V2ScreenFixture,
    private val theme: AppTheme,
    private val variant: ScreenVariant,
) {
    private val composeRule = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(VariantRule(variant)).around(composeRule)

    @Test
    fun capture() {
        fixture.screen.show(composeRule) { content -> VersoTheme(theme = theme) { content() } }
        val suffix = if (variant == ScreenVariant.FONT_200) "${theme.fileSuffix}-texte-200" else theme.fileSuffix
        captureScreenRoboImage("build/outputs/roborazzi/v2/${fixture.screen.id}-$suffix.png")
    }

    /** Suffixe des fichiers : clair, sepia, sombre, noir. */
    private val AppTheme.fileSuffix: String
        get() = when (this) {
            AppTheme.LIGHT -> "clair"
            AppTheme.SEPIA -> "sepia"
            AppTheme.DARK -> "sombre"
            AppTheme.BLACK -> "noir"
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun parameters(): List<Array<Any>> = V2ScreenCatalog.fixtures.flatMap { fixture ->
            fixture.themes.map { arrayOf<Any>(fixture, it, ScreenVariant.LIGHT) } +
                listOf(arrayOf<Any>(fixture, fixture.themes.first(), ScreenVariant.FONT_200))
        }
    }
}

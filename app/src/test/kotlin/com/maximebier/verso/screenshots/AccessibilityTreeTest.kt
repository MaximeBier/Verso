package com.maximebier.verso.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertWithMessage
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendu natif : sans lui, Robolectric mesure mal le texte et chaque Text passerait pour coupé en largeur. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class AccessibilityTreeTest(private val fixture: ScreenFixture, private val variant: ScreenVariant) {

    private val composeRule = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(VariantRule(variant)).around(composeRule)

    @Test
    fun semanticTreeIsAccessible() {
        fixture.show(composeRule) { content -> VersoTheme { content() } }

        assertWithMessage("${fixture.id} (${variant.suffix})")
            .that(AccessibilityChecks.violations(composeRule))
            .isEmpty()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun parameters(): List<Array<Any>> =
            V1ScreenCatalog.fixtures.flatMap { fixture ->
                listOf(ScreenVariant.LIGHT, ScreenVariant.FONT_200).map { arrayOf<Any>(fixture, it) }
            }
    }
}

package com.maximebier.verso.ui.settings

import android.content.Context
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.screenshots.samples.LicensesSample
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class LicensesScreenTest {

    @get:Rule val composeRule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyLicenseTextIsBundledAndNotBlank() {
        assertThat(OpenSourceLicenses.entries).hasSize(13)
        OpenSourceLicenses.entries.forEach { entry ->
            val text = ctx.resources.openRawResource(entry.text).bufferedReader().use { it.readText() }
            assertThat(text.trim()).isNotEmpty()
        }
    }

    @Test
    fun listsReadiumFontsAndLibraries() {
        composeRule.setContent { VersoTheme { LicensesSample() } }

        composeRule.onNodeWithText(ctx.getString(R.string.settings_licenses)).assertExists()
        listOf(
            R.string.license_component_readium, R.string.license_component_atkinson,
            R.string.license_component_libron,
            R.string.license_component_androidx, R.string.license_component_coil,
            R.string.license_component_okio, R.string.license_component_jspecify,
        ).forEach {
            // Liste paresseuse : les dernières lignes ne sont composées qu'une fois amenées à l'écran.
            composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ctx.getString(it), substring = true))
            composeRule.onNodeWithText(ctx.getString(it), substring = true).assertExists()
        }
    }

    @Test
    fun tappingAnEntryShowsItsFullText() {
        composeRule.setContent { VersoTheme { LicensesSample() } }

        composeRule.onNodeWithText(ctx.getString(R.string.license_component_readium), substring = true)
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Redistribution and use in source and binary forms", substring = true).assertExists()
    }
}

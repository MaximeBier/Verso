package com.maximebier.verso.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.BuildConfig
import com.maximebier.verso.R
import com.maximebier.verso.data.DarkThemeVariant
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class SettingsScreenTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private var reopen by mutableStateOf(true)
    private var confirming by mutableStateOf(false)
    private var theme by mutableStateOf(ThemeMode.AUTO)
    private var darkVariant by mutableStateOf(DarkThemeVariant.DARK)
    private val events = mutableListOf<String>()

    private fun show() {
        composeRule.setContent {
            VersoTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        reopenLastBook = reopen,
                        themeMode = theme,
                        darkThemeVariant = darkVariant,
                        confirmingClearJournal = confirming,
                    ),
                    versionName = "1.0.0",
                    onBack = { events += "back" },
                    onReopenLastBookChange = { reopen = it; events += "reopen=$it" },
                    onThemeModeChange = { theme = it; events += "theme=$it" },
                    onDarkThemeVariantChange = { darkVariant = it; events += "dark=$it" },
                    onClearJournalClick = { confirming = true; events += "clear?" },
                    onClearJournalConfirm = { confirming = false; events += "clear!" },
                    onClearJournalDismiss = { confirming = false; events += "dismiss" },
                    onOpenSourceCode = { events += "source" },
                    onOpenLicenses = { events += "licenses" },
                )
            }
        }
    }

    private fun themeRow(label: Int) = composeRule.onNode(
        hasText(ctx.getString(label)) and hasAnyAncestor(hasContentDescription(ctx.getString(R.string.settings_theme))),
    )

    private fun darkVariantSegment(label: Int) = composeRule.onNode(
        hasText(ctx.getString(label)) and hasAnyAncestor(hasContentDescription(ctx.getString(R.string.settings_dark_theme))),
    )

    @Test
    fun reopenSwitchExposesSwitchRoleAndState() {
        show()
        val row = composeRule.onNode(hasText(ctx.getString(R.string.settings_reopen_last_book)) and isToggleable())

        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        row.assertIsOn()
        row.performClick()
        row.assertIsOff()
        assertThat(events).containsExactly("reopen=false")
    }

    @Test
    fun themeSelectorSelectsTheChosenMode() {
        show()
        themeRow(R.string.settings_theme_auto).assertIsSelected()
        themeRow(R.string.settings_theme_dark).performScrollTo().performClick()
        themeRow(R.string.settings_theme_dark).assertIsSelected()
        assertThat(events).containsExactly("theme=DARK")
    }

    @Test
    fun themeSelectorOffersTheFiveThemesAsRadioButtons() {
        show()
        val labels = listOf(
            R.string.settings_theme_auto, R.string.settings_theme_light, R.string.settings_theme_sepia,
            R.string.settings_theme_dark, R.string.settings_theme_night,
        ).map(ctx::getString)
        labels.forEach { label ->
            composeRule.onNode(hasText(label) and hasAnyAncestor(hasContentDescription(ctx.getString(R.string.settings_theme))))
                .performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertHeightIsAtLeast(48.dp)
        }
        themeRow(R.string.settings_theme_sepia).performClick()
        themeRow(R.string.settings_theme_sepia).assertIsSelected()
        themeRow(R.string.settings_theme_night).performScrollTo().performClick()
        assertThat(events).containsExactly("theme=SEPIA", "theme=NIGHT").inOrder()
    }

    /** 1.09 : « Thème sombre », Sombre coché par défaut, Nuit au choix ; le libellé est celui du groupe pour TalkBack. */
    @Test
    fun darkThemeVariantIsASegmentedChoice() {
        show()
        darkVariantSegment(R.string.settings_dark_theme_dark).performScrollTo().assertIsSelected().assertHeightIsAtLeast(48.dp)
        darkVariantSegment(R.string.settings_dark_theme_night).assertIsNotSelected().performClick()
        darkVariantSegment(R.string.settings_dark_theme_night).assertIsSelected()
        darkVariantSegment(R.string.settings_dark_theme_dark).assertIsNotSelected()
        assertThat(events).containsExactly("dark=NIGHT")
    }

    @Test
    fun showsAllSectionsAndTheVersion() {
        show()
        listOf(
            R.string.settings_section_startup, R.string.settings_section_display, R.string.settings_display_body,
            R.string.settings_section_privacy, R.string.settings_privacy_body, R.string.settings_clear_journal,
            R.string.settings_section_about, R.string.settings_source_code, R.string.settings_licenses,
        ).forEach { composeRule.onNodeWithText(ctx.getString(it), substring = true).performScrollTo().assertExists() }
        // À propos : « Verso » puis « Version 1.0.0 », lus d'un seul tenant par TalkBack.
        composeRule.onNode(hasText(ctx.getString(R.string.app_name)) and hasText(ctx.getString(R.string.settings_version, "1.0.0")))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun clearJournalAsksForConfirmation() {
        show()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_clear_journal), substring = true).performScrollTo().performClick()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_clear_journal_dialog_title)).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_clear_journal_dialog_body)).assertExists()

        composeRule.onNodeWithText(ctx.getString(R.string.settings_clear_journal_dialog_confirm)).performClick()

        assertThat(events).containsExactly("clear?", "clear!").inOrder()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_clear_journal_dialog_title)).assertDoesNotExist()
    }

    @Test
    fun sourceCodeAndLicensesRowsAreActions() {
        show()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_source_code), substring = true).performScrollTo().performClick()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_licenses), substring = true).performScrollTo().performClick()
        assertThat(events).containsExactly("source", "licenses").inOrder()
    }

    @Test
    fun openUrlStartsAViewIntentForTheBrowser() {
        val url = ctx.getString(R.string.settings_source_code_url)

        openUrl(composeRule.activity, url)

        val started = shadowOf(composeRule.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.dataString).isEqualTo("https://github.com/MaximeBier/Verso")
    }

    @Test
    fun versionComesFromBuildConfig() {
        assertThat(BuildConfig.VERSION_NAME).matches("""\d+\.\d+\.\d+""")
    }
}

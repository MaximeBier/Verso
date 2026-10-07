package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ReadingSettingsLimits
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingSettingsSheetTest {

    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    /** Contenu de la feuille branché sur un état local, comme le fait ReaderScreen avec le ViewModel. */
    private fun show(
        initial: ReadingSettingsSheetState = ReadingSettingsSheetState(ReadingSettings(), ThemeMode.AUTO, ScrollMode.CONTINUOUS),
    ): () -> ReadingSettingsSheetState {
        var state by mutableStateOf(initial)
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                ReadingSettingsContent(
                    state = state,
                    onFontSelected = { state = state.copy(settings = state.settings.copy(font = it)) },
                    onSmaller = { state = state.copy(settings = state.settings.smaller()) },
                    onLarger = { state = state.copy(settings = state.settings.larger()) },
                    onThemeSelected = { state = state.copy(themeMode = it) },
                    onLineSpacingSelected = { state = state.copy(settings = state.settings.copy(lineSpacing = it)) },
                    onMarginsSelected = { state = state.copy(settings = state.settings.copy(margins = it)) },
                    onClose = {},
                )
            }
        }
        return { state }
    }

    @Test
    fun defaultsAreSelectedAsInTheMockup() {
        show()
        compose.onNodeWithText(s(R.string.reader_settings_font_literata)).assertIsSelected()
        compose.onNodeWithText(s(R.string.reader_settings_theme_auto)).assertIsSelected()
        compose.onNodeWithText("20").assertExists()
        compose.onNodeWithText(s(R.string.reader_settings_line_spacing_normal)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(s(R.string.reader_settings_margins_normal)).performScrollTo().assertIsSelected()
    }

    @Test
    fun everyChoiceIsAppliedAtOnce() {
        val state = show()
        compose.onNodeWithText(s(R.string.reader_settings_font_atkinson)).performClick()
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_larger)).performClick()
        compose.onNodeWithText(s(R.string.reader_settings_theme_sepia)).performClick()
        compose.onNodeWithText(s(R.string.reader_settings_line_spacing_airy)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.reader_settings_margins_wide)).performScrollTo().performClick()

        assertThat(state()).isEqualTo(
            ReadingSettingsSheetState(
                ReadingSettings(font = ReadingFont.ATKINSON, fontSizeSp = 21, lineSpacing = LineSpacing.AIRY, margins = Margins.WIDE),
                ThemeMode.SEPIA,
                ScrollMode.CONTINUOUS,
            ),
        )
        compose.onNodeWithText("21").assertExists()
    }

    @Test
    fun libronCanBeChosen() {
        val state = show()
        compose.onNodeWithText(s(R.string.reader_settings_font_libron)).performClick().assertIsSelected()
        assertThat(state().settings.font).isEqualTo(ReadingFont.LIBRON)
    }

    @Test
    fun scrollModeRowSaysItIsForThisBook() {
        var chosen: ScrollMode? = null
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                ReadingSettingsContent(
                    state = ReadingSettingsSheetState(ReadingSettings(), ThemeMode.AUTO, ScrollMode.CONTINUOUS),
                    onFontSelected = {}, onSmaller = {}, onLarger = {}, onThemeSelected = {},
                    onLineSpacingSelected = {}, onMarginsSelected = {}, onClose = {},
                    scrollModeRow = { ScrollModeRow(selected = ScrollMode.CONTINUOUS, onSelect = { chosen = it }) },
                )
            }
        }
        compose.onNodeWithText(s(R.string.reader_settings_scroll)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.reader_settings_scroll_for_this_book)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.reader_settings_scroll_continuous)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(s(R.string.reader_settings_scroll_pages)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertThat(chosen).isEqualTo(ScrollMode.PAGES)
    }

    @Test
    fun sectionsAreInTheChosenOrder() {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                ReadingSettingsContent(
                    state = ReadingSettingsSheetState(ReadingSettings(), ThemeMode.AUTO, ScrollMode.CONTINUOUS),
                    onFontSelected = {}, onSmaller = {}, onLarger = {}, onThemeSelected = {},
                    onLineSpacingSelected = {}, onMarginsSelected = {}, onClose = {},
                    scrollModeRow = { ScrollModeRow(selected = ScrollMode.CONTINUOUS, onSelect = {}) },
                )
            }
        }
        // Thème, Défilement, Police, Taille, Interligne, Marges (décision de Maxime, 2026-10-07).
        val tops = listOf(
            R.string.reader_settings_theme,
            R.string.reader_settings_scroll,
            R.string.reader_settings_font,
            R.string.reader_settings_text_size,
            R.string.reader_settings_line_spacing,
            R.string.reader_settings_margins,
        ).map { compose.onNodeWithText(s(it), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y }
        assertThat(tops).isInStrictOrder()
    }

    @Test
    fun sizeButtonsAreDisabledAtTheLimits() {
        show(ReadingSettingsSheetState(ReadingSettings(fontSizeSp = ReadingSettingsLimits.MAX_FONT_SIZE_SP), ThemeMode.AUTO, ScrollMode.CONTINUOUS))
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_larger)).assertIsNotEnabled()
    }

    @Test
    fun controlsAreAtLeast48DpAndIconButtonsAreLabelled() {
        show()
        compose.onNodeWithContentDescription(s(R.string.common_close)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_smaller)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_larger)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.reader_settings_font_system)).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.reader_settings_theme_night)).assertHeightIsAtLeast(48.dp)
    }
}

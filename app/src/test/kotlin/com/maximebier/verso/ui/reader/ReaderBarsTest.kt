package com.maximebier.verso.ui.reader

import com.maximebier.verso.data.AppTheme
import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.ui.common.remainingTimeText
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderBarsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val state = ReaderBarsState(
        bookTitle = "Madame Bovary",
        chapterPath = listOf("Deuxième partie", "Chapitre I"),
        readingPercent = 31,
        progression = 0.31f,
        remainingMinutes = 330,
    )

    private fun show(
        visible: Boolean,
        onBack: () -> Unit = {},
        onToc: () -> Unit = {},
        onJournal: () -> Unit = {},
        onSettings: () -> Unit = {},
    ) {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                ReaderBars(
                    visible = visible,
                    state = state,
                    onBack = onBack,
                    onTocClick = onToc,
                    onJournalClick = onJournal,
                    onSettingsClick = onSettings,
                )
            }
        }
    }

    @Test
    fun hiddenBarsShowNothing() {
        show(visible = false)

        compose.onNodeWithText(context.getString(R.string.reader_toc)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_back_to_library)).assertDoesNotExist()
    }

    @Test
    fun visibleBarsShowTitleChapterProgressAndTimeLeft() {
        show(visible = true)

        compose.onNodeWithText("Madame Bovary").assertIsDisplayed()
        compose.onNodeWithText("Deuxième partie, chapitre I").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_percent_read, 31)).assertIsDisplayed()
        val duration = context.getString(R.string.common_duration_hours_minutes, 5, 30)
        compose.onNodeWithText(context.getString(R.string.common_time_remaining, duration)).assertIsDisplayed()
    }

    @Test
    fun buttonsInvokeCallbacksAndAreAtLeast48Dp() {
        var back = 0
        var toc = 0
        var journal = 0
        var settings = 0
        show(visible = true, onBack = { back++ }, onToc = { toc++ }, onJournal = { journal++ }, onSettings = { settings++ })

        compose.onNodeWithContentDescription(context.getString(R.string.reader_back_to_library))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNode(hasText(context.getString(R.string.reader_toc)) and hasClickAction())
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNode(hasText(context.getString(R.string.reader_journal)) and hasClickAction())
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNode(hasText(context.getString(R.string.reader_settings)) and hasClickAction())
            .assertHeightIsAtLeast(48.dp).performClick()

        assertThat(listOf(back, toc, journal, settings)).containsExactly(1, 1, 1, 1).inOrder()
    }

    @Test
    fun barShowsThreeToolsUntilSearchArrives() {
        show(visible = true)
        // Étape 11 : Sommaire, Journal, Réglages ; Rechercher s'ajoute à l'étape 15 (jamais de commande inactive).
        compose.onAllNodes(hasClickAction() and hasText(context.getString(R.string.reader_settings))).assertCountEquals(1)
        compose.onAllNodes(hasClickAction() and hasText("Rechercher")).assertCountEquals(0)
    }

    @Test
    fun settingsGlyphIsHiddenFromTalkBack() {
        show(visible = true)
        // Le bouton se lit « Réglages, bouton », jamais « Aa ».
        compose.onNode(hasText(context.getString(R.string.reader_settings)) and hasClickAction())
            .assert(hasText(context.getString(R.string.reader_settings_glyph)).not())
    }

    @Test
    fun longChapterLabelKeepsRomanNumerals() {
        var withWord: String? = null
        var roman: String? = null
        var alone: String? = null
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                withWord = chapterLongLabel(listOf("Deuxième partie", "Chapitre I"))
                roman = chapterLongLabel(listOf("Deuxième partie", "IV"))
                alone = chapterLongLabel(listOf("Préface"))
            }
        }
        compose.waitForIdle()

        assertThat(withWord).isEqualTo("Deuxième partie, chapitre I")
        assertThat(roman).isEqualTo("Deuxième partie, IV")
        assertThat(alone).isEqualTo("Préface")
    }

    @Test
    fun remainingTimeUsesFrenchDurations() {
        val labels = mutableListOf<String>()
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                labels.clear()
                labels += remainingTimeText(0)
                labels += remainingTimeText(45)
                labels += remainingTimeText(120)
                labels += remainingTimeText(65)
                labels += remainingTimeText(1)
                labels += remainingTimeText(60)
            }
        }
        compose.waitForIdle()

        assertThat(labels).containsExactly(
            context.getString(R.string.common_time_remaining_less_than_minute),
            context.getString(R.string.common_time_remaining, context.getString(R.string.common_duration_minutes, 45)),
            context.getString(R.string.common_time_remaining, context.getString(R.string.common_duration_hours, 2)),
            context.getString(R.string.common_time_remaining, context.getString(R.string.common_duration_hours_minutes, 1, 5)),
            context.getString(R.string.common_time_remaining_one, context.getString(R.string.common_duration_minutes, 1)),
            context.getString(R.string.common_time_remaining_one, context.getString(R.string.common_duration_hours, 1)),
        ).inOrder()
    }
}

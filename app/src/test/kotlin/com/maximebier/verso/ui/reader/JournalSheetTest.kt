package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.screenshots.samples.JournalEmptySample
import com.maximebier.verso.screenshots.samples.JournalSample
import com.maximebier.verso.screenshots.samples.journalSampleState
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class JournalSheetTest {

    @get:Rule val composeRule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun clock(h: Int, m: Int) = ctx.getString(R.string.common_clock_time, h, m)

    @Test
    fun positionOfLocatorJsonGivesTheFileWithoutFragmentAndTheProgressionInside() {
        val json = """{"href":"OEBPS/p1.xhtml#c2","type":"application/xhtml+xml","locations":{"progression":0.42,"totalProgression":0.1}}"""

        assertThat(positionOfLocatorJson(json)).isEqualTo(ResourcePosition("OEBPS/p1.xhtml", 0.42))
        assertThat(positionOfLocatorJson("pas du json")).isNull()
    }

    @Test
    fun showsHeaderDayGroupsAndSessionDetails() {
        composeRule.setContent { VersoTheme { JournalSample() } }

        composeRule.onNodeWithText(ctx.getString(R.string.journal_title)).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.journal_subtitle)).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.journal_today)).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.journal_yesterday)).assertExists()
        composeRule.onNodeWithText("Lundi 21 septembre").assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.journal_time_range, clock(21, 5), clock(21, 36)), substring = true).assertExists()
        composeRule.onNodeWithText("Partie I, chap. VI → VIII", substring = true).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.journal_percent_range, 18, 25), substring = true).assertExists()
    }

    @Test
    fun sessionInProgressIsMarkedAndHasNoAction() {
        composeRule.setContent { VersoTheme { JournalSample() } }

        val since = ctx.getString(R.string.journal_since, clock(22, 10))
        composeRule.onNodeWithText(since, substring = true).assertHasNoClickAction()
        composeRule.onNodeWithText(ctx.getString(R.string.journal_in_progress), substring = true).assertExists()
    }

    @Test
    fun tappingFinishedSessionResumesAtItsEnd() {
        var resumed: BookPosition? = null
        composeRule.setContent { VersoTheme { JournalSample(onResume = { resumed = it }) } }

        val yesterdayRange = ctx.getString(R.string.journal_time_range, clock(21, 5), clock(21, 36))
        composeRule.onNodeWithText(yesterdayRange, substring = true)
            .assertHasClickAction()
            .performClick()

        val expected = journalSampleState.days[1].sessions.single().resumeTarget
        assertThat(resumed).isEqualTo(expected)
    }

    @Test
    fun closeButtonOfTheSheetCallsOnDismiss() {
        var closed = false
        composeRule.setContent { VersoTheme { JournalSample(onDismiss = { closed = true }) } }

        composeRule.onNodeWithText(ctx.getString(R.string.journal_title)).assertExists()
        composeRule.onNodeWithContentDescription(ctx.getString(R.string.common_close)).performClick()

        assertThat(closed).isTrue()
    }

    @Test
    fun emptyJournalShowsExplanation() {
        composeRule.setContent { VersoTheme { JournalEmptySample() } }

        composeRule.onNodeWithText(ctx.getString(R.string.journal_empty)).assertExists()
    }
}

package com.maximebier.verso.ui.reader.search

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.readium.SearchHit
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val hit = SearchHit(
        locator = Locator(href = Url("c1.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(totalProgression = 0.30)),
        chapter = "I",
        before = "…petite ",
        match = "rivière",
        after = " qui se jette…",
        progression = 0.30,
    )

    private fun show(state: SearchUiState, events: MutableList<String> = mutableListOf()) {
        rule.setContent {
            VersoTheme {
                SearchScreen(
                    state = state,
                    onQueryChange = { events += "query:$it" },
                    onClear = { events += "clear" },
                    onClose = { events += "close" },
                    onResultClick = { events += "result:${it.progression}" },
                    requestFocus = false,
                )
            }
        }
    }

    @Test
    fun matchIsBoldUnderlinedOnHighlight() {
        val text = searchMatchText(hit, highlight = Color.Yellow)
        assertThat(text.text).isEqualTo("…petite rivière qui se jette…")
        val span = text.spanStyles.single()
        assertThat(span.start).isEqualTo("…petite ".length)
        assertThat(span.end).isEqualTo("…petite rivière".length)
        assertThat(span.item.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(span.item.textDecoration).isEqualTo(TextDecoration.Underline)
        assertThat(span.item.background).isEqualTo(Color.Yellow)
    }

    @Test
    fun runningSearchShowsCountGroupAndPercent() {
        val state = SearchUiState(query = "rivière", running = true, groups = listOf(SearchGroup("Deuxième partie, chapitre I", listOf(hit))), resultCount = 3, progress = 0.45f)
        show(state)
        rule.onNodeWithText("Recherche dans tout le livre… 3 résultats pour l’instant").assertIsDisplayed()
        rule.onNodeWithText("Deuxième partie, chapitre I").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.common_percent, 30)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.search_in_progress)).assertIsDisplayed()
    }

    @Test
    fun finishedSearchWithoutResultSaysSo() {
        show(SearchUiState(query = "xylophone", done = true))
        rule.onNodeWithText(context.getString(R.string.search_status_done_none)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.search_in_progress)).assertDoesNotExist()
    }

    @Test
    fun oneResultUsesTheSingular() {
        show(SearchUiState(query = "Yonville", done = true, groups = listOf(SearchGroup("I", listOf(hit))), resultCount = 1, progress = 1f))
        rule.onNodeWithText("1 résultat dans le livre").assertIsDisplayed()
    }

    @Test
    fun buttonsAndFieldReportEvents() {
        val events = mutableListOf<String>()
        show(SearchUiState(query = "riv", groups = listOf(SearchGroup("I", listOf(hit))), resultCount = 1, done = true), events)
        rule.onNode(hasSetTextAction()).performTextInput("i")
        rule.onNodeWithContentDescription(context.getString(R.string.search_clear)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.search_close)).performClick()
        rule.onNodeWithText("…petite rivière qui se jette…").performClick()
        assertThat(events.first()).startsWith("query:")
        assertThat(events.drop(1)).containsExactly("clear", "close", "result:0.3").inOrder()
    }
}

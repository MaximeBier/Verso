package com.maximebier.verso.ui.reader

import com.maximebier.verso.data.AppTheme
import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.core.text.preorder
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TocSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Préordre : 0 Première partie, 1 Chapitre I, 2 Chapitre II, 3 Deuxième partie, 4 Chapitre I, 5 Chapitre II
    private val toc = listOf(
        TocNode(
            "Première partie", "p1.xhtml",
            listOf(TocNode("Chapitre I", "p1c1.xhtml"), TocNode("Chapitre II", "p1c2.xhtml")),
        ),
        TocNode(
            "Deuxième partie", "p2.xhtml",
            listOf(TocNode("Chapitre I", "p2c1.xhtml"), TocNode("Chapitre II", "p2c2.xhtml")),
        ),
    )
    private val readingOrder = listOf("p1.xhtml", "p1c1.xhtml", "p1c2.xhtml", "p2.xhtml", "p2c1.xhtml", "p2c2.xhtml")

    @Test
    fun rowsMarkChaptersBeforeCurrentAsRead() {
        val rows = buildTocRows(toc, readingOrder, currentHref = "p2c1.xhtml")

        assertThat(rows).containsExactly(
            TocRow.PartHeader("Première partie"),
            TocRow.Chapter(1, "Chapitre I", 0, ChapterStatus.READ),
            TocRow.Chapter(2, "Chapitre II", 0, ChapterStatus.READ),
            TocRow.PartHeader("Deuxième partie"),
            TocRow.Chapter(4, "Chapitre I", 0, ChapterStatus.CURRENT),
            TocRow.Chapter(5, "Chapitre II", 0, ChapterStatus.UNREAD),
        ).inOrder()
    }

    @Test
    fun partPageCountsAsPreviousChapterForCurrent() {
        // Sur la page de titre de la deuxième partie, le dernier chapitre commencé est le chapitre II de la première.
        val rows = buildTocRows(toc, readingOrder, currentHref = "p2.xhtml")

        assertThat(rows.filterIsInstance<TocRow.Chapter>().map { it.status })
            .containsExactly(ChapterStatus.READ, ChapterStatus.CURRENT, ChapterStatus.UNREAD, ChapterStatus.UNREAD)
            .inOrder()
    }

    /** Façon Gutenberg (Madame Bovary) : sommaire plat, chapitres ancrés, plusieurs par fichier. */
    private val anchored = listOf(
        TocNode("I", "a.xhtml", progression = 0.10),
        TocNode("II", "a.xhtml", progression = 0.50),
        TocNode("III", "b.xhtml", progression = 0.30),
        TocNode("IV", "b.xhtml", progression = 0.80),
    )

    private fun statusesAt(href: String?, progression: Double?) =
        buildTocRows(anchored, listOf("a.xhtml", "b.xhtml"), href, progression)
            .map { (it as TocRow.Chapter).status }

    @Test
    fun anchoredChaptersFollowTheProgressionInsideTheFile() {
        // Défaut C : à 0 %, le sommaire surlignait la dernière entrée du fichier et marquait les autres « Lu ».
        assertThat(statusesAt("a.xhtml", 0.20))
            .containsExactly(ChapterStatus.CURRENT, ChapterStatus.UNREAD, ChapterStatus.UNREAD, ChapterStatus.UNREAD).inOrder()
        assertThat(statusesAt("a.xhtml", 0.60))
            .containsExactly(ChapterStatus.READ, ChapterStatus.CURRENT, ChapterStatus.UNREAD, ChapterStatus.UNREAD).inOrder()
        assertThat(statusesAt("b.xhtml", 0.50))
            .containsExactly(ChapterStatus.READ, ChapterStatus.READ, ChapterStatus.CURRENT, ChapterStatus.UNREAD).inOrder()
        assertThat(statusesAt("b.xhtml", 0.90))
            .containsExactly(ChapterStatus.READ, ChapterStatus.READ, ChapterStatus.READ, ChapterStatus.CURRENT).inOrder()
    }

    @Test
    fun beforeTheFirstAnchorOfAFileThePreviousChapterIsCurrent() {
        assertThat(statusesAt("b.xhtml", 0.10))
            .containsExactly(ChapterStatus.READ, ChapterStatus.CURRENT, ChapterStatus.UNREAD, ChapterStatus.UNREAD).inOrder()
    }

    @Test
    fun anchorNotFoundIsChosenLikeTheTopBarDoes() {
        // « II » : ancre introuvable dans le fichier (progression null = début du fichier).
        val toc = listOf(
            TocNode("I", "a.xhtml", progression = 0.10),
            TocNode("II", "a.xhtml"),
            TocNode("III", "a.xhtml", progression = 0.60),
        )
        val current = buildTocRows(toc, listOf("a.xhtml"), "a.xhtml", 0.30)
            .filterIsInstance<TocRow.Chapter>().single { it.status == ChapterStatus.CURRENT }

        assertThat(listOf(current.title)).isEqualTo(chapterPathAt(toc, "a.xhtml", 0.30))
    }

    @Test
    fun beforeTheFirstAnchorOfTheBookNothingIsReadOrCurrent() {
        assertThat(statusesAt("a.xhtml", 0.0)).containsExactly(
            ChapterStatus.UNREAD, ChapterStatus.UNREAD, ChapterStatus.UNREAD, ChapterStatus.UNREAD,
        )
    }

    @Test
    fun flatTocWithoutCurrentHasNoReadChapter() {
        val flat = listOf(TocNode("Un", "1.xhtml"), TocNode("Deux", "2.xhtml"))

        val rows = buildTocRows(flat, listOf("1.xhtml", "2.xhtml"), currentHref = null)

        assertThat(rows.map { (it as TocRow.Chapter).status }).containsExactly(ChapterStatus.UNREAD, ChapterStatus.UNREAD)
        assertThat(tocSummary(flat)).isEqualTo(TocSummary(parts = 0, chapters = 2))
        assertThat(tocSummary(toc)).isEqualTo(TocSummary(parts = 2, chapters = 4))
    }

    @Test
    fun preorderVisitsParentBeforeChildren() {
        assertThat(preorder(toc) { it.children }.map { it.href })
            .containsExactly("p1.xhtml", "p1c1.xhtml", "p1c2.xhtml", "p2.xhtml", "p2c1.xhtml", "p2c2.xhtml")
            .inOrder()
    }

    @Test
    fun currentChapterIsSelectedAndShowsProgress() {
        show(buildTocRows(toc, readingOrder, "p2c1.xhtml"))

        compose.onNodeWithText(context.getString(R.string.toc_chapter_current, 31)).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.toc_chapter_current, 31)), useUnmergedTree = false)
            .assertIsSelected()
    }

    @Test
    fun tappingChapterReportsItsIndex() {
        val clicked = mutableListOf<Int>()
        show(buildTocRows(toc, readingOrder, "p2c1.xhtml"), onChapterClick = { clicked += it })

        compose.onNode(hasText("Chapitre II") and hasText(context.getString(R.string.toc_chapter_read)))
            .performClick()

        assertThat(clicked).containsExactly(2)
    }

    @Test
    fun opensScrolledToCurrentChapter() {
        val many = (1..40).map { TocNode("Chapitre $it", "c$it.xhtml") }
        show(buildTocRows(many, many.map { it.href }, currentHref = "c30.xhtml"))

        compose.onNodeWithText("Chapitre 30").assertIsDisplayed()
    }

    @Test
    fun sheetShowsTitleAndSubtitleAndClosesWithTalkBackLabel() {
        var closed = 0
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                TocSheet(
                    bookTitle = "Madame Bovary",
                    summary = tocSummary(toc),
                    rows = buildTocRows(toc, readingOrder, "p2c1.xhtml"),
                    readingPercent = 31,
                    onChapterClick = {},
                    onDismiss = { closed++ },
                )
            }
        }
        val parts = context.resources.getQuantityString(R.plurals.toc_part_count, 2, 2)
        val chapters = context.resources.getQuantityString(R.plurals.toc_chapter_count, 4, 4)
        val counts = context.getString(R.string.toc_subtitle_counts, parts, chapters)

        compose.onNodeWithText(context.getString(R.string.toc_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.toc_subtitle, "Madame Bovary", counts)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.common_close)).performClick()

        assertThat(closed).isEqualTo(1)
    }

    private fun show(rows: List<TocRow>, onChapterClick: (Int) -> Unit = {}) {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                TocChapterList(
                    rows = rows,
                    readingPercent = 31,
                    onChapterClick = onChapterClick,
                    modifier = Modifier.height(756.dp),
                )
            }
        }
    }
}

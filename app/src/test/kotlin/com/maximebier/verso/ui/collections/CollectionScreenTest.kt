package com.maximebier.verso.ui.collections

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.collections.CollectionSummary
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.ui.library.LibraryBook
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class CollectionScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun book(id: Long, title: String, status: BookStatus, progression: Double) =
        LibraryBook(id, title, "Émile Zola", null, title, status, progression, (progression * 100).toInt())

    private val books = listOf(
        book(1, "La Fortune des Rougon", BookStatus.FINISHED, 1.0),
        book(2, "La Curée", BookStatus.FINISHED, 1.0),
        book(3, "L’Assommoir", BookStatus.IN_PROGRESS, 0.4),
        book(4, "Germinal", BookStatus.IN_PROGRESS, 0.12),
        book(5, "La Bête humaine", BookStatus.TO_READ, 0.0),
    )
    private val summary = CollectionSummary(
        fraction = 0.44, percent = 44, finished = 2, inProgress = 2, toRead = 1,
        remainingMinutes = 26 * 60, resumeIndex = 2, author = "Émile Zola",
    )
    private val zola = CollectionUiState(loading = false, detail = CollectionDetail(10, "Les Rougon-Macquart", books, summary))

    private fun show(state: CollectionUiState, actions: CollectionActions = CollectionActions()) {
        compose.setContent { VersoTheme { CollectionContent(state, actions) } }
    }

    @Test
    fun headerProgressCountsAndRemainingTime() {
        show(zola)
        compose.onNodeWithText("Les Rougon-Macquart").assertIsDisplayed()
        val five = context.resources.getQuantityString(R.plurals.library_book_count, 5, 5)
        compose.onNodeWithText(text(R.string.collections_author_and_count, "Émile Zola", five)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_percent, 44)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.collection_percent_label)).assertIsDisplayed()
        val finished = context.resources.getQuantityString(R.plurals.collection_finished, 2, 2)
        val counts = listOf(finished, text(R.string.collection_in_progress, 2), text(R.string.collection_to_read, 1))
            .joinToString(text(R.string.collection_counts_separator))
        compose.onNodeWithText(counts).assertIsDisplayed()
        val duration = text(R.string.common_duration_hours, 26)
        compose.onNodeWithText(text(R.string.collection_remaining, duration)).assertIsDisplayed()
    }

    @Test
    fun resumeCardOpensTheFirstUnfinishedBook() {
        val opened = mutableListOf<Long>()
        show(zola, CollectionActions(onOpenReader = { opened += it }))
        compose.onNodeWithText(text(R.string.common_percent_read, 40)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.collection_resume_content_description, "L’Assommoir")).performClick()
        assertThat(opened).containsExactly(3L)
    }

    @Test
    fun allFinishedHidesResumeAndRemainingTime() {
        val done = summary.copy(finished = 5, inProgress = 0, toRead = 0, resumeIndex = null, remainingMinutes = 0, percent = 100, remainingKnown = false)
        show(zola.copy(detail = zola.detail!!.copy(summary = done)))
        compose.onNodeWithText(text(R.string.collection_resume)).assertDoesNotExist()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.collection_finished, 5, 5)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.collection_remaining_less_than_minute)).assertDoesNotExist()
    }

    @Test
    fun remainingTimeIsHiddenWhenWordCountsAreUnknown() {
        show(zola.copy(detail = zola.detail!!.copy(summary = summary.copy(remainingMinutes = 0, remainingKnown = false))))
        compose.onNodeWithText(text(R.string.collection_remaining_less_than_minute)).assertDoesNotExist()
    }

    @Test
    fun emptyCollectionExplainsHowToAddBooks() {
        val empty = CollectionSummary(0.0, 0, 0, 0, 0, 0, null, null)
        show(CollectionUiState(loading = false, detail = CollectionDetail(10, "Vide", emptyList(), empty)))
        compose.onNodeWithText(text(R.string.common_percent, 0)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.collection_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.collection_reorder)).assertDoesNotExist()
    }

    @Test
    fun bookMenuOpensDetailsOrRemoves() {
        val details = mutableListOf<Long>()
        val removed = mutableListOf<Long>()
        show(zola, CollectionActions(onOpenDetails = { details += it }, onRemove = { removed += it }))
        compose.onNodeWithContentDescription(text(R.string.library_book_options, "Germinal")).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.library_menu_details)).performClick()
        compose.onNodeWithContentDescription(text(R.string.library_book_options, "Germinal")).performClick()
        compose.onNodeWithText(text(R.string.collection_book_menu_remove)).performClick()
        assertThat(details).containsExactly(4L)
        assertThat(removed).containsExactly(4L)
    }

    @Test
    fun collectionMenuAndDeleteDialog() {
        var renames = 0
        var reorders = 0
        show(zola, CollectionActions(onRenameRequest = { renames++ }, onReorderStart = { reorders++ }))
        compose.onNodeWithContentDescription(text(R.string.collection_options)).performClick()
        compose.onNodeWithText(text(R.string.collection_menu_rename)).performClick()
        compose.onNodeWithContentDescription(text(R.string.collection_options)).performClick()
        compose.onNode(hasText(text(R.string.collection_menu_reorder)) and hasAnyAncestor(isPopup())).performClick()
        assertThat(renames).isEqualTo(1)
        assertThat(reorders).isEqualTo(1)
    }

    @Test
    fun deleteDialogNamesTheCollection() {
        var confirmed = 0
        show(zola.copy(dialog = CollectionDialog.Delete), CollectionActions(onDeleteConfirm = { confirmed++ }))
        compose.onNodeWithText(text(R.string.collection_delete_title, "Les Rougon-Macquart")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.collection_delete_body)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.collection_delete_confirm)).performClick()
        assertThat(confirmed).isEqualTo(1)
    }

    @Test
    fun reorderModeOffersUpAndDown() {
        val moves = mutableListOf<Pair<Int, Int>>()
        var ended = 0
        show(zola.copy(reordering = true), CollectionActions(onMove = { from, to -> moves += from to to }, onReorderEnd = { ended++ }))
        compose.onNodeWithContentDescription(text(R.string.collection_move_up, "La Fortune des Rougon")).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.collection_move_down, "La Fortune des Rougon")).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(text(R.string.collection_move_down, "La Bête humaine")).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.library_book_options, "Germinal")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.collection_reorder_done)).performScrollTo().performClick()
        assertThat(moves).containsExactly(0 to 1)
        assertThat(ended).isEqualTo(1)
    }

    @Test
    fun dragHandleMovesByWholeRows() {
        val moves = mutableListOf<Pair<Int, Int>>()
        show(zola.copy(reordering = true), CollectionActions(onMove = { from, to -> moves += from to to }))
        val handle = compose.onNodeWithContentDescription(text(R.string.collection_drag, "La Fortune des Rougon"))
        val handleHeight = handle.fetchSemanticsNode().boundsInRoot.height
        handle.performTouchInput {
            down(center)
            // Une ligne (couverture de 60 dp + marges) fait environ 1,7 poignée de 48 dp : 3,9 poignées ≈ 2 lignes et plus.
            moveBy(Offset(0f, handleHeight * 0.5f))
            moveBy(Offset(0f, handleHeight * 3.4f))
            up()
        }
        assertThat(moves).hasSize(1)
        assertThat(moves.single().first).isEqualTo(0)
        assertThat(moves.single().second).isAtLeast(2)
    }

    @Test
    fun dragHandleOffersAccessibilityActions() {
        show(zola.copy(reordering = true))
        val node = compose.onNodeWithContentDescription(text(R.string.collection_drag, "Germinal")).fetchSemanticsNode()
        val labels = node.config[SemanticsActions.CustomActions].map { it.label }
        assertThat(labels).containsExactly(text(R.string.collection_move_up, "Germinal"), text(R.string.collection_move_down, "Germinal"))
        assertThat(node.config.contains(SemanticsActions.OnClick)).isFalse()
    }
}

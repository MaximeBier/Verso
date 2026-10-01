package com.maximebier.verso.ui.library

import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.ui.collections.CollectionsUiState
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class LibraryScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)
    private val fiveHoursThirty get() = text(R.string.common_time_remaining, text(R.string.common_duration_hours_minutes, 5, 30))

    private fun show(state: LibraryUiState, actions: LibraryActions = LibraryActions()) {
        compose.setContent { VersoTheme { LibraryContent(state = state, actions = actions) } }
    }

    @Test
    fun emptyLibraryShowsSingleImportButtonAndHidesHeaderImport() {
        var imports = 0
        show(LibrarySamples.empty, LibraryActions(onImport = { imports++ }))
        compose.onNodeWithText(text(R.string.library_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_empty_import)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.library_import)).assertDoesNotExist()
        assertThat(imports).isEqualTo(1)
    }

    @Test
    fun emptyLibraryHasNoTabs() {
        show(LibrarySamples.empty)
        compose.onNodeWithText(text(R.string.library_tab_collections)).assertDoesNotExist()
    }

    @Test
    fun tabsSwitchBetweenBooksAndCollections() {
        compose.setContent {
            VersoTheme { LibraryContent(LibrarySamples.list, LibraryActions(), collections = CollectionsUiState(loading = false)) }
        }
        compose.onNodeWithText(text(R.string.library_tab_books)).assertIsSelected()
        compose.onNodeWithText(text(R.string.library_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_tab_collections)).performClick()
        compose.onAllNodesWithText(text(R.string.library_tab_collections))[0].assertIsSelected()
        compose.onNodeWithText(text(R.string.collections_new)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_title)).assertDoesNotExist()
    }

    @Test
    fun listShowsHeaderImportCountAndFullResumeCard() {
        show(LibrarySamples.list)
        compose.onNodeWithText(text(R.string.library_import)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_title)).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.library_book_count, 7, 7)).assertIsDisplayed()
        compose.onNodeWithText("Deuxième partie, chapitre I").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_percent_read, 31)).assertIsDisplayed()
        compose.onNodeWithText(fiveHoursThirty).assertIsDisplayed()
    }

    @Test
    fun gridKeepsTheCompleteResumeCard() {
        show(LibrarySamples.grid)
        compose.onNodeWithText("Deuxième partie, chapitre I").assertIsDisplayed()
        compose.onNodeWithText(fiveHoursThirty).assertIsDisplayed()
    }

    @Test
    fun optionsButtonHasTalkBackLabelAndOpensMenu() {
        var details: Long? = null
        var deleted: LibraryBook? = null
        show(LibrarySamples.list, LibraryActions(onOpenDetails = { details = it }, onDeleteRequest = { deleted = it }))

        compose.onNodeWithContentDescription(text(R.string.library_book_options, "Germinal")).performClick()
        compose.onNodeWithText(text(R.string.library_menu_details)).performClick()
        assertThat(details).isEqualTo(4L)

        compose.onNodeWithContentDescription(text(R.string.library_book_options, "Germinal")).performClick()
        compose.onNodeWithText(text(R.string.library_menu_delete)).performClick()
        assertThat(deleted?.id).isEqualTo(4L)
    }

    @Test
    fun bookMenuOffersAddToCollectionAndOpensTheSheet() {
        val sheets = mutableListOf<Long>()
        compose.setContent {
            VersoTheme {
                LibraryContent(
                    LibrarySamples.list,
                    LibraryActions(),
                    addToCollectionSheet = { bookId, _ -> SideEffect { sheets += bookId } },
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.library_book_options, "Germinal")).performClick()
        val items = listOf(R.string.library_menu_details, R.string.library_menu_add_to_collection, R.string.library_menu_delete)
            .map { compose.onNodeWithText(text(it)).fetchSemanticsNode().boundsInRoot.top }
        assertThat(items).isInOrder()
        compose.onNodeWithText(text(R.string.library_menu_add_to_collection)).performClick()
        compose.waitForIdle()
        assertThat(sheets.distinct()).containsExactly(4L)
    }

    @Test
    fun resumeCardHasItsOwnOptionsToReachTheDetails() {
        var details: Long? = null
        var opened: Long? = null
        show(LibrarySamples.list, LibraryActions(onOpenDetails = { details = it }, onOpenBook = { opened = it }))
        val book = LibrarySamples.list.resume!!.book
        compose.onNodeWithContentDescription(text(R.string.library_book_options, book.title)).performClick()
        compose.onNodeWithText(text(R.string.library_menu_details)).performClick()
        assertThat(details).isEqualTo(book.id)
        assertThat(opened).isNull()
    }

    private val sortButtonLabel
        get() = text(R.string.library_sort_button_description, text(R.string.library_sort_state_recent), text(R.string.library_view_state_list))

    @Test
    fun sortButtonOpensTheSheetWithSortAndDisplay() {
        var sort: LibrarySort? = null
        var mode: LibraryViewMode? = null
        show(LibrarySamples.list, LibraryActions(onSortChange = { sort = it }, onViewModeChange = { mode = it }))
        compose.onNodeWithText(text(R.string.library_sort_sheet_title)).assertDoesNotExist()

        compose.onNodeWithContentDescription(sortButtonLabel).assertHasClickAction().performClick()
        compose.onNodeWithText(text(R.string.library_sort_sheet_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_sort_title)).performClick()
        assertThat(sort).isEqualTo(LibrarySort.TITLE)
        compose.onNodeWithText(text(R.string.library_view_grid)).performClick()
        assertThat(mode).isEqualTo(LibraryViewMode.GRID)

        compose.onNodeWithContentDescription(text(R.string.common_close)).performClick()
        compose.onNodeWithText(text(R.string.library_sort_sheet_title)).assertDoesNotExist()
    }

    @Test
    fun sortButtonShowsTheCurrentSort() {
        show(LibrarySamples.list.copy(sort = LibrarySort.AUTHOR, viewMode = LibraryViewMode.GRID))
        val label = text(R.string.library_sort_button_description, text(R.string.library_sort_state_author), text(R.string.library_view_state_grid))
        compose.onNodeWithContentDescription(label).assertIsDisplayed()
    }

    @Test
    fun statesAreWrittenNotOnlyColored() {
        // Sans carte et sans les 3 premiers livres : Germinal, Le Grand Meaulnes, Le Rouge et le Noir, Les Misérables
        // sont tous composés (la LazyColumn ne compose que les lignes visibles).
        show(LibrarySamples.list.copy(resume = null, books = LibrarySamples.books.drop(3)))
        compose.onAllNodesWithText(text(R.string.library_state_finished)).assertCountEquals(1)
        // « À lire » est aussi le libellé de la pastille de filtre (2.07) : on ne compte que la ligne du livre
        // (le bouton de la rangée, Role.Button), pas la pastille (Role.RadioButton).
        compose.onAllNodes(hasText(text(R.string.library_state_to_read)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertCountEquals(1)
        compose.onNodeWithText(text(R.string.common_percent, 12)).assertIsDisplayed()
    }

    @Test
    fun filterChipsSelectAndReport() {
        var filter: LibraryFilter? = null
        show(LibrarySamples.list, LibraryActions(onFilterChange = { filter = it }))
        compose.onNodeWithText(text(R.string.library_filter_all)).assertIsSelected()
        compose.onNodeWithText(text(R.string.library_filter_finished)).performScrollTo().performClick()
        assertThat(filter).isEqualTo(LibraryFilter.FINISHED)
    }

    @Test
    fun emptyFilterSaysSo() {
        show(LibrarySamples.list.copy(resume = null, filter = LibraryFilter.FINISHED, books = LibrarySamples.books.take(2)))
        compose.onNodeWithText(text(R.string.library_filter_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.library_book_count, 0, 0)).assertIsDisplayed()
    }

    @Test
    fun deleteConfirmationFromMenu() {
        var confirmed = false
        show(LibrarySamples.list.copy(pendingDelete = LibrarySamples.books[3]), LibraryActions(onDeleteConfirm = { confirmed = true }))
        compose.onNodeWithText(text(R.string.details_delete_dialog_title, "Germinal")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_delete_dialog_confirm)).performClick()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun duplicateDialogButtons() {
        var ignored = false
        var replaced = false
        show(LibrarySamples.duplicate, LibraryActions(onDuplicateIgnore = { ignored = true }, onDuplicateReplace = { replaced = true }))
        compose.onNodeWithText(text(R.string.import_duplicate_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.import_duplicate_position_kept)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.import_duplicate_replace)).performClick()
        compose.onNodeWithText(text(R.string.import_duplicate_ignore)).performClick()
        assertThat(replaced).isTrue()
        assertThat(ignored).isTrue()
    }

    @Test
    fun rejectedDialogShowsReasonAndNothingAdded() {
        var dismissed = false
        show(LibrarySamples.rejected, LibraryActions(onRejectedDismiss = { dismissed = true }))
        compose.onNodeWithText(text(R.string.import_error_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.import_error_not_epub, "notes-de-cours.pdf")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.import_error_nothing_added)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.import_error_ok)).performClick()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun snackbarActionStartsTheBook() {
        var opened: Long? = null
        var shown = false
        compose.mainClock.autoAdvance = false
        show(LibrarySamples.snackbar, LibraryActions(onSnackbarAction = { opened = it }, onSnackbarShown = { shown = true }))
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(text(R.string.import_success, "Le Rouge et le Noir")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.import_success_action)).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(opened).isEqualTo(6L)
        assertThat(shown).isTrue()
    }

    @Test
    fun openFailureIsAnnouncedBySnackbar() {
        // Revue finale I3 : livre refusé par le moteur (ou fichier illisible), retour à la bibliothèque avec un message.
        var shown = false
        compose.mainClock.autoAdvance = false
        show(LibrarySamples.list.copy(openFailed = "Album illustré"), LibraryActions(onOpenFailedShown = { shown = true }))
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(text(R.string.library_open_failed, "Album illustré")).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(15_000)
        assertThat(shown).isTrue()
    }
}

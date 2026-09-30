package com.maximebier.verso.ui.details

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.ui.theme.VersoTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class DetailsScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun show(state: DetailsUiState, actions: DetailsActions = DetailsActions()) {
        compose.setContent { VersoTheme { DetailsContent(state = state, actions = actions) } }
    }

    @Test
    fun showsProgressFieldsAndMetadata() {
        show(DetailsSamples.bovary)
        compose.onNodeWithText(text(R.string.details_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_percent_read, 31)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_time_remaining, text(R.string.common_duration_hours_minutes, 5, 30))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_resume)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.details_field_title)).assertIsDisplayed()
        compose.onNodeWithText("Madame Bovary").assertIsDisplayed()
        compose.onNodeWithText("Gustave Flaubert").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_autosave_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_imported_on)).assertIsDisplayed()
        compose.onNodeWithText("12 septembre 2026").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_size_mb, "1,2")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_format_epub)).assertIsDisplayed()
        compose.onNodeWithText("madame-bovary.epub").assertIsDisplayed()
    }

    @Test
    fun statusSegmentShowsAndChangesTheState() {
        var chosen: BookStatus? = null
        show(DetailsSamples.bovary, DetailsActions(onStatusChange = { chosen = it }))
        compose.onNodeWithText(text(R.string.details_state_label)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_state_in_progress)).assertIsSelected()
        compose.onNodeWithText(text(R.string.details_state_finished)).performClick()
        assertThat(chosen).isEqualTo(BookStatus.FINISHED)
    }

    @Test
    fun neverOpenedBookOffersStart() {
        var opened: Long? = null
        show(DetailsSamples.neverOpened, DetailsActions(onOpenReader = { opened = it }))
        compose.onNodeWithText(text(R.string.common_resume)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.common_start)).performClick()
        assertThat(opened).isEqualTo(1L)
    }

    @Test
    fun editingSendsNewValues() {
        var title: String? = null
        var author: String? = null
        show(DetailsSamples.bovary, DetailsActions(onTitleChange = { title = it }, onAuthorChange = { author = it }))
        compose.onNodeWithText("Madame Bovary").performTextReplacement("Madame Bovary, mœurs de province")
        compose.onNodeWithText("Gustave Flaubert").performTextReplacement("")
        assertThat(title).isEqualTo("Madame Bovary, mœurs de province")
        assertThat(author).isEqualTo("")
    }

    @Test
    fun backAndDeleteButtons() {
        var back = false
        var deleteClicked = false
        show(DetailsSamples.bovary, DetailsActions(onBack = { back = true }, onDeleteClick = { deleteClicked = true }))
        compose.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        compose.onNodeWithText(text(R.string.details_delete_book)).performClick()
        assertThat(back).isTrue()
        assertThat(deleteClicked).isTrue()
    }

    @Test
    fun deleteDialogConfirmAndCancel() {
        var confirmed = false
        var dismissed = false
        show(DetailsSamples.deleteDialog, DetailsActions(onDeleteConfirm = { confirmed = true }, onDeleteDismiss = { dismissed = true }))
        compose.onNodeWithText(text(R.string.details_delete_dialog_title, "Madame Bovary")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_delete_dialog_body)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_delete_dialog_cancel)).performClick()
        compose.onNodeWithText(text(R.string.details_delete_dialog_confirm)).performClick()
        assertThat(dismissed).isTrue()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun routedScreenLeavesAfterDeletion() {
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        val dir = File(context.cacheDir, "details-test").apply { mkdirs() }
        val books = BookRepository(db.bookDao(), dir, dir)
        val id = runBlocking {
            books.insert(
                BookEntity(
                    title = "Germinal", author = "Émile Zola", filePath = File(dir, "g.epub").absolutePath, sha256 = "sha-g",
                    coverPath = null, sizeBytes = 850_000, originalFileName = "germinal.epub", importedAt = DetailsSamples.importedAt,
                    lastOpenedAt = null, readingLocatorJson = null, progression = 0.0, totalWords = 180_000,
                ),
            )
        }
        var back = 0
        val vm = DetailsViewModel(bookId = id, books = books, saveScope = CoroutineScope(Dispatchers.Unconfined))
        compose.setContent {
            VersoTheme { DetailsScreen(bookId = id, onBack = { back++ }, onOpenReader = {}, viewModel = vm) }
        }
        compose.onNodeWithText(text(R.string.details_delete_book)).performClick()
        compose.onNodeWithText(text(R.string.details_delete_dialog_confirm)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { back > 0 }
        assertThat(back).isEqualTo(1)
        assertThat(runBlocking { books.book(id) }).isNull()
        db.close()
    }
}

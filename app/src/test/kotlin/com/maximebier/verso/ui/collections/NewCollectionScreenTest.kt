package com.maximebier.verso.ui.collections

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.ui.library.LibraryBook
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class NewCollectionScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun book(id: Long, title: String) =
        LibraryBook(id, title, "Alexandre Dumas", null, "seed$id", BookStatus.TO_READ, 0.0, 0)

    private val books = listOf(
        SelectableBook(book(1, "Les Trois Mousquetaires"), selected = true),
        SelectableBook(book(2, "Vingt Ans après"), selected = true),
        SelectableBook(book(3, "Le Vicomte de Bragelonne"), selected = true),
        SelectableBook(book(4, "Le Comte de Monte-Cristo"), selected = false),
    )

    private fun show(state: NewCollectionUiState, actions: NewCollectionActions = NewCollectionActions()) {
        compose.setContent { VersoTheme { NewCollectionContent(state, actions) } }
    }

    @Test
    fun createIsEnabledOnlyWithAName() {
        var state by mutableStateOf(NewCollectionUiState(loading = false))
        compose.setContent { VersoTheme { NewCollectionContent(state, NewCollectionActions()) } }
        compose.onNodeWithText(context.getString(R.string.new_collection_create)).assertIsNotEnabled()
        state = state.copy(name = "Les Mousquetaires")
        compose.onNodeWithText(context.getString(R.string.new_collection_create)).assertIsEnabled()
    }

    @Test
    fun rowsAreCheckboxesAndCountIsShown() {
        val toggled = mutableListOf<Long>()
        show(
            NewCollectionUiState(loading = false, name = "Les Mousquetaires", query = "Dumas", books = books, selectedCount = 3),
            NewCollectionActions(onToggle = { toggled += it }),
        )
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.new_collection_selected, 3, 3)).assertIsDisplayed()
        compose.onNode(hasText("Les Trois Mousquetaires"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.onNode(hasText("Le Comte de Monte-Cristo"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        assertThat(toggled).containsExactly(4L)
    }

    @Test
    fun clearFilterAndClose() {
        val queries = mutableListOf<String>()
        var closed = 0
        show(
            NewCollectionUiState(loading = false, query = "Dumas", books = books),
            NewCollectionActions(onQueryChange = { queries += it }, onClose = { closed++ }),
        )
        compose.onNodeWithContentDescription(context.getString(R.string.new_collection_filter_clear)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.common_close)).performClick()
        assertThat(queries).containsExactly("")
        assertThat(closed).isEqualTo(1)
    }
}

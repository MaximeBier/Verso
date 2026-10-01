package com.maximebier.verso.ui.collections

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class AddToCollectionSheetTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val state = AddToCollectionUiState(
        loading = false,
        bookTitle = "Germinal",
        choices = listOf(
            CollectionChoice(1, "Les Rougon-Macquart", 5, checked = true),
            CollectionChoice(2, "Les Mousquetaires", 3, checked = false),
        ),
    )

    @Test
    fun sheetShowsTheBookAndItsCollections() {
        val toggled = mutableListOf<Long>()
        var created = 0
        var dismissed = 0
        compose.setContent {
            VersoTheme {
                AddToCollectionSheetContent(state, onToggle = { toggled += it }, onNewCollection = { created++ }, onDismiss = { dismissed++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.add_to_collection_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_quoted, "Germinal")).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.library_book_count, 5, 5), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Les Rougon-Macquart"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.onNode(hasText("Les Mousquetaires"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        compose.onNodeWithText(context.getString(R.string.add_to_collection_new)).performClick()
        compose.onNodeWithText(context.getString(R.string.add_to_collection_done)).performClick()
        assertThat(toggled).containsExactly(2L)
        assertThat(created).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }
}

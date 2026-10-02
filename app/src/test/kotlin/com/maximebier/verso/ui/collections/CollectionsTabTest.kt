package com.maximebier.verso.ui.collections

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class CollectionsTabTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun books(n: Int) = context.resources.getQuantityString(R.plurals.library_book_count, n, n)

    private val zola = CollectionCard(
        id = 10, name = "Les Rougon-Macquart", author = "Émile Zola", bookCount = 5,
        covers = listOf(CoverRef("La Fortune des Rougon", null, "fr")), fraction = 0.44f, percent = 44, finished = 2,
    )
    private val mixed = zola.copy(id = 11, name = "En vrac", author = null, bookCount = 3, percent = 29, finished = 1)

    private fun show(state: CollectionsUiState, actions: CollectionsActions = CollectionsActions()) {
        compose.setContent { VersoTheme { CollectionsTab(state, actions) } }
    }

    private fun description(card: CollectionCard): String {
        val count = books(card.bookCount)
        val subtitle = card.author?.let { context.getString(R.string.collections_card_author_and_count, it, count) } ?: count
        return context.getString(R.string.collections_card_content_description, card.name, subtitle, card.percent)
    }

    @Test
    fun talkBackReadsTheAuthorAfterTheName() {
        show(CollectionsUiState(loading = false, cards = listOf(zola, mixed)))
        compose.onNodeWithContentDescription("Les Rougon-Macquart, Émile Zola, 5\u00A0livres, 44\u202F% lus").assertExists()
        compose.onNodeWithContentDescription("En vrac, 3\u00A0livres, 29\u202F% lus").assertExists()
    }

    @Test
    fun cardsShowNameAuthorCountAndProgress() {
        show(CollectionsUiState(loading = false, cards = listOf(zola, mixed)))
        compose.onNodeWithText(context.getString(R.string.collections_title)).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.collections_count, 2, 2)).assertIsDisplayed()
        compose.onNodeWithContentDescription(description(zola)).assertIsDisplayed()
        compose.onNodeWithContentDescription(description(mixed)).assertIsDisplayed()
    }

    @Test
    fun cardTextsAreDrawn() {
        show(CollectionsUiState(loading = false, cards = listOf(zola, mixed)))
        // Textes sous la sémantique fusionnée : cherchés dans l’arbre non fusionné.
        compose.onNodeWithText("Les Rougon-Macquart", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.collections_author_and_count, "Émile Zola", books(5)), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithText(books(3), useUnmergedTree = true).assertExists()
        val finished = context.resources.getQuantityString(R.plurals.collections_finished_of, 2, 2, 5)
        compose.onNodeWithText(context.getString(R.string.common_percent, 44) + " " + finished, useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptyTabExplainsAndOffersNewCollection() {
        var created = 0
        show(CollectionsUiState(loading = false), CollectionsActions(onNewCollection = { created++ }))
        compose.onNodeWithText(context.getString(R.string.collections_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.collections_new)).performClick()
        assertThat(created).isEqualTo(1)
    }

    @Test
    fun cardIsClickableOnlyWhenTheCollectionScreenExists() {
        show(CollectionsUiState(loading = false, cards = listOf(zola)))
        compose.onNodeWithContentDescription(description(zola)).assertHasNoClickAction()
    }

    @Test
    fun clickOpensTheCollection() {
        var opened: Long? = null
        show(CollectionsUiState(loading = false, cards = listOf(zola)), CollectionsActions(onOpenCollection = { opened = it }))
        compose.onNodeWithContentDescription(description(zola)).assertHasClickAction().performClick()
        assertThat(opened).isEqualTo(10L)
    }
}

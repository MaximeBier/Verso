package com.maximebier.verso.ui.reader.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.readium.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class BookSearchModelTest {

    private val hrefs = listOf("c1.xhtml", "c2.xhtml", "c3.xhtml", "c4.xhtml")

    private fun hit(href: String, total: Double, chapter: String? = "Chapitre $href") = SearchHit(
        locator = Locator(href = Url(href)!!, mediaType = MediaType.XHTML, locations = Locator.Locations(totalProgression = total)),
        chapter = chapter,
        before = "…la ",
        match = "rivière",
        after = " qui…",
        progression = total,
    )

    /** Recherche pilotée par le test : chaque lot est poussé à la main, `null` termine. */
    private class ScriptedSearch {
        val queries = mutableListOf<String>()
        val batches = MutableSharedFlow<List<SearchHit>?>()
        fun search(query: String): Flow<List<SearchHit>> = flow {
            queries += query
            batches.takeWhile { it != null }.collect { emit(it!!) }
        }
    }

    private fun TestScope.model(search: ScriptedSearch, scope: CoroutineScope = backgroundScope) = BookSearchModel(
        scope = scope,
        search = search::search,
        readingOrderHrefs = { hrefs },
        chapterLabel = { it.chapter },
    )

    @Test
    fun searchStartsAfterDebounceAndResultsGrow() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)

        model.onQueryChange("riv")
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS - 1)
        runCurrent()
        assertThat(search.queries).isEmpty()
        assertThat(model.state.value.query).isEqualTo("rivière")

        advanceTimeBy(2)
        runCurrent()
        assertThat(search.queries).containsExactly("rivière")
        assertThat(model.state.value.running).isTrue()

        search.batches.emit(listOf(hit("c1.xhtml", 0.10)))
        runCurrent()
        assertThat(model.state.value.resultCount).isEqualTo(1)
        assertThat(model.state.value.progress).isEqualTo(0.25f)

        search.batches.emit(listOf(hit("c1.xhtml", 0.10), hit("c1.xhtml", 0.12), hit("c3.xhtml", 0.60)))
        runCurrent()
        val state = model.state.value
        assertThat(state.resultCount).isEqualTo(3)
        assertThat(state.groups.map { it.title }).containsExactly("Chapitre c1.xhtml", "Chapitre c3.xhtml").inOrder()
        assertThat(state.groups[0].hits).hasSize(2)
        assertThat(state.progress).isEqualTo(0.75f)
        assertThat(state.done).isFalse()

        search.batches.emit(null)
        runCurrent()
        assertThat(model.state.value.running).isFalse()
        assertThat(model.state.value.done).isTrue()
        assertThat(model.state.value.progress).isEqualTo(1f)
    }

    @Test
    fun newInputCancelsTheRunningSearch() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(listOf(hit("c1.xhtml", 0.10)))
        runCurrent()

        model.onQueryChange("moulin")
        runCurrent()
        assertThat(model.state.value.groups).isEmpty()
        assertThat(model.state.value.running).isFalse()
        // L'ancienne recherche ne reçoit plus rien : ce lot n'arrive pas dans l'état.
        search.batches.emit(listOf(hit("c1.xhtml", 0.10), hit("c2.xhtml", 0.3)))
        runCurrent()
        assertThat(model.state.value.resultCount).isEqualTo(0)

        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(search.queries).containsExactly("rivière", "moulin").inOrder()
    }

    @Test
    fun cancelledSearchResumesKeepingTheResultsAlreadyShown() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(listOf(hit("c1.xhtml", 0.10), hit("c2.xhtml", 0.30)))
        runCurrent()

        model.cancel()
        runCurrent()
        assertThat(model.state.value.running).isFalse()
        assertThat(model.state.value.done).isFalse()
        assertThat(search.batches.subscriptionCount.value).isEqualTo(0)

        model.resume()
        runCurrent()
        assertThat(search.queries).containsExactly("rivière", "rivière").inOrder()
        assertThat(model.state.value.running).isTrue()
        // La recherche reprend du début : ses premiers lots, déjà affichés, ne font pas reculer la liste.
        search.batches.emit(listOf(hit("c1.xhtml", 0.10)))
        runCurrent()
        assertThat(model.state.value.resultCount).isEqualTo(2)
        assertThat(model.state.value.progress).isEqualTo(0.5f)
        search.batches.emit(listOf(hit("c1.xhtml", 0.10), hit("c2.xhtml", 0.30), hit("c4.xhtml", 0.90)))
        search.batches.emit(null)
        runCurrent()
        assertThat(model.state.value.resultCount).isEqualTo(3)
        assertThat(model.state.value.done).isTrue()
    }

    @Test
    fun resumeLeavesFinishedOrTooShortSearchesAlone() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("r")
        model.cancel()
        model.resume()
        runCurrent()
        assertThat(search.queries).isEmpty()

        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(null)
        runCurrent()
        model.cancel()
        model.resume()
        runCurrent()
        assertThat(search.queries).containsExactly("rivière")
        assertThat(model.state.value.done).isTrue()
    }

    @Test
    fun tooShortQueryNeverSearches() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange(" r ")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS * 3)
        runCurrent()
        assertThat(search.queries).isEmpty()
        assertThat(model.state.value).isEqualTo(SearchUiState(query = " r "))
    }

    @Test
    fun noResultEndsDoneAndEmpty() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("xylophone")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(null)
        runCurrent()
        assertThat(model.state.value.done).isTrue()
        assertThat(model.state.value.resultCount).isEqualTo(0)
    }

    @Test
    fun sameQueryKeepsResultsWithoutSearchingAgain() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(listOf(hit("c1.xhtml", 0.10)))
        search.batches.emit(null)
        runCurrent()

        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(search.queries).containsExactly("rivière")
        assertThat(model.state.value.resultCount).isEqualTo(1)
    }

    @Test
    fun clearEmptiesEverything() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        model.clear()
        runCurrent()
        assertThat(model.state.value).isEqualTo(SearchUiState())
    }
}

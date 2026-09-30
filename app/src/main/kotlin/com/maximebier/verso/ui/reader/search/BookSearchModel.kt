package com.maximebier.verso.ui.reader.search

import com.maximebier.verso.readium.BookSearch
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Résultats consécutifs d'un même chapitre (maquette 2.06 : un intitulé, puis ses extraits). */
data class SearchGroup(val title: String?, val hits: List<SearchHit>)

data class SearchUiState(
    val query: String = "",
    val running: Boolean = false,
    val done: Boolean = false,
    val groups: List<SearchGroup> = emptyList(),
    val resultCount: Int = 0,
    val progress: Float = 0f,
)

/**
 * État de la recherche du livre ouvert, détenu par `ReaderViewModel` (il vit autant que le lecteur : la recherche
 * rouverte retrouve sa requête et ses résultats). Saisie anti-rebond, une seule recherche à la fois.
 */
class BookSearchModel(
    private val scope: CoroutineScope,
    private val search: (String) -> Flow<List<SearchHit>>,
    private val readingOrderHrefs: () -> List<String>,
    private val chapterLabel: (SearchHit) -> String?,
) {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        if (query == _state.value.query && (_state.value.running || _state.value.done || job?.isActive == true)) return
        job?.cancel()
        _state.value = SearchUiState(query = query)
        if (query.trim().length < BookSearch.MIN_QUERY_CHARS) return
        job = scope.launch {
            delay(DEBOUNCE_MS)
            run(query)
        }
    }

    fun clear() = onQueryChange("")

    /** Recherche ou lecteur fermés : la recherche en cours s'arrête (requête et résultats trouvés sont gardés). */
    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    /**
     * Recherche rouverte : une recherche arrêtée par [cancel] avant sa fin repart du début, sans anti-rebond. Les
     * résultats déjà affichés restent ; la liste ne grandit à nouveau qu'une fois la recherche revenue plus loin.
     */
    fun resume() {
        val current = _state.value
        if (current.done || job?.isActive == true || current.query.trim().length < BookSearch.MIN_QUERY_CHARS) return
        job = scope.launch { run(current.query) }
    }

    private suspend fun run(query: String) {
        _state.update { it.copy(running = true) }
        // Listes cumulées : une liste plus courte que celle affichée est un début déjà connu (reprise).
        search(query).collect { hits -> _state.update { if (hits.size < it.resultCount) it else it.withHits(hits) } }
        _state.update { it.copy(running = false, done = true, progress = 1f) }
    }

    private fun SearchUiState.withHits(hits: List<SearchHit>): SearchUiState {
        val groups = mutableListOf<SearchGroup>()
        hits.forEach { hit ->
            val title = chapterLabel(hit) ?: hit.chapter
            val last = groups.lastOrNull()
            if (last != null && last.title == title) {
                groups[groups.lastIndex] = last.copy(hits = last.hits + hit)
            } else {
                groups += SearchGroup(title, listOf(hit))
            }
        }
        val hrefs = readingOrderHrefs()
        val lastIndex = hits.lastOrNull()?.let { hrefs.indexOf(Locators.hrefKey(it.locator)) } ?: -1
        val progress = if (hrefs.isEmpty() || lastIndex < 0) progress else (lastIndex + 1).toFloat() / hrefs.size
        return copy(groups = groups, resultCount = hits.size, progress = progress)
    }

    companion object {
        /** Délai après la dernière frappe avant de chercher. */
        const val DEBOUNCE_MS = 300L
    }
}

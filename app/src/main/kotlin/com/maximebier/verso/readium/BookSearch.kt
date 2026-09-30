@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.readium

import android.util.Log
import com.maximebier.verso.core.text.searchSnippet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try

/** Un résultat : position exacte du mot (saut), titre du sommaire du fichier, extrait prêt à afficher. */
data class SearchHit(
    val locator: Locator,
    val chapter: String?,
    val before: String,
    val match: String,
    val after: String,
    val progression: Double,
)

/**
 * Recherche plein texte dans le livre ouvert, par le service de Readium (ICU : casse et accents ignorés).
 * Chaque émission est la liste cumulée des résultats, un lot par fichier qui en contient.
 */
class BookSearch(
    private val publication: Publication,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun search(query: String): Flow<List<SearchHit>> = flow {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_CHARS) return@flow
        val iterator = publication.search(trimmed) ?: return@flow
        try {
            val hits = mutableListOf<SearchHit>()
            while (true) {
                val page = when (val result = iterator.next()) {
                    is Try.Success -> result.value ?: break
                    is Try.Failure -> {
                        Log.w(TAG, "Recherche interrompue : ${result.value.message}")
                        break
                    }
                }
                val found = page.locators.mapNotNull(::toHit)
                if (found.isEmpty()) continue
                hits += found
                emit(hits.toList())
            }
        } finally {
            iterator.close()
        }
    }.flowOn(dispatcher)

    private fun toHit(locator: Locator): SearchHit? {
        val text = locator.text
        val match = text.highlight?.takeIf { it.isNotBlank() } ?: return null
        val snippet = searchSnippet(before = text.before.orEmpty(), match = match, after = text.after.orEmpty())
        return SearchHit(
            locator = locator,
            chapter = locator.title?.takeIf { it.isNotBlank() },
            before = snippet.before,
            match = snippet.match,
            after = snippet.after,
            progression = (locator.locations.totalProgression ?: 0.0).coerceIn(0.0, 1.0),
        )
    }

    companion object {
        /** En dessous, aucune recherche : une lettre seule donnerait des milliers de résultats inutiles. */
        const val MIN_QUERY_CHARS = 2
        private const val TAG = "BookSearch"
    }
}

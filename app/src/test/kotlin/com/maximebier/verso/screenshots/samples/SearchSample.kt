package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.readium.SearchHit
import com.maximebier.verso.ui.reader.search.SearchGroup
import com.maximebier.verso.ui.reader.search.SearchScreen
import com.maximebier.verso.ui.reader.search.SearchUiState
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

private fun sampleHit(before: String, after: String, progression: Double) = SearchHit(
    locator = Locator(href = Url("deuxieme-partie-1.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(totalProgression = progression)),
    chapter = "I",
    before = before,
    match = "rivière",
    after = after,
    progression = progression,
)

/** Maquette 2.06 : « rivière », trois résultats de la Deuxième partie, chapitre I, recherche en cours. */
@Composable
fun SearchSample() {
    val hits = listOf(
        sampleHit("…au fond d’une vallée qu’arrose la Rieule, petite ", " qui se jette dans l’Andelle, après avoir fait tourner trois moulins…", 0.305),
        sampleHit("…d’où l’on découvre la vallée. La ", " qui la traverse en fait comme deux régions de physionomie distincte…", 0.307),
        sampleHit("…le bourg paresseux, s’écartant de la plaine, a continué naturellement à s’agrandir vers la ", ".", 0.312),
    )
    SearchScreen(
        state = SearchUiState(query = "rivière", running = true, groups = listOf(SearchGroup("Deuxième partie, chapitre I", hits)), resultCount = 3, progress = 0.45f),
        onQueryChange = {},
        onClear = {},
        onClose = {},
        onResultClick = {},
        requestFocus = false,
    )
}

package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.ui.library.EmptyLibraryScreen
import com.maximebier.verso.ui.library.LibraryActions
import com.maximebier.verso.ui.library.LibraryContent
import com.maximebier.verso.ui.library.LibrarySamples

/** Premier lancement, bibliothèque vide. */
@Composable
internal fun LibraryEmptySample() {
    EmptyLibraryScreen(onOpenSettings = {}, onImport = {})
}

/** Liste avec la carte Reprendre ; le menu ⋮ s'ouvre par un clic (état interne du bouton), voir V1ScreenCatalog. */
@Composable
internal fun LibraryBookMenuSample() {
    LibraryContent(state = LibrarySamples.list, actions = LibraryActions())
}

/** Grille. */
@Composable
internal fun LibraryGridSample() {
    LibraryContent(state = LibrarySamples.grid, actions = LibraryActions())
}

/** Bibliothèque avec états (la feuille est ouverte par la capture). */
@Composable
internal fun LibraryStatesSample() {
    LibraryContent(LibrarySamples.states, LibraryActions())
}

package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.ui.library.EmptyLibraryScreen
import com.maximebier.verso.ui.library.LibraryActions
import com.maximebier.verso.ui.library.LibraryContent
import com.maximebier.verso.ui.library.LibrarySamples

/** 1.01 : premier lancement, bibliothèque vide. */
@Composable
internal fun LibraryEmptySample() {
    EmptyLibraryScreen(onOpenSettings = {}, onImport = {})
}

/** 1.02 : liste avec la carte Reprendre. */
@Composable
internal fun LibraryListSample() {
    LibraryContent(state = LibrarySamples.list, actions = LibraryActions())
}

/** 1.02b : même écran que 1.02 ; le menu ⋮ s'ouvre par un clic (état interne du bouton), voir V1ScreenCatalog. */
@Composable
internal fun LibraryBookMenuSample() {
    LibraryContent(state = LibrarySamples.list, actions = LibraryActions())
}

/** 1.03 : grille. */
@Composable
internal fun LibraryGridSample() {
    LibraryContent(state = LibrarySamples.grid, actions = LibraryActions())
}

/** 2.07 et 2.07b : bibliothèque avec états (la feuille est ouverte par la capture). */
@Composable
internal fun LibraryStatesSample() {
    LibraryContent(LibrarySamples.states, LibraryActions())
}

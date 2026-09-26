package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.ui.library.LibraryActions
import com.maximebier.verso.ui.library.LibraryContent
import com.maximebier.verso.ui.library.LibrarySamples

/** 1.04 : snackbar « … a été ajouté. » (horloge figée pendant son affichage, voir V1ScreenCatalog). */
@Composable
internal fun ImportSuccessSample() {
    LibraryContent(state = LibrarySamples.snackbar, actions = LibraryActions())
}

/** 1.05 : dialogue « Livre déjà importé ». */
@Composable
internal fun ImportDuplicateSample() {
    LibraryContent(state = LibrarySamples.duplicate, actions = LibraryActions())
}

/** 1.06 : dialogue « Fichier refusé ». */
@Composable
internal fun ImportRejectedSample() {
    LibraryContent(state = LibrarySamples.rejected, actions = LibraryActions())
}

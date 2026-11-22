package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.ui.details.DetailsActions
import com.maximebier.verso.ui.details.DetailsContent
import com.maximebier.verso.ui.details.DetailsSamples

/** Fiche du livre. */
@Composable
internal fun DetailsSample() {
    DetailsContent(DetailsSamples.bovary, DetailsActions())
}

/** Dialogue « Supprimer ce livre ». */
@Composable
internal fun DeleteBookDialogSample() {
    DetailsContent(DetailsSamples.deleteDialog, DetailsActions())
}

/** Fiche V2 (état et statistiques). */
@Composable
internal fun DetailsV2Sample() {
    DetailsContent(DetailsSamples.bovary, DetailsActions())
}

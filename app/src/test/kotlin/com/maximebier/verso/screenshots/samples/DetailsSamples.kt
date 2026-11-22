package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.ui.details.DetailsActions
import com.maximebier.verso.ui.details.DetailsContent
import com.maximebier.verso.ui.details.DetailsSamples

/** Dialogue « Supprimer ce livre ». */
@Composable
internal fun DeleteBookDialogSample() {
    DetailsContent(DetailsSamples.deleteDialog, DetailsActions())
}

/** Fiche du livre (état et statistiques). */
@Composable
internal fun DetailsV2Sample() {
    DetailsContent(DetailsSamples.bovary, DetailsActions())
}

/** Fiche d’un livre déjà ouvert, statistiques masquées : le journal à la place de la section, puis les notes. */
@Composable
internal fun DetailsWithoutStatisticsSample() {
    DetailsContent(DetailsSamples.bovary.copy(stats = null, highlightCount = 3), DetailsActions())
}

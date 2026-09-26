package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.ui.details.DetailsActions
import com.maximebier.verso.ui.details.DetailsContent
import com.maximebier.verso.ui.details.DetailsSamples

/** 1.07 : fiche du livre. */
@Composable
internal fun DetailsSample() {
    DetailsContent(DetailsSamples.bovary, DetailsActions())
}

/** 1.08 : dialogue « Supprimer ce livre ». */
@Composable
internal fun DeleteBookDialogSample() {
    DetailsContent(DetailsSamples.deleteDialog, DetailsActions())
}

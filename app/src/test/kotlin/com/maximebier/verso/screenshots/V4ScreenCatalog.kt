package com.maximebier.verso.screenshots

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.R
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.screenshots.samples.AddToCollectionNoCollectionSample
import com.maximebier.verso.screenshots.samples.AddToCollectionSample
import com.maximebier.verso.screenshots.samples.CollectionEmptySample
import com.maximebier.verso.screenshots.samples.DetailsWithCollectionsSample
import com.maximebier.verso.screenshots.samples.CollectionFinishedSample
import com.maximebier.verso.screenshots.samples.CollectionReorderSample
import com.maximebier.verso.screenshots.samples.CollectionSample
import com.maximebier.verso.screenshots.samples.CollectionsEmptySample
import com.maximebier.verso.screenshots.samples.CollectionsTabSample
import com.maximebier.verso.screenshots.samples.NewCollectionSample

/** Un écran V4 et les palettes où il est capturé (toutes par défaut). */
data class V4ScreenFixture(val screen: ScreenFixture, val themes: List<AppTheme> = AppTheme.entries) {
    override fun toString(): String = screen.id
}

/** Écrans V4 (collections). */
object V4ScreenCatalog {
    val fixtures: List<V4ScreenFixture> = listOf(
        V4ScreenFixture(ScreenFixture("Collections-Liste") { CollectionsTabSample() }),
        V4ScreenFixture(ScreenFixture("Collections-Liste-Vide") { CollectionsEmptySample() }),
        V4ScreenFixture(ScreenFixture("Collections-Detail") { CollectionSample() }),
        V4ScreenFixture(ScreenFixture("Collections-Detail-Reordonner") { CollectionReorderSample() }),
        V4ScreenFixture(ScreenFixture("Collections-Detail-Reordonner-Fin", afterContent = scrollToLastBook) { CollectionReorderSample() }),
        V4ScreenFixture(ScreenFixture("Collections-Detail-Vide") { CollectionEmptySample() }),
        V4ScreenFixture(ScreenFixture("Collections-Detail-Termine") { CollectionFinishedSample() }),
        V4ScreenFixture(ScreenFixture("Collections-Nouvelle") { NewCollectionSample() }),
        V4ScreenFixture(ScreenFixture("Collections-Ajouter", afterContent = openCollectionsSheet) { AddToCollectionSample() }),
        V4ScreenFixture(ScreenFixture("Collections-Ajouter-SansCollection", afterContent = openCollectionsSheet) { AddToCollectionNoCollectionSample() }),
        V4ScreenFixture(ScreenFixture("Livre-Details-Collections") { DetailsWithCollectionsSample() }),
    )
}

/** Ouvre la feuille « Ajouter à une collection » par « Modifier » de la ligne « Collections » de la fiche. */
private val openCollectionsSheet: ComposeContentTestRule.() -> Unit = {
    val label = ApplicationProvider.getApplicationContext<Context>().getString(R.string.details_collections_edit_content_description)
    onNodeWithContentDescription(label).performScrollTo().performClick()
    waitForIdle()
}

/** Fait défiler l’écran d’une collection jusqu’à la dernière ligne (« La Bête humaine »), pour juger les lignes elles-mêmes. */
private val scrollToLastBook: ComposeContentTestRule.() -> Unit = {
    onNode(hasScrollAction()).performScrollToNode(hasText("La Bête humaine"))
    waitForIdle()
}

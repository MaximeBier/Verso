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

/** Écrans V4 (collections, 4.01 à 4.04). */
object V4ScreenCatalog {
    val fixtures: List<V4ScreenFixture> = listOf(
        V4ScreenFixture(ScreenFixture("4.01-collections") { CollectionsTabSample() }),
        V4ScreenFixture(ScreenFixture("4.01-collections-vide") { CollectionsEmptySample() }),
        V4ScreenFixture(ScreenFixture("4.02-une-collection") { CollectionSample() }),
        V4ScreenFixture(ScreenFixture("4.02-reordonner") { CollectionReorderSample() }),
        V4ScreenFixture(ScreenFixture("4.02-reordonner-lignes", afterContent = scrollToLastBook) { CollectionReorderSample() }),
        V4ScreenFixture(ScreenFixture("4.02-vide") { CollectionEmptySample() }),
        V4ScreenFixture(ScreenFixture("4.02-tout-termine") { CollectionFinishedSample() }),
        V4ScreenFixture(ScreenFixture("4.03-nouvelle-collection") { NewCollectionSample() }),
        V4ScreenFixture(ScreenFixture("4.04-ajouter-a-une-collection", afterContent = openCollectionsSheet) { AddToCollectionSample() }),
        V4ScreenFixture(ScreenFixture("4.04-sans-collection", afterContent = openCollectionsSheet) { AddToCollectionNoCollectionSample() }),
        V4ScreenFixture(ScreenFixture("1.07-fiche-collections") { DetailsWithCollectionsSample() }),
    )
}

/** Ouvre la feuille 4.04 par « Modifier » de la ligne « Collections » de la fiche. */
private val openCollectionsSheet: ComposeContentTestRule.() -> Unit = {
    val label = ApplicationProvider.getApplicationContext<Context>().getString(R.string.details_collections_edit_content_description)
    onNodeWithContentDescription(label).performScrollTo().performClick()
    waitForIdle()
}

/** Fait défiler l’écran 4.02 jusqu’à la dernière ligne (« La Bête humaine »), pour juger les lignes elles-mêmes. */
private val scrollToLastBook: ComposeContentTestRule.() -> Unit = {
    onNode(hasScrollAction()).performScrollToNode(hasText("La Bête humaine"))
    waitForIdle()
}

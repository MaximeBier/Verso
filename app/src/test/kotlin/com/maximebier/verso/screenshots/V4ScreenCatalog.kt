package com.maximebier.verso.screenshots

import com.maximebier.verso.data.AppTheme
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
        V4ScreenFixture(ScreenFixture("4.03-nouvelle-collection") { NewCollectionSample() }),
    )
}

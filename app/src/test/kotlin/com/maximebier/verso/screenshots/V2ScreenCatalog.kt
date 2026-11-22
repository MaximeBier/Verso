package com.maximebier.verso.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.R
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.screenshots.samples.DetailsV2Sample
import com.maximebier.verso.screenshots.samples.DetailsWithoutStatisticsSample
import com.maximebier.verso.screenshots.samples.JournalSample
import com.maximebier.verso.screenshots.samples.LibraryStatesSample
import com.maximebier.verso.screenshots.samples.PagesModeSample
import com.maximebier.verso.screenshots.samples.ReaderToolsSample
import com.maximebier.verso.screenshots.samples.ReadingSettingsSheetSample
import com.maximebier.verso.screenshots.samples.ReturnCardSample
import com.maximebier.verso.screenshots.samples.SampleReadingText
import com.maximebier.verso.screenshots.samples.SearchSample
import com.maximebier.verso.screenshots.samples.SettingsV2Sample
import com.maximebier.verso.screenshots.samples.TocSheetSample
import com.maximebier.verso.ui.theme.VersoTheme

/** Un écran V2 et les palettes où il est capturé (toutes par défaut). */
data class V2ScreenFixture(val screen: ScreenFixture, val themes: List<AppTheme> = AppTheme.entries) {
    override fun toString(): String = screen.id
}

/**
 * Écrans V2 dessinables par Compose. Thèmes sépia et nuit : texte de lecture (rendu Compose ; le vrai rendu Readium est
 * vérifié sur le téléphone) et écrans V1 sans autre capture dans ces palettes, pour « sans zone d'une autre couleur ».
 */
object V2ScreenCatalog {
    private val paperThemes = listOf(AppTheme.SEPIA, AppTheme.NIGHT)

    /** Ouvre la feuille « Trier et afficher » par son bouton. */
    private val openSortSheet: ComposeContentTestRule.() -> Unit = {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val label = ctx.getString(
            R.string.library_sort_button_description,
            ctx.getString(R.string.library_sort_state_recent),
            ctx.getString(R.string.library_view_state_list),
        )
        onNodeWithContentDescription(label).performClick()
    }

    val fixtures: List<V2ScreenFixture> = listOf(
        V2ScreenFixture(
            ScreenFixture("Lecture") {
                Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) { SampleReadingText() }
            },
            paperThemes,
        ),
        V2ScreenFixture(ScreenFixture("Lecture-Sommaire") { TocSheetSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("Lecture-Journal") { JournalSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("Lecture-Revenir") { ReturnCardSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("Lecture-Barre") { ReaderToolsSample() }),
        V2ScreenFixture(ScreenFixture("Lecture-Reglages") { ReadingSettingsSheetSample() }),
        V2ScreenFixture(ScreenFixture("Lecture-Pages") { PagesModeSample() }),
        V2ScreenFixture(ScreenFixture("Bibliotheque-Liste") { LibraryStatesSample() }),
        V2ScreenFixture(ScreenFixture("Bibliotheque-Liste-Defile", afterContent = scrollListToProgress) { LibraryStatesSample() }),
        V2ScreenFixture(ScreenFixture("Bibliotheque-TrierAfficher", afterContent = openSortSheet) { LibraryStatesSample() }),
        V2ScreenFixture(ScreenFixture("Livre-Details") { DetailsV2Sample() }),
        V2ScreenFixture(ScreenFixture("Livre-Details-SansStatistiques") { DetailsWithoutStatisticsSample() }),
        V2ScreenFixture(ScreenFixture("Parametres") { SettingsV2Sample() }),
        V2ScreenFixture(ScreenFixture("Lecture-Recherche") { SearchSample() }),
    )
}

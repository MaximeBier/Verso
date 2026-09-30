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
import com.maximebier.verso.screenshots.samples.DetailsSample
import com.maximebier.verso.screenshots.samples.DetailsV2Sample
import com.maximebier.verso.screenshots.samples.JournalSample
import com.maximebier.verso.screenshots.samples.LibraryListSample
import com.maximebier.verso.screenshots.samples.LibraryStatesSample
import com.maximebier.verso.screenshots.samples.PagesModeSample
import com.maximebier.verso.screenshots.samples.ReaderBarsSample
import com.maximebier.verso.screenshots.samples.ReaderToolsSample
import com.maximebier.verso.screenshots.samples.ReadingSettingsSheetSample
import com.maximebier.verso.screenshots.samples.ReturnCardSample
import com.maximebier.verso.screenshots.samples.SampleReadingText
import com.maximebier.verso.screenshots.samples.SettingsSample
import com.maximebier.verso.screenshots.samples.TocSheetSample
import com.maximebier.verso.ui.theme.VersoTheme

/** Un écran V2 et les palettes où il est capturé (toutes par défaut). */
data class V2ScreenFixture(val screen: ScreenFixture, val themes: List<AppTheme> = AppTheme.entries) {
    override fun toString(): String = screen.id
}

/**
 * Écrans V2 dessinables par Compose. 2.03 et 2.04 : texte de lecture (rendu Compose ; le vrai rendu Readium est
 * vérifié sur le téléphone) et écrans V1 dans les palettes sépia et nuit, pour « sans zone d'une autre couleur ».
 */
object V2ScreenCatalog {
    private val paperThemes = listOf(AppTheme.SEPIA, AppTheme.NIGHT)

    /** Ouvre la feuille « Trier et afficher » (2.07b) par son bouton. */
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
            ScreenFixture("2.03-2.04-lecture") {
                Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) { SampleReadingText() }
            },
            paperThemes,
        ),
        V2ScreenFixture(ScreenFixture("2.03-2.04-barre-affichee") { ReaderBarsSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-bibliotheque") { LibraryListSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-details") { DetailsSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-parametres") { SettingsSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-sommaire") { TocSheetSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-journal") { JournalSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-revenir") { ReturnCardSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.01-barre-de-lecture") { ReaderToolsSample() }),
        V2ScreenFixture(ScreenFixture("2.02-reglages-de-lecture") { ReadingSettingsSheetSample() }),
        V2ScreenFixture(ScreenFixture("2.05-mode-pages") { PagesModeSample() }),
        V2ScreenFixture(ScreenFixture("2.07-bibliotheque-avec-etats") { LibraryStatesSample() }),
        V2ScreenFixture(ScreenFixture("2.07b-trier-et-afficher", afterContent = openSortSheet) { LibraryStatesSample() }),
        V2ScreenFixture(ScreenFixture("2.08-details-du-livre") { DetailsV2Sample() }),
    )
}

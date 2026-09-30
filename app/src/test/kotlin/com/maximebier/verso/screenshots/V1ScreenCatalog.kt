package com.maximebier.verso.screenshots

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.R
import com.maximebier.verso.screenshots.samples.DeleteBookDialogSample
import com.maximebier.verso.screenshots.samples.DetailsSample
import com.maximebier.verso.screenshots.samples.ImportDuplicateSample
import com.maximebier.verso.screenshots.samples.ImportRejectedSample
import com.maximebier.verso.screenshots.samples.ImportSuccessSample
import com.maximebier.verso.screenshots.samples.JournalEmptySample
import com.maximebier.verso.screenshots.samples.JournalSample
import com.maximebier.verso.screenshots.samples.LibraryBookMenuSample
import com.maximebier.verso.screenshots.samples.LibraryEmptySample
import com.maximebier.verso.screenshots.samples.LibraryGridSample
import com.maximebier.verso.screenshots.samples.LibraryListSample
import com.maximebier.verso.screenshots.samples.LicensesSample
import com.maximebier.verso.screenshots.samples.ReaderBarsSample
import com.maximebier.verso.screenshots.samples.ReturnCardSample
import com.maximebier.verso.screenshots.samples.SettingsClearJournalDialogSample
import com.maximebier.verso.screenshots.samples.SettingsSample
import com.maximebier.verso.screenshots.samples.TocSheetSample
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

/** Variantes imposées par les critères : clair, sombre système, texte Android à 200 %. */
enum class ScreenVariant(val suffix: String) {
    LIGHT("clair"),
    DARK("sombre"),
    FONT_200("texte-200"),
}

/**
 * Un écran du catalogue. [beforeContent] et [afterContent] entourent `setContent` pour les écrans qu'un état
 * interne ou une horloge conditionne (menu ⋮ ouvert par un clic, snackbar figée pendant son affichage).
 */
data class ScreenFixture(
    val id: String,
    val beforeContent: ComposeContentTestRule.() -> Unit = {},
    val afterContent: ComposeContentTestRule.() -> Unit = {},
    val content: @Composable () -> Unit,
) {
    override fun toString(): String = id

    /** Compose l'écran dans [wrap] (le thème) et l'amène dans l'état de la maquette. */
    fun show(rule: ComposeContentTestRule, wrap: @Composable (@Composable () -> Unit) -> Unit) {
        rule.beforeContent()
        rule.setContent { wrap(content) }
        rule.waitForIdle()
        rule.afterContent()
        rule.waitForIdle()
    }
}

/**
 * Applique la variante avant le lancement de l'activité de test (à placer avant la règle Compose). La
 * configuration vaut pour toutes les fenêtres (dialogues, menus, feuilles modales) : c'est ainsi que les
 * feuilles reçoivent le texte à 200 %, qu'une surcharge de LocalDensity n'atteint pas.
 */
class VariantRule(private val variant: ScreenVariant) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            when (variant) {
                ScreenVariant.LIGHT -> Unit
                ScreenVariant.DARK -> RuntimeEnvironment.setQualifiers("+night")
                ScreenVariant.FONT_200 -> RuntimeEnvironment.setFontScale(2f)
            }
            base.evaluate()
        }
    }
}

/** Ouvre le menu ⋮ de « Vingt mille lieues sous les mers » (1.02b), en faisant défiler la liste jusqu'au bouton. */
private val openBookMenu: ComposeContentTestRule.() -> Unit = {
    val label = ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.library_book_options, "Vingt mille lieues sous les mers")
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasContentDescription(label))
    onNodeWithContentDescription(label).performClick()
}

/** La snackbar reste affichée : horloge manuelle, avancée le temps de son apparition. */
private val freezeClock: ComposeContentTestRule.() -> Unit = { mainClock.autoAdvance = false }
private val showSnackbar: ComposeContentTestRule.() -> Unit = { mainClock.advanceTimeBy(1_000) }

/** Tous les écrans V1 dessinables par Compose (1.10 est rendu par Readium, voir 7.3). */
object V1ScreenCatalog {
    val fixtures: List<ScreenFixture> = listOf(
        ScreenFixture("1.01-premier-lancement") { LibraryEmptySample() },
        ScreenFixture("1.02-bibliotheque-liste") { LibraryListSample() },
        ScreenFixture("1.02b-menu-dun-livre", afterContent = openBookMenu) { LibraryBookMenuSample() },
        ScreenFixture("1.03-bibliotheque-grille") { LibraryGridSample() },
        ScreenFixture("1.04-import-reussi", beforeContent = freezeClock, afterContent = showSnackbar) { ImportSuccessSample() },
        ScreenFixture("1.05-livre-deja-importe") { ImportDuplicateSample() },
        ScreenFixture("1.06-fichier-refuse") { ImportRejectedSample() },
        ScreenFixture("1.07-details-du-livre") { DetailsSample() },
        ScreenFixture("1.08-supprimer-un-livre") { DeleteBookDialogSample() },
        ScreenFixture("1.09-parametres") { SettingsSample() },
        ScreenFixture("1.09-effacer-le-journal") { SettingsClearJournalDialogSample() },
        ScreenFixture("1.09-licences") { LicensesSample() },
        ScreenFixture("1.11-lecture-barre-affichee") { ReaderBarsSample() },
        ScreenFixture("1.12-sommaire") { TocSheetSample() },
        ScreenFixture("1.13-journal-de-lecture") { JournalSample() },
        ScreenFixture("1.13-journal-vide") { JournalEmptySample() },
        ScreenFixture("1.14-retour-a-votre-lecture") { ReturnCardSample() },
    )
}

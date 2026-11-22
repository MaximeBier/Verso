package com.maximebier.verso.screenshots

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.R
import com.maximebier.verso.screenshots.samples.DeleteBookDialogSample
import com.maximebier.verso.screenshots.samples.ImportDuplicateSample
import com.maximebier.verso.screenshots.samples.ImportRejectedSample
import com.maximebier.verso.screenshots.samples.ImportSuccessSample
import com.maximebier.verso.screenshots.samples.JournalEmptySample
import com.maximebier.verso.screenshots.samples.JournalSample
import com.maximebier.verso.screenshots.samples.LibraryBookMenuSample
import com.maximebier.verso.screenshots.samples.LibraryEmptySample
import com.maximebier.verso.screenshots.samples.LibraryGridSample
import com.maximebier.verso.screenshots.samples.LicensesSample
import com.maximebier.verso.screenshots.samples.ReturnCardSample
import com.maximebier.verso.screenshots.samples.SettingsClearJournalDialogSample
import com.maximebier.verso.screenshots.samples.TocSheetSample
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

/** Variantes vérifiées : clair, sombre système, texte Android à 200 %. */
enum class ScreenVariant(val suffix: String) {
    LIGHT("clair"),
    DARK("sombre"),
    FONT_200("clair-texte-200"),
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

    /** Compose l'écran dans [wrap] (le thème) et l'amène dans l'état voulu. */
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

/** Ouvre le menu ⋮ de « Vingt mille lieues sous les mers », en faisant défiler la liste jusqu'au bouton. */
private val openBookMenu: ComposeContentTestRule.() -> Unit = {
    val label = ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.library_book_options, "Vingt mille lieues sous les mers")
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasContentDescription(label))
    onNodeWithContentDescription(label).performClick()
}

/** Fait défiler la liste jusqu'à une ligne « en cours », pour juger aussi son pourcentage. */
internal val scrollListToProgress: ComposeContentTestRule.() -> Unit = {
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(4)
}

/** Fait défiler la grille jusqu’aux livres, sous la carte « Reprendre », l’en-tête et les filtres. */
private val scrollGridToBooks: ComposeContentTestRule.() -> Unit = {
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(3)
}

/** La snackbar reste affichée : horloge manuelle, avancée le temps de son apparition. */
private val freezeClock: ComposeContentTestRule.() -> Unit = { mainClock.autoAdvance = false }
private val showSnackbar: ComposeContentTestRule.() -> Unit = { mainClock.advanceTimeBy(1_000) }

/**
 * Écrans V1 dessinables par Compose (le texte de lecture est rendu par Readium). Identifiant = nom de la planche du
 * canevas, plus l'état ; les écrans repris dans un autre catalogue avec toutes les palettes n'y sont pas répétés.
 */
object V1ScreenCatalog {
    val fixtures: List<ScreenFixture> = listOf(
        ScreenFixture("Bibliotheque-Vide") { LibraryEmptySample() },
        ScreenFixture("Bibliotheque-MenuLivre", afterContent = openBookMenu) { LibraryBookMenuSample() },
        ScreenFixture("Bibliotheque-Grille") { LibraryGridSample() },
        ScreenFixture("Bibliotheque-Grille-Defile", afterContent = scrollGridToBooks) { LibraryGridSample() },
        ScreenFixture("Import-Reussi", beforeContent = freezeClock, afterContent = showSnackbar) { ImportSuccessSample() },
        ScreenFixture("Import-Doublon") { ImportDuplicateSample() },
        ScreenFixture("Import-Refuse") { ImportRejectedSample() },
        ScreenFixture("Livre-Supprimer") { DeleteBookDialogSample() },
        ScreenFixture("Parametres-EffacerJournal") { SettingsClearJournalDialogSample() },
        ScreenFixture("Parametres-Licences") { LicensesSample() },
        ScreenFixture("Lecture-Sommaire") { TocSheetSample() },
        ScreenFixture("Lecture-Journal") { JournalSample() },
        ScreenFixture("Lecture-Journal-Vide") { JournalEmptySample() },
        ScreenFixture("Lecture-Revenir") { ReturnCardSample() },
    )
}

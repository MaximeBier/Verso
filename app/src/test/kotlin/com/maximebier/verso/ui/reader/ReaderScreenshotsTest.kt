package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures (390 × 844 dp, xxhdpi) à comparer à docs/design/screens/1.11-lecture-barre-affichee.png,
 * 1.11-barre-affichee-sombre.png, 1.12-sommaire.png et 1.12-sommaire-sombre.png. Thème : celui du système
 * (qualificatif `+night` pour le sombre), comme les captures de la tâche 2.2.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class ReaderScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val bars = ReaderBarsState(
        bookTitle = "Madame Bovary",
        chapterPath = listOf("Deuxième partie", "Chapitre I"),
        readingPercent = 31,
        progression = 0.31f,
        remainingMinutes = 330,
    )

    private val toc = listOf(
        TocNode("Première partie", "p1.xhtml", (1..9).map { TocNode("Chapitre ${roman(it)}", "p1c$it.xhtml") }),
        TocNode("Deuxième partie", "p2.xhtml", (1..15).map { TocNode("Chapitre ${roman(it)}", "p2c$it.xhtml") }),
        TocNode("Troisième partie", "p3.xhtml", (1..11).map { TocNode("Chapitre ${roman(it)}", "p3c$it.xhtml") }),
    )

    @Test
    fun readerBarsLight() = captureBars("1.11-lecture-barre-affichee-clair")

    @Test
    @Config(qualifiers = "+night")
    fun readerBarsDark() = captureBars("1.11-lecture-barre-affichee-sombre")

    @Test
    fun tocLight() = captureToc("1.12-sommaire-clair")

    @Test
    @Config(qualifiers = "+night")
    fun tocDark() = captureToc("1.12-sommaire-sombre")

    private fun captureBars(file: String) {
        compose.setContent {
            VersoTheme {
                Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
                    SampleReadingText()
                    ReaderBars(visible = true, state = bars, onBack = {}, onTocClick = {}, onJournalClick = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$file.png")
    }

    private fun captureToc(file: String) {
        val readingOrder = preorder(toc) { it.children }.map { it.href }
        compose.setContent {
            VersoTheme {
                Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
                    SampleReadingText()
                }
                TocSheet(
                    bookTitle = "Madame Bovary",
                    summary = tocSummary(toc),
                    rows = buildTocRows(toc, readingOrder, currentHref = "p2c1.xhtml"),
                    readingPercent = 31,
                    onChapterClick = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        // La feuille modale est une fenêtre à part : capture de tout l’écran, voile compris.
        captureScreenRoboImage("build/outputs/roborazzi/$file.png")
    }

    /** Imitation du texte Readium (le vrai rendu est une WebView, absente de Robolectric). */
    @Composable
    private fun SampleReadingText() {
        val colors = VersoTheme.colors
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text("Deuxième partie", Modifier.fillMaxWidth(), color = colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Text("I", Modifier.fillMaxWidth(), color = colors.text, fontSize = 28.sp, textAlign = TextAlign.Center)
            repeat(4) {
                Text(
                    "Yonville-l’Abbaye est un bourg à huit lieues de Rouen, entre la route d’Abbeville et celle de Beauvais, au fond d’une vallée qu’arrose la Rieule.",
                    color = colors.text,
                    style = VersoTypography.body.copy(fontSize = 19.sp, lineHeight = 30.4.sp),
                )
            }
        }
    }

    private fun roman(n: Int): String =
        listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV")[n - 1]
}

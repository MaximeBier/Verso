package com.maximebier.verso.screenshots.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.preorder
import com.maximebier.verso.ui.reader.ReaderBars
import com.maximebier.verso.ui.reader.ReaderBarsState
import com.maximebier.verso.ui.reader.ReturnCard
import com.maximebier.verso.ui.reader.TocSheet
import com.maximebier.verso.ui.reader.buildTocRows
import com.maximebier.verso.ui.reader.tocSummary
import com.maximebier.verso.ui.theme.VersoTheme

/** Barre de la maquette 1.11 (Madame Bovary, 31 %, 5 h 30 restantes). */
internal val readerBarsSampleState = ReaderBarsState(
    bookTitle = "Madame Bovary",
    chapterPath = listOf("Deuxième partie", "Chapitre I"),
    readingPercent = 31,
    progression = 0.31f,
    remainingMinutes = 330,
)

/** Sommaire de la maquette 1.12 : trois parties de 9, 15 et 11 chapitres. */
internal val tocSample = listOf(
    TocNode("Première partie", "p1.xhtml", (1..9).map { TocNode("Chapitre ${roman(it)}", "p1c$it.xhtml") }),
    TocNode("Deuxième partie", "p2.xhtml", (1..15).map { TocNode("Chapitre ${roman(it)}", "p2c$it.xhtml") }),
    TocNode("Troisième partie", "p3.xhtml", (1..11).map { TocNode("Chapitre ${roman(it)}", "p3c$it.xhtml") }),
)

/** 1.11 : texte (imité) et barre de lecture affichée. */
@Composable
internal fun ReaderBarsSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
        ReaderBars(visible = true, state = readerBarsSampleState, onBack = {}, onTocClick = {}, onJournalClick = {})
    }
}

/** 1.12 : feuille Sommaire ouverte au-dessus du texte, chapitre courant « Deuxième partie, chapitre I ». */
@Composable
internal fun TocSheetSample() {
    val readingOrder = preorder(tocSample) { it.children }.map { it.href }
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
    }
    TocSheet(
        bookTitle = "Madame Bovary",
        summary = tocSummary(tocSample),
        rows = buildTocRows(tocSample, readingOrder, currentHref = "p2c1.xhtml"),
        readingPercent = 31,
        onChapterClick = {},
        onDismiss = {},
    )
}

/** 1.14 : carte « Revenir » en bas de l'écran de lecture. */
@Composable
internal fun ReturnCardSample(onStayHere: () -> Unit = {}, onGoBack: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        ReturnCard(
            location = "Partie II, chap. I",
            percent = 31,
            onStayHere = onStayHere,
            onGoBack = onGoBack,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        )
    }
}

/**
 * Imitation du texte Readium (le vrai rendu est une WebView, absente de Robolectric). Elle défile comme le vrai
 * texte : à 200 %, les paragraphes sortent de l'écran sans être coupés.
 */
@Composable
internal fun SampleReadingText() {
    val colors = VersoTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 56.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Text("Deuxième partie", Modifier.fillMaxWidth(), color = colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text("I", Modifier.fillMaxWidth(), color = colors.text, fontSize = 28.sp, textAlign = TextAlign.Center)
        repeat(4) {
            Text(
                "Yonville-l’Abbaye est un bourg à huit lieues de Rouen, entre la route d’Abbeville et celle de Beauvais, au fond d’une vallée qu’arrose la Rieule.",
                color = colors.text,
                style = VersoTheme.typography.body.copy(fontSize = 20.sp, lineHeight = 32.sp),
            )
        }
    }
}

private fun roman(n: Int): String =
    listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV")[n - 1]

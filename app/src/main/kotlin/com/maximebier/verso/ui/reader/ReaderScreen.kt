package com.maximebier.verso.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.reader.ReaderSurface
import com.maximebier.verso.ui.theme.VersoTheme

/** Point d’entrée de ReaderRoute (signature figée par la tâche 2.2). */
@Composable
fun ReaderDestination(bookId: Long, onBack: () -> Unit) {
    val viewModel: ReaderViewModel = viewModel(key = "reader-$bookId", factory = ReaderViewModel.factory(bookId))
    ReaderScreen(viewModel = viewModel, onBackToLibrary = onBack)
}

@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBackToLibrary: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    LaunchedEffect(density.fontScale, density.density) {
        viewModel.onDisplayMetrics(fontScale = density.fontScale, density = density.density)
    }
    KeepScreenOn()
    ImmersiveSystemBars(showSystemBars = state.barsVisible)
    BackHandler(enabled = state.tocVisible) { viewModel.hideToc() }
    if (state.failed) {
        LaunchedEffect(Unit) { onBackToLibrary() }
    }

    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        val publication = state.publication
        if (publication != null) {
            ReaderSurface(
                publication = publication,
                initialLocator = state.initialLocator,
                dark = isSystemInDarkTheme(),
                onReady = viewModel::onReaderReady,
                onCenterTap = viewModel::toggleBars,
                onInternalLink = viewModel::onInternalLinkFollowed,
            )
            // Surface quittée (rotation, thème) : plus de sauts vers l’ancienne rendition jusqu’au prochain onReady.
            DisposableEffect(publication) {
                onDispose { viewModel.onReaderGone() }
            }
        }
        ReaderBars(
            visible = state.barsVisible,
            state = ReaderBarsState(
                bookTitle = state.bookTitle,
                chapterPath = state.chapterPath,
                readingPercent = state.readingPercent,
                progression = state.readingProgression.toFloat(),
                remainingMinutes = state.remainingMinutes,
            ),
            onBack = onBackToLibrary,
            onTocClick = viewModel::showToc,
            // Journal : bascule uiState.journalVisible ; la feuille est affichée plus bas par la tâche 6.4.
            onJournalClick = viewModel::showJournal,
            onBottomBarHeightChanged = { bottomBarHeightPx = it },
        )
        state.returnCard?.let { card ->
            // Au-dessus de la barre du bas quand elle est affichée, sinon à 24 dp du bas plus la barre de gestes.
            val placement = if (state.barsVisible && bottomBarHeightPx > 0) {
                Modifier.padding(bottom = with(density) { bottomBarHeightPx.toDp() } + 16.dp)
            } else {
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)
            }
            ReturnCard(
                location = card.location ?: state.bookTitle,
                percent = card.percent,
                onStayHere = viewModel::stayHere,
                onGoBack = viewModel::goBack,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(placement)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (state.tocVisible) {
        val rows = remember(state.toc, state.readingOrderHrefs, state.currentHref) {
            buildTocRows(state.toc, state.readingOrderHrefs, state.currentHref)
        }
        val summary = remember(state.toc) { tocSummary(state.toc) }
        TocSheet(
            bookTitle = state.bookTitle,
            summary = summary,
            rows = rows,
            readingPercent = state.readingPercent,
            onChapterClick = viewModel::jumpToTocEntry,
            onDismiss = viewModel::hideToc,
        )
    }
    // La feuille « Journal » (6.2) est affichée ici par la tâche 6.4 quand state.journalVisible est vrai.
}

/** Écran maintenu allumé pendant la lecture uniquement ; la luminosité n’est jamais touchée. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

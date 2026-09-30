package com.maximebier.verso.ui.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
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
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.reader.ReaderStyle
import com.maximebier.verso.reader.ReaderSurface
import com.maximebier.verso.readium.ReadingStyle
import com.maximebier.verso.ui.reader.search.SearchScreen
import com.maximebier.verso.ui.theme.VersoTheme

/** Point d’entrée de ReaderRoute (signature figée par la tâche 2.2). */
@Composable
fun ReaderDestination(bookId: Long, onBack: () -> Unit, onOpenFailed: () -> Unit = onBack, openJournal: Boolean = false) {
    val viewModel: ReaderViewModel = viewModel(key = "reader-$bookId", factory = ReaderViewModel.factory(bookId, openJournal))
    ReaderScreen(viewModel = viewModel, onBackToLibrary = onBack, onOpenFailed = onOpenFailed)
}

@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBackToLibrary: () -> Unit, onOpenFailed: () -> Unit = onBackToLibrary) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val activity = LocalActivity.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    // Rotation ou thème du système : ON_STOP puis ON_START sur une activité recréée, la session continue.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onStop(changingConfigurations = activity?.isChangingConfigurations == true)
    }
    val theme = VersoTheme.theme
    LaunchedEffect(theme) { viewModel.onThemeChanged(theme) }
    // Le texte de lecture suit la taille de police d’Android ; la distance en écrans utilise la même échelle.
    val readingFontScale = remember(density, state.readingSettings.fontSizeSp) {
        ReadingStyle.readingFontScale(density, state.readingSettings.fontSizeSp)
    }
    LaunchedEffect(readingFontScale, density.density) {
        viewModel.onDisplayMetrics(fontScale = readingFontScale.toFloat(), density = density.density)
    }
    KeepScreenOn()
    ImmersiveSystemBars(showSystemBars = state.barsVisible)
    BackHandler(enabled = state.tocVisible) { viewModel.hideToc() }
    BackHandler(enabled = state.settingsVisible) { viewModel.hideReadingSettings() }
    BackHandler(enabled = state.searchVisible) { viewModel.hideSearch() }
    if (state.failed) {
        LaunchedEffect(Unit) { onOpenFailed() }
    }

    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        val publication = state.publication
        if (publication != null) {
            ReaderSurface(
                publication = publication,
                initialLocator = state.initialLocator,
                // Lu à la création du lecteur seulement ; les changements passent par ReaderController.submit.
                initialStyle = ReaderStyle(state.readingSettings, theme, state.scrollMode),
                positions = state.readingPositions,
                onReady = viewModel::onReaderReady,
                onTap = viewModel::toggleBars,
                onInternalLink = viewModel::onInternalLinkFollowed,
                onFailed = viewModel::onEngineFailed,
                bottomInset = if (state.scrollMode == ScrollMode.PAGES) pageFooterReserve() else 0.dp,
            )
            if (state.scrollMode == ScrollMode.PAGES) {
                // Sous la barre de lecture dans l’ordre de dessin : la barre, quand elle est affichée, le recouvre.
                PageFooter(
                    chapter = chapterLongLabel(state.displayedChapterPath),
                    pageInfo = state.pageInfo,
                    onTurn = viewModel::turnPage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
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
            onSettingsClick = viewModel::showReadingSettings,
            onBack = onBackToLibrary,
            onTocClick = viewModel::showToc,
            // Journal : bascule uiState.journalVisible ; la feuille est affichée plus bas.
            onJournalClick = viewModel::showJournal,
            onSearchClick = viewModel::showSearch,
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
        if (state.searchVisible) {
            SearchScreen(
                state = search,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
                onClose = viewModel::hideSearch,
                onResultClick = viewModel::openSearchResult,
            )
        }
    }

    if (state.tocVisible) {
        val rows = remember(state.toc, state.readingOrderHrefs, state.currentHref, state.currentProgression) {
            buildTocRows(state.toc, state.readingOrderHrefs, state.currentHref, state.currentProgression)
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
    journal?.let { journalState ->
        JournalSheet(
            state = journalState,
            onResume = viewModel::resumeFromJournal,
            onDismiss = viewModel::hideJournal,
        )
    }
    if (state.settingsVisible) {
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        ReadingSettingsSheet(
            state = ReadingSettingsSheetState(state.readingSettings, themeMode, state.scrollMode),
            onFontSelected = { font -> viewModel.updateReadingSettings { it.copy(font = font) } },
            onSmaller = { viewModel.updateReadingSettings { it.smaller() } },
            onLarger = { viewModel.updateReadingSettings { it.larger() } },
            onThemeSelected = viewModel::setThemeMode,
            onLineSpacingSelected = { spacing -> viewModel.updateReadingSettings { it.copy(lineSpacing = spacing) } },
            onMarginsSelected = { margins -> viewModel.updateReadingSettings { it.copy(margins = margins) } },
            onDismiss = viewModel::hideReadingSettings,
            scrollModeRow = { ScrollModeRow(selected = state.scrollMode, onSelect = viewModel::setScrollMode) },
        )
    }
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

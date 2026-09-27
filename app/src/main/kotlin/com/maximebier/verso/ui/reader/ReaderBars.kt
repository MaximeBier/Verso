package com.maximebier.verso.ui.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maximebier.verso.R
import com.maximebier.verso.core.text.longLocation
import com.maximebier.verso.ui.common.remainingTimeText
import com.maximebier.verso.ui.a11y.rememberReducedMotion
import com.maximebier.verso.ui.components.OutlinedPillButton
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoProgressBar
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme
import java.util.Locale

data class ReaderBarsState(
    val bookTitle: String,
    val chapterPath: List<String>,
    val readingPercent: Int,
    val progression: Float,
    val remainingMinutes: Int,
)

private const val BAR_ANIMATION_MS = 200

/** Barre de lecture (1.11) : en haut, retour, titre, chapitre ; en bas, progression, temps restant, boutons. */
@Composable
fun ReaderBars(
    visible: Boolean,
    state: ReaderBarsState,
    onBack: () -> Unit,
    onTocClick: () -> Unit,
    onJournalClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBottomBarHeightChanged: (Int) -> Unit = {},
) {
    val reduced = rememberReducedMotion()
    LaunchedEffect(visible) { if (!visible) onBottomBarHeightChanged(0) }
    // La Box n’a aucun modificateur de pointeur : les taps hors des barres atteignent le texte dessous.
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (reduced) EnterTransition.None else fadeIn(tween(BAR_ANIMATION_MS)) + slideInVertically(tween(BAR_ANIMATION_MS)) { -it },
            exit = if (reduced) ExitTransition.None else fadeOut(tween(BAR_ANIMATION_MS)) + slideOutVertically(tween(BAR_ANIMATION_MS)) { -it },
        ) {
            ReaderTopBar(title = state.bookTitle, chapter = chapterLongLabel(state.chapterPath), onBack = onBack)
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (reduced) EnterTransition.None else fadeIn(tween(BAR_ANIMATION_MS)) + slideInVertically(tween(BAR_ANIMATION_MS)) { it },
            exit = if (reduced) ExitTransition.None else fadeOut(tween(BAR_ANIMATION_MS)) + slideOutVertically(tween(BAR_ANIMATION_MS)) { it },
        ) {
            ReaderBottomBar(
                state = state,
                onTocClick = onTocClick,
                onJournalClick = onJournalClick,
                modifier = Modifier.onSizeChanged { onBottomBarHeightChanged(it.height) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderTopBar(title: String, chapter: String?, onBack: () -> Unit) {
    val colors = VersoTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .heightIn(min = VersoDimens.topBarReader)
                // Marge verticale sans effet à 100 % (hauteur minimale) ; elle décolle le texte des bords à 200 %.
                .padding(start = 4.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VersoIconButton(
                icon = VersoIcons.ArrowLeft,
                contentDescription = stringResource(R.string.reader_back_to_library),
                onClick = onBack,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    // 18 sp 700, interligne 1,25 (référence 1.11).
                    style = VersoTheme.typography.bookTitleStrong.copy(lineHeight = 22.5.sp),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chapter != null) {
                    Text(
                        text = chapter,
                        // 14 sp, interligne 1,3 (référence 1.11).
                        style = VersoTheme.typography.caption.copy(lineHeight = 18.2.sp),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderBottomBar(
    state: ReaderBarsState,
    onTocClick: () -> Unit,
    onJournalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    Column(modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.common_percent_read, state.readingPercent),
                    style = VersoTheme.typography.bodyStrong,
                    color = colors.text,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = remainingTimeText(state.remainingMinutes),
                    style = VersoTheme.typography.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
            // Barre du livre en cours (§3.7) : 6 dp, accent ; elle occupe toute la largeur.
            VersoProgressBar(fraction = state.progression, current = true)
            // Côte à côte et de même largeur ; l’un sous l’autre quand le texte système grossit, au lieu de couper
            // « Sommaire » au milieu du mot.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Boutons contour de la référence §3.8 (48 dp, pilule, bordure outline, 16 sp 600, icône 20).
                OutlinedPillButton(
                    text = stringResource(R.string.reader_toc),
                    onClick = onTocClick,
                    modifier = Modifier.weight(1f),
                    icon = VersoIcons.ListBullets,
                )
                OutlinedPillButton(
                    text = stringResource(R.string.reader_journal),
                    onClick = onJournalClick,
                    modifier = Modifier.weight(1f),
                    icon = VersoIcons.History,
                )
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBarsIgnoringVisibility))
    }
}

/** Forme longue « Deuxième partie, chapitre I » ([longLocation], gabarit `common_location_long`). */
@Composable
fun chapterLongLabel(path: List<String>): String? {
    val template = stringResource(R.string.common_location_long)
    return longLocation(path) { part, chapter -> String.format(Locale.FRENCH, template, part, chapter) }
}

/** Mode immersif : barres système masquées pendant la lecture, rétablies avec la barre de lecture. */
@Composable
fun ImmersiveSystemBars(showSystemBars: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    val controller = remember(activity, view) {
        WindowCompat.getInsetsController(activity.window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    LaunchedEffect(controller, showSystemBars) {
        if (showSystemBars) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

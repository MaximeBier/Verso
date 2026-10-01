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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoProgressBar
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
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
    onSearchClick: () -> Unit,
    onNotesClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
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
                tools = readerTools(onTocClick, onJournalClick, onSearchClick, onNotesClick, onSettingsClick),
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

/** Outil de la barre du bas (2.01) : icône, ou glyphe « Aa », au-dessus du libellé. */
data class ReaderTool(val label: String, val glyph: ToolGlyph, val onClick: () -> Unit)

sealed interface ToolGlyph {
    data class Icon(val vector: ImageVector) : ToolGlyph
    data class Text(val text: String) : ToolGlyph
}

/** Ordre de la maquette 2.01, plus « Notes » (V3) avant les réglages : Sommaire, Journal, Rechercher, Notes, Réglages. */
@Composable
private fun readerTools(
    onTocClick: () -> Unit,
    onJournalClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotesClick: () -> Unit,
    onSettingsClick: () -> Unit,
): List<ReaderTool> = buildList {
    add(ReaderTool(stringResource(R.string.reader_toc), ToolGlyph.Icon(VersoIcons.ListBullets), onTocClick))
    add(ReaderTool(stringResource(R.string.reader_journal), ToolGlyph.Icon(VersoIcons.History), onJournalClick))
    add(ReaderTool(stringResource(R.string.reader_search), ToolGlyph.Icon(VersoIcons.Search), onSearchClick))
    add(ReaderTool(stringResource(R.string.reader_notes), ToolGlyph.Icon(VersoIcons.Note), onNotesClick))
    add(ReaderTool(stringResource(R.string.reader_settings), ToolGlyph.Text(stringResource(R.string.reader_settings_glyph)), onSettingsClick))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderBottomBar(state: ReaderBarsState, tools: List<ReaderTool>, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Column(modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                // 2.01 : 16 dp en haut et en bas, 20 dp sur les côtés.
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
            ReaderToolbar(tools)
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBarsIgnoringVisibility))
    }
}

private val ToolGap = 4.dp
private val ToolCellPadding = 4.dp
private val ToolMinHeight = 64.dp
private val ToolIconSize = 22.dp

/**
 * Outils en une rangée de colonnes égales (2.01 : 64 dp de haut, icône 22 dp, texte 14 sp 600). Quand un libellé ne
 * tient plus (texte système agrandi), deux colonnes, puis une, plutôt que de couper « Rechercher ».
 */
@Composable
internal fun ReaderToolbar(tools: List<ReaderTool>, modifier: Modifier = Modifier) {
    val style = VersoTheme.typography.captionSemiBold
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = fittingColumns(
            labels = tools.map { it.label },
            style = style,
            maxWidth = maxWidth,
            gap = ToolGap,
            cellPadding = ToolCellPadding,
            candidates = listOf(tools.size, 2, 1).distinct(),
        )
        AdaptiveGrid(items = tools, columns = columns, gap = ToolGap) { tool, cellModifier ->
            ToolButton(tool, cellModifier)
        }
    }
}

@Composable
private fun ToolButton(tool: ReaderTool, modifier: Modifier) {
    val colors = VersoTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = ToolMinHeight)
            .clip(VersoShapes.small)
            .clickable(role = Role.Button, onClick = tool.onClick)
            .padding(horizontal = ToolCellPadding, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        when (val glyph = tool.glyph) {
            is ToolGlyph.Icon -> Icon(glyph.vector, contentDescription = null, tint = colors.text, modifier = Modifier.size(ToolIconSize))
            // 2.01 : « Aa » 20 sp 700, décoratif ; le bouton se lit par son libellé.
            is ToolGlyph.Text -> Text(
                text = glyph.text,
                style = VersoTheme.typography.bodyStrong.copy(fontSize = 20.sp, lineHeight = 22.sp),
                color = colors.text,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(text = tool.label, style = VersoTheme.typography.captionSemiBold, color = colors.text, textAlign = TextAlign.Center)
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

package com.maximebier.verso.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.reader.PageInfo
import com.maximebier.verso.ui.theme.VersoTheme

/** Mesures de la maquette 2.05 : 24 dp sur les côtés, 28 dp sous le texte du pied, 16 dp entre le texte et lui. */
object PageFooterMetrics {
    val side: Dp = 24.dp
    val bottom: Dp = 28.dp
    val gap: Dp = 16.dp
    /** Écart entre le chapitre et « Page 2 sur 9 » (maquette : 12 px). */
    val between: Dp = 12.dp

    /**
     * À partir de cette échelle de police Android, le chapitre passe sur toute la largeur (deux lignes au plus) et
     * « Page 2 sur 9 » en dessous : sur une seule ligne, le chapitre serait coupé à 200 %.
     */
    const val STACKED_FONT_SCALE: Float = 1.5f

    /** Lignes du chapitre quand le pied est empilé. */
    const val STACKED_CHAPTER_LINES: Int = 2
}

/** Vrai quand le pied de page s'empile (voir [PageFooterMetrics.STACKED_FONT_SCALE]). */
@Composable
private fun footerIsStacked(): Boolean = LocalDensity.current.fontScale >= PageFooterMetrics.STACKED_FONT_SCALE

/**
 * Hauteur réservée sous le texte en mode pages : une ligne de légende (suit la taille de police) et ses marges ;
 * trois lignes quand le pied s'empile (chapitre sur deux lignes, puis la page).
 */
@Composable
fun pageFooterReserve(): Dp {
    val lines = if (footerIsStacked()) PageFooterMetrics.STACKED_CHAPTER_LINES + 1 else 1
    return with(LocalDensity.current) { VersoTheme.typography.caption.lineHeight.toDp() } * lines +
        PageFooterMetrics.bottom + PageFooterMetrics.gap
}

/**
 * Pied de page discret du mode pages (2.05) : chapitre à gauche, « Page 2 sur 9 » à droite. Pour TalkBack, un seul
 * élément focalisable (« Deuxième partie, chapitre I, Page 2 sur 9 ») qui porte les actions « Page suivante » et
 * « Page précédente » ([onTurn], vrai = suivante) : le texte de la WebView ne peut pas les porter.
 */
@Composable
fun PageFooter(
    chapter: String?,
    pageInfo: PageInfo?,
    modifier: Modifier = Modifier,
    onTurn: ((forward: Boolean) -> Unit)? = null,
) {
    val colors = VersoTheme.colors
    val nextLabel = stringResource(R.string.reader_page_next)
    val previousLabel = stringResource(R.string.reader_page_previous)
    val stacked = footerIsStacked()
    val footerModifier = modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .padding(start = PageFooterMetrics.side, end = PageFooterMetrics.side, bottom = PageFooterMetrics.bottom)
        .semantics(mergeDescendants = true) {
            // Sans chapitre ni page (avant la première mesure), pas de nœud TalkBack vide.
            if (onTurn != null && (!chapter.isNullOrEmpty() || pageInfo != null)) {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { onTurn(true); true },
                    CustomAccessibilityAction(previousLabel) { onTurn(false); true },
                )
            }
        }
    val pageText = pageInfo?.let { stringResource(R.string.reader_page_of, it.page, it.pageCount) }
    if (stacked) {
        Column(footerModifier) {
            Text(
                text = chapter.orEmpty(),
                style = VersoTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = PageFooterMetrics.STACKED_CHAPTER_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (pageText != null) {
                Text(text = pageText, style = VersoTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    } else {
        Row(footerModifier, horizontalArrangement = Arrangement.spacedBy(PageFooterMetrics.between)) {
            Text(
                text = chapter.orEmpty(),
                style = VersoTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pageText != null) {
                Text(text = pageText, style = VersoTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

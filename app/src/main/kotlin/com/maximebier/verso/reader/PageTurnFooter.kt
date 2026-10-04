package com.maximebier.verso.reader

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.maximebier.verso.R
import com.maximebier.verso.ui.reader.PageFooterMetrics
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Pied de page dessiné sur la photo d’une page qui tourne (le vrai est caché pendant le tour), comme le vrai
 * (`PageFooter`, une ligne) : le chapitre est repris de la photo de l’écran, « Page x sur y » est redessiné.
 */
class PageFooterPainter internal constructor(
    private val context: Context,
    private val measurer: TextMeasurer,
    private val style: () -> TextStyle,
    private val density: () -> Density,
) {
    /** `pages` : écart avec la page affichée. Rien hors du chapitre affiché (chapitre et compte inconnus). */
    fun paint(bitmap: Bitmap, screen: Bitmap?, info: PageInfo?, pages: Int) {
        if (info == null || !bitmap.isMutable) return
        val target = info.page + pages
        if (target !in 1..info.pageCount) return
        val d = density()
        val textStyle = style()
        val text = measurer.measure(context.getString(R.string.reader_page_of, target, info.pageCount), textStyle, density = d)
        val current = measurer.measure(context.getString(R.string.reader_page_of, info.page, info.pageCount), textStyle, density = d)
        val side = with(d) { PageFooterMetrics.side.toPx() }
        val bottom = with(d) { PageFooterMetrics.bottom.toPx() }
        val between = with(d) { PageFooterMetrics.between.toPx() }
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val top = h - bottom - text.size.height
        val canvas = android.graphics.Canvas(bitmap)
        // Chapitre : la partie gauche du pied de page de la photo, jusqu’avant le numéro affiché.
        if (screen != null && screen.width == bitmap.width && screen.height == bitmap.height) {
            val right = (w - side - current.size.width - between / 2).toInt()
            val rect = android.graphics.Rect(0, (top - between).toInt(), right, bitmap.height)
            canvas.drawBitmap(screen, rect, rect, null)
        }
        CanvasDrawScope().draw(d, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap()), Size(w, h)) {
            drawText(text, topLeft = Offset(w - side - text.size.width, top))
        }
    }
}

@Composable
fun rememberPageFooterPainter(): PageFooterPainter {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val style by rememberUpdatedState(VersoTheme.typography.caption.copy(color = VersoTheme.colors.textSecondary))
    val density by rememberUpdatedState(LocalDensity.current)
    return remember(context, measurer) { PageFooterPainter(context, measurer, { style }, { density }) }
}

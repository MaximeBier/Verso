package com.maximebier.verso.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.ui.reader.PageFooterMetrics
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.graphics.Color as ComposeColor

/** Pied de page dessiné sur la photo d’une page qui tourne, sur une ligne ou empilé (texte d’Android agrandi). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageFooterPainterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val style = TextStyle(fontSize = 12.sp, color = ComposeColor.Black)
    private val info = PageInfo(page = 3, pageCount = 10)

    private fun painter(fontScale: Float): Pair<PageFooterPainter, Density> {
        val density = Density(1f, fontScale)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        return PageFooterPainter(context, measurer, { style }, { density }) to density
    }

    /** Hauteur de « Page 4 sur 10 » et haut de la ligne de la page, comme le peintre les calcule. */
    private fun pageLineTop(fontScale: Float): Pair<Int, Int> {
        val density = Density(1f, fontScale)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val text = measurer.measure(context.getString(R.string.reader_page_of, 4, 10), style, density = density)
        val bottom = with(density) { PageFooterMetrics.bottom.toPx() }
        return text.size.height to (H - bottom - text.size.height).toInt()
    }

    private fun page(): Bitmap = createBitmap(W, H).apply { eraseColor(Color.WHITE) }
    private fun screen(): Bitmap = createBitmap(W, H).apply { eraseColor(Color.RED) }

    private fun Bitmap.darkPixelsIn(xs: IntRange, ys: IntRange): Int = ys.sumOf { y ->
        xs.count { x ->
            val p = getPixel(x, y)
            Color.red(p) < 100 && Color.green(p) < 100 && Color.blue(p) < 100
        }
    }

    @Test
    fun atNormalTextSizeThePageNumberIsOnTheRightAndTheChapterOnTheLeft() {
        val (painter, _) = painter(fontScale = 1f)
        val bitmap = page()
        painter.paint(bitmap, screen(), info, pages = 1)
        val (height, top) = pageLineTop(1f)
        val line = top until top + height
        assertThat(bitmap.darkPixelsIn(W / 2 until W, line)).isGreaterThan(0)
        assertThat(bitmap.darkPixelsIn(0 until W / 2, line)).isEqualTo(0)
        // Chapitre repris de la photo de l’écran à gauche seulement, jamais sous le numéro de page.
        assertThat(bitmap.getPixel(10, top)).isEqualTo(Color.RED)
        assertThat(bitmap.getPixel(W - 5, top - 1)).isEqualTo(Color.WHITE)
    }

    @Test
    fun atLargeTextSizeTheFooterIsStackedLikeTheRealOne() {
        val scale = PageFooterMetrics.STACKED_FONT_SCALE * 4 / 3 // 200 %
        val (painter, _) = painter(fontScale = scale)
        val bitmap = page()
        painter.paint(bitmap, screen(), info, pages = 1)
        val (height, top) = pageLineTop(scale)
        val line = top until top + height
        // « Page 4 sur 10 » seule, à gauche.
        assertThat(bitmap.darkPixelsIn(0 until W / 2, line)).isGreaterThan(0)
        assertThat(bitmap.darkPixelsIn(W * 3 / 4 until W, line)).isEqualTo(0)
        // Chapitre : les deux lignes au-dessus, sur toute la largeur.
        val chapterTop = top - PageFooterMetrics.STACKED_CHAPTER_LINES * height
        assertThat(bitmap.getPixel(W - 5, top - 1)).isEqualTo(Color.RED)
        assertThat(bitmap.getPixel(5, chapterTop + 1)).isEqualTo(Color.RED)
        assertThat(bitmap.getPixel(W - 5, chapterTop - 2)).isEqualTo(Color.WHITE)
    }

    @Test
    fun nothingIsPaintedOutsideTheDisplayedChapter() {
        val (painter, _) = painter(fontScale = 1f)
        val bitmap = page()
        painter.paint(bitmap, screen(), info.copy(page = 10), pages = 1)
        painter.paint(bitmap, screen(), null, pages = 1)
        assertThat(bitmap.darkPixelsIn(0 until W, 0 until H)).isEqualTo(0)
        assertThat(bitmap.getPixel(10, H - 5)).isEqualTo(Color.WHITE)
    }

    private companion object {
        const val W = 400
        const val H = 300
    }
}

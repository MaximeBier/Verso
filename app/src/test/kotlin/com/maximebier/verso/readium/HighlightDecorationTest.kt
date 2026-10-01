package com.maximebier.verso.readium

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.reader.HighlightMark
import com.maximebier.verso.reader.testLocator
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration

@RunWith(AndroidJUnit4::class)
class HighlightDecorationTest {
    @Test
    fun oneDecorationPerHighlightWithBackgroundAndUnderline() {
        val marks = listOf(HighlightMark(7, testLocator()), HighlightMark(9, testLocator(progression = 0.8)))
        AppTheme.entries.forEach { theme ->
            val decorations = HighlightDecoration.decorations(marks, theme)
            assertThat(decorations.map { HighlightDecoration.idOf(it.id) }).containsExactly(7L, 9L).inOrder()
            val style = decorations.first().style as Decoration.Style.Highlight
            assertThat(style.tint).isEqualTo(ReadingStyle.palette(theme).highlight.toArgb())
            assertThat(SearchMatchDecoration.element(decorations.first())).contains("border-bottom: 2px solid")
        }
    }

    @Test
    fun foreignIdsAreIgnored() {
        assertThat(HighlightDecoration.idOf("verso-search-match")).isNull()
        assertThat(HighlightDecoration.idOf("highlight-abc")).isNull()
    }
}

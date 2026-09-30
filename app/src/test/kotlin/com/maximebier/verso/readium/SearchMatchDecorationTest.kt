package com.maximebier.verso.readium

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoColors
import com.maximebier.verso.ui.theme.VersoPalette
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

// Robolectric : `Decoration` est Parcelable (classes Android).
@RunWith(AndroidJUnit4::class)
class SearchMatchDecorationTest {

    private val locator = Locator(
        href = Url("c1.xhtml")!!,
        mediaType = MediaType.XHTML,
        text = Locator.Text(before = "petite ", highlight = "rivière", after = " qui"),
    )

    @Test
    fun oneHighlightInTheSearchGroupWithThemeColors() {
        val decoration = SearchMatchDecoration.decorations(locator, AppTheme.SEPIA).single()
        assertThat(decoration.locator).isEqualTo(locator)
        val style = decoration.style as Decoration.Style.Highlight
        assertThat(style.tint).isEqualTo(VersoPalette.Sepia.highlight.toArgb())
        val html = SearchMatchDecoration.element(decoration)
        // ReadiumCSS rend transparent le background-color de tout élément du texte quand un fond est choisi.
        assertThat(html).doesNotContain("background-color")
        assertThat(html).contains("box-shadow: inset 0 0 0 100vmax #E9C98C")
        assertThat(html).contains("border-bottom: 2px solid #2E2419")
        assertThat(html).contains("mix-blend-mode: darken")
    }

    @Test
    fun darkThemesLightenInsteadOfDarken() {
        val html = SearchMatchDecoration.element(SearchMatchDecoration.decorations(locator, AppTheme.NIGHT).single())
        assertThat(html).contains("mix-blend-mode: lighten")
        assertThat(html).contains("box-shadow: inset 0 0 0 100vmax #352A19")
    }

    /**
     * Le calque est opaque et fondu en « darken » (clair) ou « lighten » (sombre) : le fond sous le mot devient
     * exactement `highlight` et le texte garde sa couleur, seulement si `highlight` est, canal par canal, entre le
     * fond et le texte. Garde-fou si tokens.json change.
     */
    @Test
    fun highlightSitsBetweenBackgroundAndTextInEveryTheme() {
        AppTheme.entries.forEach { theme ->
            val c: VersoColors = SearchMatchDecoration.palette(theme)
            listOf(Color::red, Color::green, Color::blue).forEach { channel ->
                val bg = channel(c.background); val hl = channel(c.highlight); val tx = channel(c.text)
                if (theme.isDark) {
                    assertThat(hl).isAtLeast(bg); assertThat(hl).isAtMost(tx)
                } else {
                    assertThat(hl).isAtMost(bg); assertThat(hl).isAtLeast(tx)
                }
            }
        }
    }
}

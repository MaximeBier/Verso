package com.maximebier.verso.readium

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoColors
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.shared.publication.Locator

/**
 * Mot trouvé par la recherche, marqué dans le texte par un fond et un soulignement (jamais la couleur seule).
 * Readium dessine les décorations par-dessus le texte : un calque opaque `highlight` (ombre intérieure, voir [element]), fondu en `darken` (thèmes
 * clairs) ou `lighten` (thèmes sombres), donne exactement le fond `highlight` sous un texte inchangé (7:1 gardé).
 */
object SearchMatchDecoration {
    const val GROUP = "verso-search"
    private const val ID = "verso-search-match"
    private const val EXTRA_BACKGROUND = "background"
    private const val EXTRA_LINE = "line"
    private const val EXTRA_BLEND = "blend"
    private const val CSS_CLASS = "verso-search-match"

    fun palette(theme: AppTheme): VersoColors = ReadingStyle.palette(theme)

    fun decorations(locator: Locator, theme: AppTheme): List<Decoration> {
        val colors = palette(theme)
        return listOf(
            Decoration(
                id = ID,
                locator = locator,
                style = Decoration.Style.Highlight(tint = colors.highlight.toArgb()),
                extras = mapOf(
                    EXTRA_BACKGROUND to colors.highlight.css(),
                    EXTRA_LINE to colors.text.css(),
                    EXTRA_BLEND to if (theme.isDark) "lighten" else "darken",
                ),
            ),
        )
    }

    /** Gabarit de `Decoration.Style.Highlight` (seul style utilisé par Verso) : une boîte par ligne du mot. */
    fun templates(): HtmlDecorationTemplates = HtmlDecorationTemplates {
        set(
            Decoration.Style.Highlight::class,
            HtmlDecorationTemplate(
                layout = HtmlDecorationTemplate.Layout.BOXES,
                width = HtmlDecorationTemplate.Width.WRAP,
                element = ::element,
                stylesheet = ".$CSS_CLASS { box-sizing: border-box; pointer-events: none; }",
            ),
        )
    }

    fun element(decoration: Decoration): String {
        val background = decoration.extras[EXTRA_BACKGROUND] as? String ?: "transparent"
        val line = decoration.extras[EXTRA_LINE] as? String ?: "currentColor"
        val blend = decoration.extras[EXTRA_BLEND] as? String ?: "normal"
        // Fond par une ombre intérieure : ReadiumCSS impose `background-color: transparent !important` à tout élément
        // du texte dès qu’une couleur de fond est choisie (`--USER__backgroundColor`), décorations comprises.
        return "<div class=\"$CSS_CLASS\" style=\"box-shadow: inset 0 0 0 100vmax $background; " +
            "border-bottom: 2px solid $line; mix-blend-mode: $blend;\"></div>"
    }

    private fun Color.css(): String = "#%06X".format(toArgb() and 0xFFFFFF)
}

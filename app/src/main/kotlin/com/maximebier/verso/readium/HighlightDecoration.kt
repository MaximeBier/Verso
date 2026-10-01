package com.maximebier.verso.readium

import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.reader.HighlightMark
import org.readium.r2.navigator.Decoration

/**
 * Surlignages dessinés dans le texte (V3) : même rendu que le mot trouvé par la recherche, fond `highlight` et
 * soulignement (jamais la couleur seule), par le gabarit de [SearchMatchDecoration]. Groupe à part, avec un écouteur :
 * le toucher d’un surlignage arrive par `onDecorationActivated`, pas comme un tap.
 */
object HighlightDecoration {
    const val GROUP = "verso-highlights"
    private const val PREFIX = "highlight-"

    fun decorations(marks: List<HighlightMark>, theme: AppTheme): List<Decoration> =
        marks.map { SearchMatchDecoration.decoration("$PREFIX${it.id}", it.locator, theme) }

    fun idOf(decorationId: String): Long? =
        if (decorationId.startsWith(PREFIX)) decorationId.removePrefix(PREFIX).toLongOrNull() else null
}

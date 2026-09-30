package com.maximebier.verso.core.text

/** Longueur maximale du contexte avant et après le mot trouvé, en caractères (maquette 2.06 : deux à trois lignes). */
const val SEARCH_CONTEXT_CHARS = 90

/** Extrait d'un résultat de recherche : contexte avant, mot trouvé, contexte après, prêts à afficher. */
data class SearchSnippet(val before: String, val match: String, val after: String)

private const val ELLIPSIS = "…"
private val WHITESPACE = Regex("\\s+")

/**
 * Espaces fusionnés ; contexte de plus de [maxContextChars] caractères coupé à la frontière de mot la plus proche
 * du mot trouvé, marqué par « … » (au début pour [before], à la fin pour [after]). Un contexte court est gardé tel quel.
 */
fun searchSnippet(
    before: String,
    match: String,
    after: String,
    maxContextChars: Int = SEARCH_CONTEXT_CHARS,
): SearchSnippet {
    val b = before.replace(WHITESPACE, " ")
    val a = after.replace(WHITESPACE, " ")
    return SearchSnippet(
        before = if (b.length <= maxContextChars) b else cutBefore(b, maxContextChars),
        match = match.replace(WHITESPACE, " "),
        after = if (a.length <= maxContextChars) a else cutAfter(a, maxContextChars),
    )
}

/** Garde la fin de [text] : on part de `length − max` et on avance jusqu'au début du mot suivant. */
private fun cutBefore(text: String, max: Int): String {
    var start = text.length - max
    while (start < text.length && text[start] != ' ') start++
    val kept = text.substring(start).trimStart()
    return ELLIPSIS + kept
}

/** Garde le début de [text] : on recule depuis `max` jusqu'à la fin du mot précédent. */
private fun cutAfter(text: String, max: Int): String {
    var end = max
    while (end > 0 && text[end] != ' ') end--
    val kept = text.substring(0, end).trimEnd()
    return kept + ELLIPSIS
}

package com.maximebier.verso.core.notes

/** Passage et son contexte, comme `Locator.Text` de Readium (highlight, before, after). */
data class TextQuote(val highlight: String, val before: String = "", val after: String = "")

/** Plage de caractères [start, end) ; start < end. */
data class CharSpan(val start: Int, val end: Int) {
    init {
        require(start in 0 until end) { "Plage vide ou négative : [$start, $end)" }
    }

    fun overlaps(other: CharSpan): Boolean = start < other.end && other.start < end

    fun union(other: CharSpan): CharSpan = CharSpan(minOf(start, other.start), maxOf(end, other.end))
}

/**
 * Passages retrouvés dans le texte brut d’un chapitre (espaces normalisés) : c’est sur ces plages que se décide le
 * chevauchement de deux surlignages (spec V3, « Chevauchement »).
 */
object TextQuotes {
    /** Caractères de contexte gardés avant et après un passage reconstruit. */
    const val CONTEXT_CHARS = 32

    /** Longueur maximale du début de passage montré dans la barre de sélection (3.04). */
    const val PREVIEW_CHARS = 48

    // \s ne couvre ni U+00A0 ni U+202F : ajoutés explicitement.
    private val SPACES = Regex("[\\s\\u00A0\\u202F\\u2007]+")

    /** Espaces (dont insécables et retours à la ligne) réduits à une espace, bords coupés. */
    fun normalize(text: String): String = collapse(text).trim()

    /** Comme [normalize], sans couper les bords (contexte d’un passage). */
    fun collapse(text: String): String = SPACES.replace(text, " ")

    /**
     * Plage du passage dans [chapterText] (déjà normalisé). Plusieurs occurrences : celle dont le contexte (fin de
     * `before`, début de `after`) correspond sur le plus de caractères ; à égalité, la première.
     */
    fun locate(chapterText: String, quote: TextQuote): CharSpan? {
        val needle = normalize(quote.highlight)
        if (needle.isEmpty()) return null
        val before = collapse(quote.before)
        val after = collapse(quote.after)
        var best: CharSpan? = null
        var bestScore = -1
        var from = chapterText.indexOf(needle)
        while (from >= 0) {
            val end = from + needle.length
            val score = commonSuffix(chapterText.substring(0, from), before) +
                commonPrefix(chapterText.substring(end), after)
            if (score > bestScore) {
                best = CharSpan(from, end)
                bestScore = score
            }
            from = chapterText.indexOf(needle, from + 1)
        }
        return best
    }

    /** Passage de [chapterText] à [span], avec [CONTEXT_CHARS] de contexte de chaque côté. */
    fun quoteAt(chapterText: String, span: CharSpan): TextQuote = TextQuote(
        highlight = chapterText.substring(span.start, span.end),
        before = chapterText.substring((span.start - CONTEXT_CHARS).coerceAtLeast(0), span.start),
        after = chapterText.substring(span.end, (span.end + CONTEXT_CHARS).coerceAtMost(chapterText.length)),
    )

    /** Début du passage pour la barre de sélection : coupé sur un mot, suivi de « … » s’il est plus long. */
    fun preview(text: String, maxChars: Int = PREVIEW_CHARS): String {
        val normalized = normalize(text)
        if (normalized.length <= maxChars) return normalized
        val cut = normalized.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
        return normalized.substring(0, cut).trimEnd() + "…"
    }

    private fun commonSuffix(a: String, b: String): Int {
        var n = 0
        while (n < a.length && n < b.length && a[a.length - 1 - n] == b[b.length - 1 - n]) n++
        return n
    }

    private fun commonPrefix(a: String, b: String): Int {
        var n = 0
        while (n < a.length && n < b.length && a[n] == b[n]) n++
        return n
    }
}


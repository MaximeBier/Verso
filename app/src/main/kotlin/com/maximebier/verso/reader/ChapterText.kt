package com.maximebier.verso.reader

/** Texte brut d’un chapitre XHTML, pour l’extrait de la carte Reprendre (Variante A). */
internal object ChapterText {

    private val DROPPED_BLOCKS = Regex("(?is)<(head|script|style)\\b.*?</\\1>")
    private val TAGS = Regex("(?s)<[^>]+>")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
    private val WHITESPACE = Regex("\\s+")

    // « &amp; » en dernier, pour ne pas décoder deux fois « &amp;lt; ».
    private val NAMED_ENTITIES = linkedMapOf(
        "&nbsp;" to " ",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&amp;" to "&",
    )

    fun plainText(html: String): String {
        var text = DROPPED_BLOCKS.replace(html, " ")
        text = TAGS.replace(text, " ")
        text = NUMERIC_ENTITY.replace(text) { match ->
            val radix = if (match.groupValues[1].isNotEmpty()) 16 else 10
            val code = match.groupValues[2].toIntOrNull(radix)
            if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else " "
        }
        NAMED_ENTITIES.forEach { (entity, value) -> text = text.replace(entity, value) }
        return WHITESPACE.replace(text, " ").trim()
    }

    /** Extrait commençant au mot qui suit `progression × longueur`, borné à `maxChars`. */
    fun excerptAt(text: String, progression: Double, maxChars: Int): String? {
        if (text.isEmpty()) return null
        val target = (progression.coerceIn(0.0, 1.0) * text.length).toInt().coerceIn(0, text.length - 1)
        val start = if (target == 0) 0 else text.indexOf(' ', target).let { if (it < 0) target else it + 1 }
        if (start >= text.length) return null
        return text.substring(start, minOf(text.length, start + maxChars)).trim().takeIf { it.isNotEmpty() }
    }
}

package com.maximebier.verso.core.text

private val COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val HEAD = Regex("<head\\b[^>]*>.*?</head\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val SCRIPT = Regex("<script\\b[^>]*>.*?</script\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val STYLE = Regex("<style\\b[^>]*>.*?</style\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val PROCESSING_INSTRUCTIONS = Regex("<\\?.*?\\?>", RegexOption.DOT_MATCHES_ALL)
private val DECLARATIONS = Regex("<![^>]*>")
private val TAG = Regex("</?([A-Za-z][A-Za-z0-9:_-]*)[^>]*>")
private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")
private val WORD = Regex("[\\p{L}\\p{N}\\p{M}]+(?:[''‐‑-][\\p{L}\\p{N}\\p{M}]+)*")

/** Balises en ligne : retirées sans espace pour ne pas couper un mot (« ex<em>tra</em>ordinaire »). */
private val INLINE_TAGS = setOf(
    "a", "abbr", "b", "bdi", "bdo", "cite", "code", "data", "del", "dfn", "em", "font", "i", "ins",
    "kbd", "mark", "q", "rb", "rp", "rt", "ruby", "s", "samp", "small", "span", "strike", "strong",
    "sub", "sup", "time", "tt", "u", "var", "wbr",
)

/** Entités nommées courantes ; une entité inconnue est retirée. */
private val NAMED_ENTITIES = mapOf(
    "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "rsquo" to "'", "lsquo" to "'", "rdquo" to """, "ldquo" to """,
    "laquo" to "«", "raquo" to "»", "hellip" to "…", "mdash" to "—", "ndash" to "–",
    "shy" to "", "oelig" to "œ", "OElig" to "Œ", "aelig" to "æ", "AElig" to "Æ",
    "agrave" to "à", "acirc" to "â", "ccedil" to "ç", "eacute" to "é", "egrave" to "è",
    "ecirc" to "ê", "euml" to "ë", "icirc" to "î", "iuml" to "ï", "ocirc" to "ô",
    "ugrave" to "ù", "ucirc" to "û", "uuml" to "ü", "yuml" to "ÿ",
    "Agrave" to "À", "Acirc" to "Â", "Ccedil" to "Ç", "Eacute" to "É", "Egrave" to "È",
    "Ecirc" to "Ê", "Icirc" to "Î", "Ocirc" to "Ô", "Ugrave" to "Ù", "Ucirc" to "Û",
)

/**
 * Nombre de mots d'un document XHTML d'EPUB.
 *
 * Retire `<head>`, `<script>`, `<style>`, commentaires, déclarations et balises (les balises de bloc
 * valent une espace, les balises en ligne rien), décode les entités courantes et numériques, retire
 * les césures conditionnelles, puis compte les suites de lettres ou chiffres. Une apostrophe (' ou ')
 * ou un trait d'union entre deux lettres ne coupe pas le mot : « l'homme », « peut-être » = 1 mot.
 */
fun countWordsInHtml(html: String): Long {
    var text = COMMENTS.replace(html, " ")
    text = HEAD.replace(text, " ")
    text = SCRIPT.replace(text, " ")
    text = STYLE.replace(text, " ")
    text = PROCESSING_INSTRUCTIONS.replace(text, " ")
    text = DECLARATIONS.replace(text, " ")
    text = TAG.replace(text) { match ->
        val name = match.groupValues[1].substringAfter(':').lowercase()
        if (name in INLINE_TAGS) "" else " "
    }
    text = ENTITY.replace(text) { match -> decodeEntity(match.groupValues[1]) }
    text = text.replace("­", "")
    return WORD.findAll(text).count().toLong()
}

private fun decodeEntity(body: String): String = when {
    body.startsWith("#x") || body.startsWith("#X") -> codePointToString(body.substring(2).toIntOrNull(16))
    body.startsWith("#") -> codePointToString(body.substring(1).toIntOrNull())
    else -> NAMED_ENTITIES[body] ?: ""
}

private fun codePointToString(codePoint: Int?): String =
    if (codePoint != null && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else ""

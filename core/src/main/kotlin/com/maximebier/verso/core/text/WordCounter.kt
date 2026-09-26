package com.maximebier.verso.core.text

/** Balises en ligne : retirées sans espace pour ne pas couper un mot (« ex<em>tra</em>ordinaire »). */
private val INLINE_TAGS = setOf(
    "a", "abbr", "b", "bdi", "bdo", "cite", "code", "data", "del", "dfn", "em", "font", "i", "ins",
    "kbd", "mark", "q", "rb", "rp", "rt", "ruby", "s", "samp", "small", "span", "strike", "strong",
    "sub", "sup", "time", "tt", "u", "var", "wbr",
)

/** Balises dont tout le contenu est ignoré (jamais lu comme du texte). */
private val SKIPPED_CONTENT_TAGS = setOf("head", "script", "style")

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
 * Machine à états qui reproduit `[\p{L}\p{N}\p{M}]+(?:[joiner][\p{L}\p{N}\p{M}]+)*` sans regex, en une
 * seule passe caractère par caractère (voir [countWordsInHtml]).
 *
 * - [NONE] : aucun mot en cours.
 * - [IN_WORD] : au milieu d'un mot.
 * - [AFTER_JOINER] : le mot vient de rencontrer une apostrophe ou un tiret ; si le caractère suivant
 *   est un caractère de mot, il se rattache au même mot ([IN_WORD]) ; sinon, ce tiret/apostrophe ne
 *   faisait pas partie d'un mot (le mot déjà en cours est compté, ce nouveau caractère repart de zéro).
 */
private class WordCounterState {
    private var state = NONE
    var count = 0L
        private set

    fun onCut() {
        if (state != NONE) count++
        state = NONE
    }

    fun onCodePoint(cp: Int) {
        when {
            isWordCodePoint(cp) -> state = IN_WORD
            isJoiner(cp) -> when (state) {
                IN_WORD -> state = AFTER_JOINER
                AFTER_JOINER -> {
                    count++
                    state = NONE
                }
                else -> Unit // NONE + joiner isolé : ne commence pas de mot
            }
            else -> onCut()
        }
    }

    fun finish(): Long {
        onCut()
        return count
    }

    private companion object {
        const val NONE = 0
        const val IN_WORD = 1
        const val AFTER_JOINER = 2
    }
}

/**
 * Nombre de mots d'un document XHTML d'EPUB.
 *
 * Ignore `<head>`, `<script>`, `<style>`, les commentaires, déclarations et balises (les balises de
 * bloc coupent un mot, les balises en ligne non), décode les entités courantes et numériques, retire
 * les césures conditionnelles, puis compte les suites de lettres ou chiffres. Une apostrophe (' ou ')
 * ou un trait d'union entre deux lettres ne coupe pas le mot : « l'homme », « peut-être » = 1 mot.
 *
 * **Aucune regex** : une seule passe caractère par caractère sur le texte source (voir
 * [WordCounterState]), sans construire de grande chaîne intermédiaire. Les regex `DOTALL`/`\p{…}`
 * d'origine — même réduites à une seule passe de nettoyage manuel suivie d'une seule regex de
 * comptage — restaient superlinéaires sur le moteur Java d'Android (ART) pour de gros documents :
 * mesuré sur l'appareil (`VersoImport` dans logcat), pas seulement sur le JDK de bureau où le
 * problème ne se voyait pas. Ce comptage n'appelle plus jamais `java.util.regex`.
 */
fun countWordsInHtml(html: String): Long {
    val n = html.length
    val counter = WordCounterState()
    val scanner = MarkupScanner(html)
    var i = 0
    while (i < n) {
        val c = html[i]
        i = when {
            c == '<' -> consumeMarkup(html, i, n, scanner, counter::onCut)
            c == '&' -> consumeEntity(html, i, n, counter::onCodePoint)
            c == '­' -> i + 1 // césure conditionnelle : retirée, transparente
            else -> {
                val cp = html.codePointAt(i)
                counter.onCodePoint(cp)
                i + Character.charCount(cp)
            }
        }
    }
    return counter.finish()
}

/**
 * Mémorise, pour chaque terminateur de balise (`-->`, `?>`, `>`), la position au-delà de laquelle on
 * sait déjà qu'il n'existe plus dans le document : évite de rescanner en vain jusqu'à la fin du texte
 * à chaque nouveau `<` non fermé (ex. une ressource tronquée, ou `a<b<c<d…` sans espaces), ce qui
 * ferait dégénérer [consumeMarkup] en O(n²). `indexOf` est monotone : si le terminateur est absent à
 * partir d'une position, il l'est aussi à partir de toute position ultérieure — donc, une fois la
 * recherche à vide constatée une fois, toutes les recherches suivantes à partir d'une position égale
 * ou plus loin peuvent répondre « absent » sans rescanner.
 */
private class MarkupScanner(private val html: String) {
    private var noCommentCloseFrom = Int.MAX_VALUE
    private var noPiCloseFrom = Int.MAX_VALUE
    private var noGtFrom = Int.MAX_VALUE

    fun findCommentClose(from: Int): Int {
        if (from >= noCommentCloseFrom) return -1
        val idx = html.indexOf("-->", from)
        if (idx == -1) noCommentCloseFrom = from
        return idx
    }

    fun findPiClose(from: Int): Int {
        if (from >= noPiCloseFrom) return -1
        val idx = html.indexOf("?>", from)
        if (idx == -1) noPiCloseFrom = from
        return idx
    }

    fun findGt(from: Int): Int {
        if (from >= noGtFrom) return -1
        val idx = html.indexOf('>', from)
        if (idx == -1) noGtFrom = from
        return idx
    }
}

/** Lit une balise, un commentaire, une instruction de traitement ou une déclaration à partir de
 * `html[start]` (`html[start] == '<'`) et renvoie l'index suivant. Appelle [onCut] quand ce contenu
 * doit couper un mot en cours (balise de bloc, section ignorée) ; ne l'appelle pas pour une balise en
 * ligne (le texte avant/après se retrouve directement concaténé, sans coupure). Les recherches de
 * terminateur passent par [scanner] pour rester O(n) même sur une entrée malformée (voir
 * [MarkupScanner]). */
private fun consumeMarkup(html: String, start: Int, n: Int, scanner: MarkupScanner, onCut: () -> Unit): Int {
    when {
        html.startsWith("<!--", start) -> {
            val end = scanner.findCommentClose(start + 4)
            if (end == -1) return start + 1 // pas de fermeture : '<' littéral, comme le ferait la regex
            onCut()
            return end + 3
        }
        html.startsWith("<?", start) -> {
            val end = scanner.findPiClose(start + 2)
            if (end == -1) return start + 1
            onCut()
            return end + 2
        }
        html.startsWith("<!", start) -> {
            val end = scanner.findGt(start + 2)
            if (end == -1) return start + 1
            onCut()
            return end + 1
        }
        else -> {
            var j = start + 1
            if (j < n && html[j] == '/') j++
            val nameStart = j
            if (j >= n || !html[j].isAsciiLetter()) return start + 1 // pas une balise reconnue par la regex d'origine
            j++
            while (j < n && (html[j].isAsciiLetterOrDigit() || html[j] == ':' || html[j] == '_' || html[j] == '-')) j++
            val name = html.substring(nameStart, j).substringAfter(':').lowercase()
            val close = scanner.findGt(j)
            if (close == -1) return start + 1
            return when {
                name in SKIPPED_CONTENT_TAGS -> {
                    onCut()
                    skipContentTag(html, close + 1, name)
                }
                name in INLINE_TAGS -> close + 1 // transparent : pas de coupure
                else -> {
                    onCut()
                    close + 1
                }
            }
        }
    }
}

/** À partir de la position juste après la balise ouvrante `<head|script|style ...>`, saute jusqu'à
 * la fermeture correspondante (insensible à la casse, espace éventuel avant `>`), ou jusqu'à la fin
 * du document si elle est absente. */
private fun skipContentTag(html: String, from: Int, name: String): Int {
    val n = html.length
    var i = from
    while (i < n) {
        if (html[i] == '<' && i + 1 < n && html[i + 1] == '/' &&
            html.regionMatches(i + 2, name, 0, name.length, ignoreCase = true)
        ) {
            var j = i + 2 + name.length
            while (j < n && html[j].isHtmlSpace()) j++
            if (j < n && html[j] == '>') return j + 1
        }
        i++
    }
    return n
}

/** Lit une entité (nommée ou numérique) à partir de `html[start]` (`html[start] == '&'`) et renvoie
 * l'index suivant. Appelle [onCodePoint] pour chaque point de code décodé ; une entité vide (`&shy;`,
 * entité inconnue) n'appelle rien, ce qui recolle les mots de part et d'autre. */
private fun consumeEntity(html: String, start: Int, n: Int, onCodePoint: (Int) -> Unit): Int {
    var j = start + 1
    if (j < n && html[j] == '#') {
        var k = j + 1
        val hex = k < n && (html[k] == 'x' || html[k] == 'X')
        if (hex) k++
        val digitsStart = k
        while (k < n && (if (hex) html[k].isAsciiHexDigit() else html[k].isAsciiDigit())) k++
        if (k > digitsStart && k < n && html[k] == ';') {
            decodeEntity(html.substring(j, k)).codePoints().forEach(onCodePoint)
            return k + 1
        }
        return start + 1
    }
    if (j < n && html[j].isAsciiLetter()) {
        var k = j + 1
        while (k < n && html[k].isAsciiLetterOrDigit()) k++
        if (k < n && html[k] == ';') {
            decodeEntity(html.substring(j, k)).codePoints().forEach(onCodePoint)
            return k + 1
        }
        return start + 1
    }
    return start + 1
}

private fun decodeEntity(body: String): String = when {
    body.startsWith("#x") || body.startsWith("#X") -> codePointToString(body.substring(2).toIntOrNull(16))
    body.startsWith("#") -> codePointToString(body.substring(1).toIntOrNull())
    else -> NAMED_ENTITIES[body] ?: ""
}

private fun codePointToString(codePoint: Int?): String =
    if (codePoint != null && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else ""

/** Équivalent de `\p{L}\p{N}\p{M}` : lettre, chiffre (au sens large) ou signe combinant. */
private fun isWordCodePoint(cp: Int): Boolean = when (Character.getType(cp)) {
    Character.UPPERCASE_LETTER.toInt(), Character.LOWERCASE_LETTER.toInt(), Character.TITLECASE_LETTER.toInt(),
    Character.MODIFIER_LETTER.toInt(), Character.OTHER_LETTER.toInt(),
    Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt(),
    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(),
    -> true
    else -> false
}

/** Apostrophe droite et tirets qui ne coupent pas un mot entre deux lettres/chiffres. */
private fun isJoiner(cp: Int): Boolean = cp == '\''.code || cp == '‐'.code || cp == '‑'.code || cp == '-'.code

private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'
private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
private fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || isAsciiDigit()
private fun Char.isAsciiHexDigit(): Boolean = isAsciiDigit() || this in 'a'..'f' || this in 'A'..'F'
private fun Char.isHtmlSpace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\u000B' || this == '\u000C' || this == '\r'

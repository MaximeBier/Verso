package com.maximebier.verso.core.text

import java.text.Normalizer
import java.util.Locale

private const val MAX_LEVELS = 2

/**
 * Chemin de titres (partie, chapitre) de la position (`href` sans fragment, `progression` dans ce fichier),
 * du plus haut au plus bas, 2 niveaux max.
 *
 * Les href sont comparés sans fragment ni `/` initial. Parmi les entrées du fichier, on garde celles déjà
 * atteintes ([isReachedAt]) et, parmi elles, celle qui commence le plus loin : plusieurs chapitres ancrés dans
 * un même fichier sont ainsi départagés par la progression. À égalité (entrées sans ancre, ou partie et premier
 * chapitre sur la même ancre), le premier l'emporte dans l'ordre du sommaire, un descendant l'emportant sur son
 * parent. Avant la première ancre du fichier, c'est l'entrée qui précède dans le sommaire (fin du chapitre
 * précédent, fichiers découpés par taille) ; aucune avant → liste vide. Au-delà de 2 niveaux, on garde les deux
 * plus profonds (Tome > Partie > Chapitre → Partie, Chapitre). Titres vides ignorés. Sommaire vide ou href
 * absent → liste vide.
 */
fun chapterPathAt(toc: List<TocNode>, href: String, progression: Double? = null): List<String> {
    val target = normalizeHref(href)
    if (target.isEmpty()) return emptyList()
    val entries = flattenWithPaths(toc)
    val inFile = entries.filter { normalizeHref(it.node.href) == target }
    if (inFile.isEmpty()) return emptyList()
    // Postordre : à égalité, un descendant passe avant son parent.
    val chosen = currentInFile(inFile.sortedBy { it.postorder }, progression) { it.node }
        ?: entries.getOrNull(inFile.first().preorder - 1)
        ?: return emptyList()
    return cleanPath(chosen.path)
}

private class TocEntry(val node: TocNode, val path: List<String>, val preorder: Int, var postorder: Int = 0)

/** Entrées dans l’ordre du sommaire ([preorder]), avec leur chemin de titres et leur rang en postordre. */
private fun flattenWithPaths(toc: List<TocNode>): List<TocEntry> {
    val result = mutableListOf<TocEntry>()
    var post = 0
    fun visit(nodes: List<TocNode>, parents: List<String>) {
        nodes.forEach { node ->
            val entry = TocEntry(node, parents + node.title, result.size)
            result += entry
            visit(node.children, entry.path)
            entry.postorder = post++
        }
    }
    visit(toc, emptyList())
    return result
}

/**
 * Gabarits des emplacements, fournis par l’app (textes dans `strings.xml`) : [part] « Partie II », [chapter]
 * « chap. I », [chapterOnly] « Chap. IV », [join] « Partie II, chap. I », [passage] « A → B ».
 */
class LocationTexts(
    val part: (String) -> String,
    val chapter: (String) -> String,
    val chapterOnly: (String) -> String,
    val join: (String, String) -> String,
    val passage: (String, String) -> String,
)

/**
 * « Deuxième partie, Chapitre I » → forme courte affichée ; chemin vide → null.
 *
 * Forme courte bâtie avec [LocationTexts] (« Partie II, chap. I », « Chap. IV »). La partie est reconnue dans
 * « Deuxième partie », « PREMIÈRE PARTIE », « Partie II », « Partie 3 » ; le chapitre dans « Chapitre I »,
 * « CHAPITRE XII. La noce », « Chapitre premier », « I », « 12. » (chiffres romains en capitales ou chiffres
 * arabes). Sinon le titre brut est gardé : « Préface », « Livre premier, chap. II », « Partie II, Le retour ».
 */
fun shortLocation(path: List<String>, texts: LocationTexts): String? {
    val levels = cleanPath(path)
    if (levels.isEmpty()) return null
    val chapter = levels.last()
    val chapterNumber = chapterNumber(chapter)
    if (levels.size == 1) return if (chapterNumber != null) texts.chapterOnly(chapterNumber) else chapter
    val part = levels.first()
    val partLabel = partNumber(part)?.let(texts.part) ?: part
    val chapterLabel = chapterNumber?.let(texts.chapter) ?: chapter
    return texts.join(partLabel, chapterLabel)
}

/**
 * Passage d'une session : « A → B », compacté si même parent (« Partie I, chap. VI → VIII »).
 *
 * Compactage seulement si les deux chemins ont le même parent et deux chapitres numérotés ; même
 * emplacement au début et à la fin → un seul emplacement ; un côté vide → l'autre ; les deux vides → null.
 * La flèche est entourée d'espaces normales, comme `journal_passage`.
 */
fun passageLabel(start: List<String>, end: List<String>, texts: LocationTexts): String? {
    val from = shortLocation(start, texts)
    val to = shortLocation(end, texts)
    if (from == null) return to
    if (to == null) return from
    if (from == to) return from
    val startLevels = cleanPath(start)
    val endLevels = cleanPath(end)
    val sameParent = startLevels.size == endLevels.size && startLevels.dropLast(1) == endLevels.dropLast(1)
    val startNumber = chapterNumber(startLevels.last())
    val endNumber = chapterNumber(endLevels.last())
    val compactEnd = endNumber?.takeIf { sameParent && startNumber != null }
    return texts.passage(from, compactEnd ?: to)
}

private fun normalizeHref(href: String): String = href.substringBefore('#').trim().removePrefix("/")

private fun cleanPath(path: List<String>): List<String> =
    path.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_LEVELS)

private val ROMAN = Regex("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")
private val CHAPTER_NUMBERED = Regex("^(?i:chapitre\\s+|chap\\.\\s*)([IVXLCDM]+|\\d+)(?![\\p{L}\\p{N}])")
private val CHAPTER_ORDINAL = Regex("^(?i:chapitre)\\s+([\\p{L}\\p{N}]+)(?![\\p{L}\\p{N}])")
private val BARE_NUMBER = Regex("^([IVXLCDM]+|\\d+)\\s*[.)]?$")
private val PART_NUMBERED = Regex("^(?i:partie)\\s+([IVXLCDM]+|\\d+)(?![\\p{L}\\p{N}])")
private val PART_ORDINAL_BEFORE = Regex("^([\\p{L}\\p{N}]+)\\s+(?i:partie)(?![\\p{L}])")
private val PART_ORDINAL_AFTER = Regex("^(?i:partie)\\s+([\\p{L}\\p{N}]+)(?![\\p{L}\\p{N}])")
private val MARKS = Regex("\\p{M}+")

/** Ordinaux français (minuscules, sans accents) → chiffres romains. */
private val ORDINALS = mapOf(
    "premier" to "I", "premiere" to "I", "1er" to "I", "1re" to "I", "ier" to "I", "ire" to "I",
    "deuxieme" to "II", "second" to "II", "seconde" to "II", "troisieme" to "III", "quatrieme" to "IV",
    "cinquieme" to "V", "sixieme" to "VI", "septieme" to "VII", "huitieme" to "VIII", "neuvieme" to "IX",
    "dixieme" to "X", "onzieme" to "XI", "douzieme" to "XII",
)

/** Numéro de chapitre (« IV », « 12 ») ou null. */
private fun chapterNumber(title: String): String? {
    val t = title.trim()
    CHAPTER_NUMBERED.find(t)?.let { m -> numberToken(m.groupValues[1])?.let { return it } }
    CHAPTER_ORDINAL.find(t)?.let { m -> ORDINALS[normalizeWord(m.groupValues[1])]?.let { return it } }
    BARE_NUMBER.find(t)?.let { m -> numberToken(m.groupValues[1])?.let { return it } }
    return null
}

/** Numéro de partie (« II », « 3 ») ou null. */
private fun partNumber(title: String): String? {
    val t = title.trim()
    PART_NUMBERED.find(t)?.let { m -> numberToken(m.groupValues[1])?.let { return it } }
    PART_ORDINAL_BEFORE.find(t)?.let { m -> ORDINALS[normalizeWord(m.groupValues[1])]?.let { return it } }
    PART_ORDINAL_AFTER.find(t)?.let { m -> ORDINALS[normalizeWord(m.groupValues[1])]?.let { return it } }
    return null
}

/** Chiffres arabes tels quels ; chiffres romains seulement s'ils sont bien formés. */
private fun numberToken(token: String): String? = when {
    token.all { it.isDigit() } -> token
    ROMAN.matches(token) -> token
    else -> null
}

private fun normalizeWord(word: String): String =
    MARKS.replace(Normalizer.normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

/**
 * Forme longue « Deuxième partie, chapitre I » (barre de lecture, carte « Reprendre ») ; chemin vide → null.
 * [join] applique le gabarit `common_location_long`. L’initiale du chapitre passe en minuscule seulement si le
 * titre commence par un mot (« Chapitre ») et non par un numéro (« IV »).
 */
fun longLocation(path: List<String>, join: (part: String, chapter: String) -> String): String? = when (path.size) {
    0 -> null
    1 -> path[0]
    else -> {
        val chapter = path[1]
        val startsWithWord = chapter.length > 1 && chapter[1].isLowerCase()
        join(path[0], if (startsWithWord) chapter.replaceFirstChar { it.lowercase(Locale.FRENCH) } else chapter)
    }
}

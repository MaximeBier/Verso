package com.maximebier.verso.core.text

import java.text.Normalizer
import java.util.Locale

/** Entrée du sommaire (titre, href Readium, enfants). */
data class TocNode(val title: String, val href: String, val children: List<TocNode> = emptyList())

private const val MAX_LEVELS = 2

/**
 * Chemin de titres (partie, chapitre) contenant `href` (sans fragment), du plus haut au plus bas, 2 niveaux max.
 *
 * Les href sont comparés sans fragment ni `/` initial. Parcours en profondeur dans l'ordre du sommaire :
 * la première entrée dont le sous-arbre contient `href` l'emporte, et un descendant l'emporte sur son
 * parent (partie et premier chapitre dans le même fichier). Plusieurs chapitres dans un seul fichier :
 * le premier l'emporte (le fragment est ignoré). Au-delà de 2 niveaux, on garde les deux plus profonds
 * (Tome > Partie > Chapitre → Partie, Chapitre). Titres vides ignorés. Sommaire vide ou href absent → liste vide.
 */
fun chapterPathAt(toc: List<TocNode>, href: String): List<String> {
    val target = normalizeHref(href)
    if (target.isEmpty()) return emptyList()
    val path = findPath(toc, target) ?: return emptyList()
    return path.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_LEVELS)
}

/**
 * « Deuxième partie, Chapitre I » → forme courte affichée ; chemin vide → null.
 *
 * Forme courte identique aux gabarits `common_location_short` (« Partie II, chap. I ») et
 * `common_location_short_no_part` (« Chap. IV »). La partie est reconnue dans « Deuxième partie »,
 * « PREMIÈRE PARTIE », « Partie II », « Partie 3 » ; le chapitre dans « Chapitre I », « CHAPITRE XII. La noce »,
 * « Chapitre premier », « I », « 12. » (chiffres romains en capitales ou chiffres arabes). Sinon le titre
 * brut est gardé : « Préface », « Livre premier, chap. II », « Partie II, Le retour ».
 */
fun shortLocation(path: List<String>): String? {
    val levels = cleanPath(path)
    if (levels.isEmpty()) return null
    val chapter = levels.last()
    val chapterNumber = chapterNumber(chapter)
    if (levels.size == 1) return if (chapterNumber != null) "Chap. $chapterNumber" else chapter
    val part = levels.first()
    val partLabel = partNumber(part)?.let { "Partie $it" } ?: part
    val chapterLabel = chapterNumber?.let { "chap. $it" } ?: chapter
    return "$partLabel, $chapterLabel"
}

/**
 * Passage d'une session : « A → B », compacté si même parent (« Partie I, chap. VI → VIII »).
 *
 * Compactage seulement si les deux chemins ont le même parent et deux chapitres numérotés ; même
 * emplacement au début et à la fin → un seul emplacement ; un côté vide → l'autre ; les deux vides → null.
 * La flèche est entourée d'espaces normales, comme `journal_passage`.
 */
fun passageLabel(start: List<String>, end: List<String>): String? {
    val from = shortLocation(start)
    val to = shortLocation(end)
    if (from == null) return to
    if (to == null) return from
    if (from == to) return from
    val startLevels = cleanPath(start)
    val endLevels = cleanPath(end)
    val sameParent = startLevels.size == endLevels.size && startLevels.dropLast(1) == endLevels.dropLast(1)
    val startNumber = chapterNumber(startLevels.last())
    val endNumber = chapterNumber(endLevels.last())
    return if (sameParent && startNumber != null && endNumber != null) "$from → $endNumber" else "$from → $to"
}

private fun findPath(nodes: List<TocNode>, target: String): List<String>? {
    for (node in nodes) {
        val deeper = findPath(node.children, target)
        if (deeper != null) return listOf(node.title) + deeper
        if (normalizeHref(node.href) == target) return listOf(node.title)
    }
    return null
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

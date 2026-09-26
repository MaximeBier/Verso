package com.maximebier.verso.core.text

/**
 * Entrée du sommaire (titre, href Readium sans fragment, enfants).
 *
 * `progression` : début de l'entrée dans son fichier (0..1) pour une entrée qui pointe vers une ancre
 * (`fichier.xhtml#id`), estimé par [anchorProgression] ou calibré par [calibrateAnchor] ; null = début du fichier
 * (entrée sans ancre, ou ancre introuvable). `fileChars` : longueur du texte du fichier, pour convertir la
 * tolérance [TocProgress.ANCHOR_TOLERANCE_CHARS] en fraction du fichier ; null = pas de tolérance.
 */
data class TocNode(
    val title: String,
    val href: String,
    val children: List<TocNode> = emptyList(),
    val progression: Double? = null,
    val fileChars: Int? = null,
)

/** Seuils du choix de l'entrée courante du sommaire. */
object TocProgress {
    /**
     * Écart toléré entre la position estimée d'une ancre (part du texte) et celle du moteur (mise en page), en
     * caractères : environ un écran et demi de texte (390 × 844 dp, 19 sp, interligne 1,6 : environ 35 caractères
     * × 26 lignes, soit 900 caractères par écran).
     * Une ancre précédée de moins de texte que cela est ramenée au début de son fichier.
     */
    const val ANCHOR_TOLERANCE_CHARS = 1_500

    /** Écart maximal accepté entre l'estimation d'une ancre et la position rapportée par le moteur (calibrage). */
    const val CALIBRATION_MAX_CHARS = 3 * ANCHOR_TOLERANCE_CHARS

    /** Après un saut vers une ancre, durée sans mouvement de la position affichée avant de la retenir (calibrage). */
    const val CALIBRATION_SETTLE_MS = 500L

    /** Au-delà de ce délai après le saut, plus de calibrage (le lecteur est sans doute reparti). */
    const val CALIBRATION_WINDOW_MS = 3_000L
}

/**
 * Début estimé d'une ancre dans son fichier : part du texte qui la précède. Moins de
 * [TocProgress.ANCHOR_TOLERANCE_CHARS] caractères avant elle (titre courant, filet) → début du fichier.
 */
fun anchorProgression(charsBefore: Int, fileChars: Int): Double = when {
    fileChars <= 0 || charsBefore < TocProgress.ANCHOR_TOLERANCE_CHARS -> 0.0
    else -> (charsBefore.toDouble() / fileChars).coerceIn(0.0, 1.0)
}

/** Tolérance de l'entrée en fraction de son fichier (0 si la longueur du fichier est inconnue). */
private fun TocNode.tolerance(): Double =
    fileChars?.takeIf { it > 0 }?.let { TocProgress.ANCHOR_TOLERANCE_CHARS.toDouble() / it } ?: 0.0

/**
 * L'entrée commence-t-elle à ou avant `progression` (progression dans son fichier, null = début) ?
 * Entrée sans ancre : début du fichier, toujours atteinte. Tolérance : [TocProgress.ANCHOR_TOLERANCE_CHARS].
 */
fun TocNode.isReachedAt(progression: Double?): Boolean {
    val start = this.progression ?: return true
    return (progression ?: 0.0) + tolerance() >= start
}

/**
 * Parmi `entries` (dans l'ordre du sommaire, toutes du fichier courant), l'entrée courante à `progression` :
 * celle, déjà atteinte, qui commence le plus loin ; à égalité, la première. Aucune atteinte → null.
 * Règle commune au sommaire (`buildTocRows`) et aux libellés ([chapterPathAt]).
 */
fun <T> currentInFile(entries: List<T>, progression: Double?, node: (T) -> TocNode): T? {
    val reached = entries.filter { node(it).isReachedAt(progression) }
    val furthest = reached.maxOfOrNull { node(it).progression ?: 0.0 } ?: return null
    return reached.first { (node(it).progression ?: 0.0) == furthest }
}

/**
 * Calibrage : remplace le début estimé de l'entrée n° [index] (préordre du sommaire) par la progression
 * [observed] que le moteur a rapportée après un saut vers elle. Les entrées du même fichier sur la même ancre
 * (partie et premier chapitre) suivent. Refusé (null) si l'entrée n'a pas d'ancre, si l'écart dépasse
 * [TocProgress.CALIBRATION_MAX_CHARS] ou si l'ordre des ancres du fichier changerait.
 */
fun calibrateAnchor(toc: List<TocNode>, index: Int, observed: Double): List<TocNode>? {
    val nodes = flattenNodes(toc)
    val target = nodes.getOrNull(index) ?: return null
    val estimate = target.progression ?: return null
    val fileChars = target.fileChars?.takeIf { it > 0 } ?: return null
    if (kotlin.math.abs(observed - estimate) * fileChars > TocProgress.CALIBRATION_MAX_CHARS) return null
    val others = nodes.filter { it.href == target.href }.mapNotNull { it.progression }.filter { it != estimate }
    val previous = others.filter { it < estimate }.maxOrNull()
    val next = others.filter { it > estimate }.minOrNull()
    if (previous != null && observed <= previous) return null
    if (next != null && observed >= next) return null
    fun calibrated(list: List<TocNode>): List<TocNode> = list.map { node ->
        val own = if (node.href == target.href && node.progression == estimate) observed else node.progression
        node.copy(progression = own, children = calibrated(node.children))
    }
    return calibrated(toc)
}

private fun flattenNodes(toc: List<TocNode>): List<TocNode> = toc.flatMap { listOf(it) + flattenNodes(it.children) }

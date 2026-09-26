package com.maximebier.verso.reader

/** Bords du chapitre affiché, lus au moment où le doigt se pose. */
data class ChapterEdges(val atTop: Boolean, val atBottom: Boolean)

enum class ChapterChain { NEXT, PREVIOUS, NONE }

/**
 * Changement de chapitre (spec) : un glissé **commencé** au bord bas vers le haut ouvre le chapitre suivant, un
 * glissé commencé au bord haut vers le bas ouvre le précédent. L’élan d’un fling qui atteint le bord ne compte
 * jamais : les bords sont ceux de l’appui. `dragDyPx` = lever − appui (négatif quand le doigt monte).
 */
fun chapterChain(edgesAtDown: ChapterEdges?, dragDyPx: Float, thresholdPx: Float): ChapterChain = when {
    edgesAtDown == null -> ChapterChain.NONE
    edgesAtDown.atBottom && dragDyPx <= -thresholdPx -> ChapterChain.NEXT
    edgesAtDown.atTop && dragDyPx >= thresholdPx -> ChapterChain.PREVIOUS
    else -> ChapterChain.NONE
}

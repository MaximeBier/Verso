package com.maximebier.verso.core.text

import kotlin.math.floor

/** Tolérance d'arrondi : une progression à un milliardième sous le début d'une page est déjà cette page. */
private const val PAGE_EPSILON = 1e-6

/**
 * Page courante, de 1 à [pageCount], à partir de la progression dans le chapitre (mode pages de Readium : début de
 * la page affichée / largeur totale du chapitre). NaN ou hors de 0..1 : bornée. Lève [IllegalArgumentException] si
 * `pageCount < 1`.
 */
fun pageInChapter(progression: Double, pageCount: Int): Int {
    require(pageCount >= 1) { "pageCount doit valoir au moins 1 : $pageCount" }
    val p = if (progression.isNaN()) 0.0 else progression.coerceIn(0.0, 1.0)
    return (floor(p * pageCount + PAGE_EPSILON).toInt() + 1).coerceIn(1, pageCount)
}

/** « Page 2 sur 9 » dans le chapitre : page (1..pageCount) et nombre de pages du chapitre. */
data class ChapterPage(val page: Int, val pageCount: Int)

/**
 * Page du chapitre quand un fichier en contient plusieurs (chapitres ancrés du sommaire) : [filePage] (1..
 * [filePageCount]) est la page dans le fichier, [chapterStarts] les pages (1-based) où commence chaque chapitre du
 * fichier, dans n’importe quel ordre. Le texte avant la première ancre forme sa propre section ; une ancre au-delà
 * du fichier est ignorée. Sans ancre, le fichier entier est le chapitre.
 */
fun chapterPage(filePage: Int, filePageCount: Int, chapterStarts: List<Int>): ChapterPage {
    require(filePageCount >= 1) { "filePageCount doit valoir au moins 1 : $filePageCount" }
    val page = filePage.coerceIn(1, filePageCount)
    val boundaries = (chapterStarts.filter { it <= filePageCount }.map { it.coerceAtLeast(1) } + 1).distinct().sorted()
    val start = boundaries.last { it <= page }
    val next = boundaries.firstOrNull { it > page } ?: (filePageCount + 1)
    return ChapterPage(page - start + 1, next - start)
}

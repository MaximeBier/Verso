package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageLabelTest {

    @Test
    fun progressionIsTurnedIntoAOneBasedPage() {
        // Readium, en pages : progression = début de la page / largeur du chapitre.
        assertThat(pageInChapter(0.0, 9)).isEqualTo(1)
        assertThat(pageInChapter(1.0 / 9, 9)).isEqualTo(2)
        assertThat(pageInChapter(8.0 / 9, 9)).isEqualTo(9)
    }

    @Test
    fun roundingJustBelowAPageBoundaryStillGivesThatPage() {
        assertThat(pageInChapter(2.0 / 9 - 1e-9, 9)).isEqualTo(3)
    }

    @Test
    fun endOfChapterAndOutOfRangeValuesAreClamped() {
        assertThat(pageInChapter(1.0, 9)).isEqualTo(9)
        assertThat(pageInChapter(1.5, 9)).isEqualTo(9)
        assertThat(pageInChapter(-0.2, 9)).isEqualTo(1)
        assertThat(pageInChapter(Double.NaN, 9)).isEqualTo(1)
    }

    @Test
    fun singlePageChapterIsPageOneOfOne() {
        assertThat(pageInChapter(0.7, 1)).isEqualTo(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageCountMustBePositive() {
        pageInChapter(0.5, 0)
    }

    // ---------- Fichier qui contient plusieurs chapitres (ancres du sommaire) ----------

    @Test
    fun withoutChapterAnchorsTheWholeFileIsTheChapter() {
        assertThat(chapterPage(filePage = 3, filePageCount = 9, chapterStarts = emptyList())).isEqualTo(ChapterPage(3, 9))
    }

    @Test
    fun pagesAreCountedInsideTheChapterOfTheFile() {
        // Chapitres commençant aux pages 1, 5 et 12 d’un fichier de 20 pages.
        val starts = listOf(1, 5, 12)
        assertThat(chapterPage(1, 20, starts)).isEqualTo(ChapterPage(1, 4))
        assertThat(chapterPage(4, 20, starts)).isEqualTo(ChapterPage(4, 4))
        assertThat(chapterPage(5, 20, starts)).isEqualTo(ChapterPage(1, 7))
        assertThat(chapterPage(11, 20, starts)).isEqualTo(ChapterPage(7, 7))
        assertThat(chapterPage(12, 20, starts)).isEqualTo(ChapterPage(1, 9))
        assertThat(chapterPage(20, 20, starts)).isEqualTo(ChapterPage(9, 9))
    }

    @Test
    fun textBeforeTheFirstAnchorIsItsOwnSection() {
        assertThat(chapterPage(2, 10, listOf(4))).isEqualTo(ChapterPage(2, 3))
        assertThat(chapterPage(4, 10, listOf(4))).isEqualTo(ChapterPage(1, 7))
    }

    @Test
    fun twoChaptersOnTheSamePageAndOutOfRangeAnchorsAreTolerated() {
        // Chapitre d’une seule page : « Page 1 sur 1 » ; ancres en désordre, en double ou hors du fichier.
        val starts = listOf(7, 3, 3, 4, 0, 99)
        assertThat(chapterPage(3, 8, starts)).isEqualTo(ChapterPage(1, 1))
        assertThat(chapterPage(5, 8, starts)).isEqualTo(ChapterPage(2, 3))
        assertThat(chapterPage(8, 8, starts)).isEqualTo(ChapterPage(2, 2))
    }
}

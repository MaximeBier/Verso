package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LabelsTest {

    /** Sommaire façon Gutenberg de Madame Bovary : parties sans numéro, chapitres « I », « II » ou « Chapitre I ». */
    private val bovary = listOf(
        TocNode(
            "Première partie", "Text/part1.xhtml",
            listOf(TocNode("I", "Text/ch01.xhtml"), TocNode("II", "Text/ch02.xhtml")),
        ),
        TocNode(
            "Deuxième partie", "Text/part2.xhtml",
            listOf(TocNode("Chapitre I", "Text/ch10.xhtml#start"), TocNode("Chapitre II", "Text/ch11.xhtml")),
        ),
    )

    private val flat = listOf(TocNode("Chapitre I", "c1.xhtml"), TocNode("Chapitre II", "c2.xhtml"))

    // ---------- chapterPathAt ----------

    @Test
    fun emptyTocGivesEmptyPath() {
        assertThat(chapterPathAt(emptyList(), "Text/ch01.xhtml")).isEmpty()
    }

    @Test
    fun flatTocGivesChapterOnly() {
        assertThat(chapterPathAt(flat, "c2.xhtml")).containsExactly("Chapitre II")
    }

    @Test
    fun nestedTocGivesPartThenChapterIgnoringFragments() {
        assertThat(chapterPathAt(bovary, "Text/ch10.xhtml#p12")).containsExactly("Deuxième partie", "Chapitre I").inOrder()
        assertThat(chapterPathAt(bovary, "Text/ch02.xhtml")).containsExactly("Première partie", "II").inOrder()
    }

    @Test
    fun leadingSlashIsIgnored() {
        assertThat(chapterPathAt(bovary, "/Text/ch02.xhtml")).containsExactly("Première partie", "II").inOrder()
    }

    @Test
    fun partPageItselfGivesPartOnly() {
        assertThat(chapterPathAt(bovary, "Text/part2.xhtml")).containsExactly("Deuxième partie")
    }

    @Test
    fun unknownOrBlankHrefGivesEmptyPath() {
        assertThat(chapterPathAt(bovary, "Text/notes.xhtml")).isEmpty()
        assertThat(chapterPathAt(bovary, "")).isEmpty()
        assertThat(chapterPathAt(bovary, "#frag")).isEmpty()
    }

    @Test
    fun deeperTocKeepsTheTwoDeepestLevels() {
        val toc = listOf(
            TocNode("Tome I", "t1.xhtml", listOf(TocNode("Première partie", "p1.xhtml", listOf(TocNode("Chapitre III", "c3.xhtml"))))),
        )
        assertThat(chapterPathAt(toc, "c3.xhtml")).containsExactly("Première partie", "Chapitre III").inOrder()
    }

    @Test
    fun deepestEntryWinsAndFirstEntryOfTheFileWinsSinceFragmentsAreIgnored() {
        val toc = listOf(
            TocNode("Première partie", "book.xhtml#p1", listOf(TocNode("Chapitre I", "book.xhtml#c1"), TocNode("Chapitre II", "book.xhtml#c2"))),
        )
        assertThat(chapterPathAt(toc, "book.xhtml#c2")).containsExactly("Première partie", "Chapitre I").inOrder()
    }

    @Test
    fun blankTitlesAreSkipped() {
        val toc = listOf(TocNode("  ", "p.xhtml", listOf(TocNode("Chapitre V", "c5.xhtml"))))
        assertThat(chapterPathAt(toc, "c5.xhtml")).containsExactly("Chapitre V")
    }

    // ---------- shortLocation ----------

    @Test
    fun emptyPathHasNoLocation() {
        assertThat(shortLocation(emptyList())).isNull()
        assertThat(shortLocation(listOf("  "))).isNull()
    }

    @Test
    fun partAndChapterGiveShortForm() {
        assertThat(shortLocation(listOf("Deuxième partie", "Chapitre I"))).isEqualTo("Partie II, chap. I")
        assertThat(shortLocation(listOf("PREMIÈRE PARTIE", "I"))).isEqualTo("Partie I, chap. I")
        assertThat(shortLocation(listOf("Partie 3", "Chapitre 12"))).isEqualTo("Partie 3, chap. 12")
        assertThat(shortLocation(listOf("Partie II", "CHAPITRE XII. La noce"))).isEqualTo("Partie II, chap. XII")
    }

    @Test
    fun chapterWithoutPartGivesChapForm() {
        assertThat(shortLocation(listOf("Chapitre IV"))).isEqualTo("Chap. IV")
        assertThat(shortLocation(listOf("Chapitre premier"))).isEqualTo("Chap. I")
        assertThat(shortLocation(listOf("XII."))).isEqualTo("Chap. XII")
    }

    @Test
    fun unrecognisedTitlesAreKeptAsIs() {
        assertThat(shortLocation(listOf("Préface"))).isEqualTo("Préface")
        assertThat(shortLocation(listOf("Chapitre dix"))).isEqualTo("Chapitre dix")
        assertThat(shortLocation(listOf("Livre premier", "Chapitre II"))).isEqualTo("Livre premier, chap. II")
        assertThat(shortLocation(listOf("Deuxième partie", "Le retour"))).isEqualTo("Partie II, Le retour")
    }

    @Test
    fun tocToShortLocationEndToEnd() {
        assertThat(shortLocation(chapterPathAt(bovary, "Text/ch10.xhtml"))).isEqualTo("Partie II, chap. I")
        assertThat(shortLocation(chapterPathAt(emptyList(), "Text/ch10.xhtml"))).isNull()
    }

    // ---------- passageLabel ----------

    @Test
    fun samePartIsCompacted() {
        assertThat(passageLabel(listOf("Première partie", "Chapitre VI"), listOf("Première partie", "Chapitre VIII")))
            .isEqualTo("Partie I, chap. VI → VIII")
    }

    @Test
    fun differentPartsAreWrittenInFull() {
        assertThat(passageLabel(listOf("Première partie", "Chapitre VIII"), listOf("Deuxième partie", "Chapitre I")))
            .isEqualTo("Partie I, chap. VIII → Partie II, chap. I")
    }

    @Test
    fun flatNumberedChaptersAreCompacted() {
        assertThat(passageLabel(listOf("Chapitre III"), listOf("Chapitre V"))).isEqualTo("Chap. III → V")
    }

    @Test
    fun unnumberedChaptersAreNotCompacted() {
        assertThat(passageLabel(listOf("Préface"), listOf("Chapitre I"))).isEqualTo("Préface → Chap. I")
    }

    @Test
    fun sameChapterGivesSingleLocation() {
        assertThat(passageLabel(listOf("Première partie", "Chapitre VI"), listOf("Première partie", "Chapitre VI")))
            .isEqualTo("Partie I, chap. VI")
    }

    @Test
    fun emptyPathsGiveNullOrTheOtherSide() {
        assertThat(passageLabel(emptyList(), emptyList())).isNull()
        assertThat(passageLabel(emptyList(), listOf("Chapitre II"))).isEqualTo("Chap. II")
        assertThat(passageLabel(listOf("Chapitre II"), emptyList())).isEqualTo("Chap. II")
    }
}

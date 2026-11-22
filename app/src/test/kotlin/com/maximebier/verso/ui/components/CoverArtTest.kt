package com.maximebier.verso.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoverArtTest {

    @Test
    fun twoLettersFromTwoWords() {
        assertThat(CoverArt.monogram("Madame Bovary", letters = 2)).isEqualTo("MB")
    }

    @Test
    fun oneLetter() {
        assertThat(CoverArt.monogram("Madame Bovary", letters = 1)).isEqualTo("M")
    }

    @Test
    fun elidedArticleIsIgnored() {
        assertThat(CoverArt.monogram("L’Assommoir", letters = 1)).isEqualTo("A")
        assertThat(CoverArt.monogram("L'Assommoir", letters = 1)).isEqualTo("A")
    }

    @Test
    fun leadingArticlesAreIgnored() {
        assertThat(CoverArt.monogram("Les Misérables", letters = 2)).isEqualTo("M")
        assertThat(CoverArt.monogram("Une vie", letters = 1)).isEqualTo("V")
    }

    @Test
    fun deAndDuAreIgnoredLikeArticles() {
        assertThat(CoverArt.monogram("Le Comte de Monte-Cristo", letters = 2)).isEqualTo("CM")
        assertThat(CoverArt.monogram("Le Vicomte de Bragelonne", letters = 2)).isEqualTo("VB")
        assertThat(CoverArt.monogram("Le Rouge et le Noir", letters = 2)).isEqualTo("RE")
        assertThat(CoverArt.monogram("Le Côté de Guermantes", letters = 2)).isEqualTo("CG")
        assertThat(CoverArt.monogram("Le Tour du monde en quatre-vingts jours", letters = 2)).isEqualTo("TM")
        assertThat(CoverArt.monogram("Les Trois Mousquetaires", letters = 1)).isEqualTo("T")
    }

    @Test
    fun elidedDeIsIgnored() {
        assertThat(CoverArt.monogram("Mémoires d’outre-tombe", letters = 2)).isEqualTo("MO")
        assertThat(CoverArt.monogram("Contes d'Andersen", letters = 2)).isEqualTo("CA")
    }

    @Test
    fun accentedInitialIsKeptAndUppercased() {
        assertThat(CoverArt.monogram("émaux et camées", letters = 2)).isEqualTo("ÉE")
    }

    @Test
    fun titleMadeOnlyOfAnArticleKeepsIt() {
        assertThat(CoverArt.monogram("Les", letters = 1)).isEqualTo("L")
    }

    @Test
    fun blankTitleGivesEmptyMonogram() {
        assertThat(CoverArt.monogram("   ", letters = 2)).isEmpty()
    }

    @Test
    fun colorIndexIsAStableCrc32() {
        // CRC32("abc") = 891568578 ; 891568578 mod 6 = 0. CRC32("a1b2c3") mod 6 = 2.
        assertThat(CoverArt.colorIndex("abc", paletteSize = 6)).isEqualTo(0)
        assertThat(CoverArt.colorIndex("a1b2c3", paletteSize = 6)).isEqualTo(2)
    }
}

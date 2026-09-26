package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WordCounterTest {

    @Test
    fun countsWordsOfPlainParagraph() {
        assertThat(countWordsInHtml("<p>Bonjour le monde</p>")).isEqualTo(3)
    }

    @Test
    fun blockTagsAndLineBreaksSeparateWords() {
        assertThat(countWordsInHtml("<p>un</p><p>deux</p>")).isEqualTo(2)
        assertThat(countWordsInHtml("un<br/>deux")).isEqualTo(2)
        assertThat(countWordsInHtml("<P>Un</P><DIV>deux</DIV>")).isEqualTo(2)
    }

    @Test
    fun inlineTagsDoNotSplitWords() {
        assertThat(countWordsInHtml("<p>ex<em>tra</em>ordinaire</p>")).isEqualTo(1)
    }

    @Test
    fun headScriptStyleAndCommentsAreIgnored() {
        val html = """<?xml version="1.0" encoding="utf-8"?><!DOCTYPE html>
            <html><head><title>Titre du livre</title><style>p { color: red; }</style></head>
            <body><!-- note de l'éditeur --><p>Un deux</p><script>var x = 1;</script></body></html>"""
        assertThat(countWordsInHtml(html)).isEqualTo(2)
    }

    @Test
    fun apostrophesKeepOneWord() {
        assertThat(countWordsInHtml("<p>L'homme qu'il aime</p>")).isEqualTo(3)
    }

    @Test
    fun hyphenatedWordsCountAsOne() {
        assertThat(countWordsInHtml("peut-être, arc-en-ciel")).isEqualTo(2)
    }

    @Test
    fun dialogueDashAndPunctuationAreNotWords() {
        assertThat(countWordsInHtml("<p>— Oui, dit-il.</p>")).isEqualTo(2)
        assertThat(countWordsInHtml("En 1857, 31 %")).isEqualTo(3)
    }

    @Test
    fun commonEntitiesAreDecoded() {
        assertThat(countWordsInHtml("Rock&nbsp;&amp;&nbsp;roll")).isEqualTo(2)
        assertThat(countWordsInHtml("l&rsquo;homme")).isEqualTo(1)
        assertThat(countWordsInHtml("&#233;t&#xE9;")).isEqualTo(1)
        assertThat(countWordsInHtml("&laquo;&#160;Oui&#160;&raquo;")).isEqualTo(1)
        assertThat(countWordsInHtml("extra&shy;ordinaire")).isEqualTo(1)
    }

    @Test
    fun emptyOrImageOnlyHasNoWords() {
        assertThat(countWordsInHtml("")).isEqualTo(0)
        assertThat(countWordsInHtml("<body><img src=\"cover.png\"/></body>")).isEqualTo(0)
    }

    @Test
    fun countsAcrossParagraphs() {
        assertThat(countWordsInHtml("<p>Un deux trois.</p>\n<p>Quatre cinq.</p>")).isEqualTo(5)
    }

    @Test
    fun consecutiveJoinersDoNotAttach() {
        // Un tiret (ou une apostrophe) qui n'est pas immédiatement suivi d'une lettre ne rattache pas :
        // "a--b" est deux mots, pas un seul "a--b".
        assertThat(countWordsInHtml("a--b")).isEqualTo(2)
        assertThat(countWordsInHtml("a''b")).isEqualTo(2)
        assertThat(countWordsInHtml("mot-")).isEqualTo(1)
        assertThat(countWordsInHtml("-mot")).isEqualTo(1)
    }

    @Test
    fun unterminatedMarkupFallsBackToLiteralText() {
        // Une balise, un commentaire ou une entité mal formés (pas de fermeture) ne sont pas retirés :
        // le texte reste littéral, comme le ferait la regex d'origine (elle échoue simplement à matcher).
        assertThat(countWordsInHtml("un <em texte sans fermeture")).isGreaterThan(0)
        assertThat(countWordsInHtml("avant <!-- jamais fermé")).isGreaterThan(0)
        assertThat(countWordsInHtml("1 &amp non-entité")).isEqualTo(3)
    }

    /**
     * Doubler la taille du document ne doit pas faire plus que doubler (à une bonne marge près) le
     * temps de comptage : garde-fou contre une régression en O(n²) (concaténation de chaînes en
     * boucle, relecture répétée, retour en arrière catastrophique d'une regex...).
     */
    @Test
    fun timeStaysLinearAsDocumentGrows() {
        val paragraph = "<p>Le rapide renard brun saute par-dessus le chien paresseux, dit-elle, " +
            "avec l'accent de circonstance&nbsp;: « Voilà&nbsp;! ».</p>\n"
        val small = paragraph.repeat(2_000)
        val large = paragraph.repeat(16_000) // 8x plus grand

        fun timeOf(html: String): Long {
            // Chauffe la JIT avant de mesurer, pour ne pas comparer du code interprété à du code compilé.
            repeat(3) { countWordsInHtml(html) }
            val start = System.nanoTime()
            countWordsInHtml(html)
            return System.nanoTime() - start
        }

        val smallNanos = timeOf(small)
        val largeNanos = timeOf(large)

        // 8x la taille -> au plus ~20x le temps (marge généreuse pour l'aléa de la JVM/CI) ; une
        // vraie régression O(n²) donnerait ~64x.
        assertThat(largeNanos).isLessThan(smallNanos * 20)
    }
}

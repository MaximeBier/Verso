package com.maximebier.verso.readium

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TocAnchorsTest {

    private fun page(body: String) =
        """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Titre très long dans l’en-tête</title>
<style>p { margin: 0 }</style></head>
<body>$body</body></html>"""

    private val words = "mot ".repeat(250).trim() // 999 caractères

    @Test
    fun charactersBeforeEachElementAndInTheWholeFile() {
        val html = page("""<h2 id="c1">I</h2><p>$words</p><h2 id="c2">II</h2><p>$words</p><h2 id='c3'>III</h2><p>$words</p>""")

        val offsets = TocAnchors.offsets(html, listOf("c1", "c2", "c3"))

        assertThat(offsets.fileChars).isWithin(10).of(3 * 1_003)
        assertThat(offsets.charsBefore.getValue("c1")).isEqualTo(0)
        assertThat(offsets.charsBefore.getValue("c2")).isWithin(5).of(1_003)
        assertThat(offsets.charsBefore.getValue("c3")).isWithin(5).of(2 * 1_003)
    }

    @Test
    fun headAndMarkupDoNotCountAsText() {
        val html = page("""<div class="x"><p><span>$words</span></p></div><p id="end" class="fin">$words</p>""")

        val offsets = TocAnchors.offsets(html, listOf("end"))

        assertThat(offsets.charsBefore.getValue("end").toDouble() / offsets.fileChars).isWithin(0.01).of(0.5)
    }

    @Test
    fun missingIdOrIdOnlyInTextIsAbsent() {
        val html = page("""<p>id="ghost" n’est pas un attribut.</p><p data-id="c9">$words</p>""")

        assertThat(TocAnchors.offsets(html, listOf("ghost", "c9", "nowhere")).charsBefore).isEmpty()
    }

    @Test
    fun emptyTextGivesZero() {
        val offsets = TocAnchors.offsets(page("""<div id="a"></div>"""), listOf("a"))

        assertThat(offsets.fileChars).isEqualTo(0)
        assertThat(offsets.charsBefore).containsExactly("a", 0)
    }
}

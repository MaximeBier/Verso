package com.maximebier.verso.core.journal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NonReadingPartsTest {
    private val structure = "http://idpf.org/epub/vocab/structure/#"

    @Test
    fun declaredFrontAndBackMatterIsNotReading() {
        for (type in listOf("toc", "copyright-page", "index", "endnotes", "rearnotes", "footnotes", "notes", "bibliography", "colophon")) {
            assertThat(NonReadingParts.isNonReading(structure + type)).isTrue()
        }
        assertThat(NonReadingParts.isNonReading("copyright-page")).isTrue()
    }

    @Test
    fun textAndOtherLandmarksStayReading() {
        for (type in listOf("bodymatter", "cover", "chapter", "preface", "introduction", "titlepage", "")) {
            assertThat(NonReadingParts.isNonReading(structure + type)).isFalse()
        }
    }

    @Test
    fun hrefsOfNonReadingLandmarksLoseTheirFragment() {
        val hrefs = NonReadingParts.hrefs(
            listOf(
                "OPS/about.xhtml#debut" to listOf(structure + "copyright-page"),
                "OPS/c0.xhtml" to listOf(structure + "bodymatter"),
                "OPS/index.xhtml" to listOf(structure + "index"),
            ),
        )
        assertThat(hrefs).containsExactly("OPS/about.xhtml", "OPS/index.xhtml")
    }
}

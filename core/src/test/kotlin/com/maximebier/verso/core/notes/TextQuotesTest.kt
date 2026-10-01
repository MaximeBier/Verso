package com.maximebier.verso.core.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextQuotesTest {
    private val chapter = TextQuotes.normalize(
        "L’eau qui court au bord de l’herbe sépare d’une raie blanche la couleur des prés, et la campagne ainsi " +
            "ressemble à un grand manteau. Plus loin, la campagne ainsi ressemble à un tapis.",
    )

    @Test fun normalizeCollapsesAllWhitespaceAndTrims() {
        assertThat(TextQuotes.normalize("  un\n\tdeux  trois quatre  ")).isEqualTo("un deux trois quatre")
    }

    @Test fun collapseKeepsEdges() {
        assertThat(TextQuotes.collapse("\n un \n")).isEqualTo(" un ")
    }

    @Test fun locatesSingleOccurrence() {
        val span = TextQuotes.locate(chapter, TextQuote(highlight = "raie blanche"))!!
        assertThat(chapter.substring(span.start, span.end)).isEqualTo("raie blanche")
    }

    @Test fun highlightIsNormalizedBeforeSearch() {
        val span = TextQuotes.locate(chapter, TextQuote(highlight = " raie\nblanche "))!!
        assertThat(chapter.substring(span.start, span.end)).isEqualTo("raie blanche")
    }

    @Test fun contextPicksTheRightOccurrence() {
        val second = TextQuotes.locate(
            chapter,
            TextQuote(highlight = "la campagne ainsi ressemble", before = "Plus loin, ", after = " à un tapis"),
        )!!
        assertThat(second.start).isEqualTo(chapter.lastIndexOf("la campagne ainsi ressemble"))
        val first = TextQuotes.locate(
            chapter,
            TextQuote(highlight = "la campagne ainsi ressemble", before = "des prés, et ", after = " à un grand"),
        )!!
        assertThat(first.start).isEqualTo(chapter.indexOf("la campagne ainsi ressemble"))
    }

    @Test fun absentPassageIsNull() {
        assertThat(TextQuotes.locate(chapter, TextQuote(highlight = "Yonville"))).isNull()
        assertThat(TextQuotes.locate(chapter, TextQuote(highlight = "   "))).isNull()
    }

    @Test fun quoteAtKeepsContextWithinBounds() {
        val span = CharSpan(0, 5)
        val quote = TextQuotes.quoteAt(chapter, span)
        assertThat(quote.highlight).isEqualTo("L’eau")
        assertThat(quote.before).isEmpty()
        assertThat(quote.after).hasLength(TextQuotes.CONTEXT_CHARS)
    }

    @Test fun previewCutsOnAWordWithEllipsis() {
        val text = "la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert"
        assertThat(TextQuotes.preview(text)).isEqualTo("la campagne ainsi ressemble à un grand manteau…")
        assertThat(TextQuotes.preview("court")).isEqualTo("court")
    }
}


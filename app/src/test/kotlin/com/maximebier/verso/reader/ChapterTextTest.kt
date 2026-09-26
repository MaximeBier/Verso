package com.maximebier.verso.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterTextTest {

    private val html = """
        <html><head><title>Titre</title><style>p { color: red; }</style></head>
        <body><h1>I</h1><p>Emma&nbsp;descendit &amp; sourit.</p><p>Charles&#8217;s cheval.</p></body></html>
    """.trimIndent()

    @Test
    fun plainTextDropsHeadTagsAndDecodesEntities() {
        assertThat(ChapterText.plainText(html)).isEqualTo("I Emma descendit & sourit. Charles’s cheval.")
    }

    @Test
    fun excerptStartsAtNextWordAndIsBounded() {
        val text = "un deux trois quatre cinq six sept huit neuf dix"

        assertThat(ChapterText.excerptAt(text, progression = 0.0, maxChars = 13)).isEqualTo("un deux trois")
        assertThat(ChapterText.excerptAt(text, progression = 0.1, maxChars = 12)).isEqualTo("trois quatre")
        assertThat(ChapterText.excerptAt("", progression = 0.5, maxChars = 10)).isNull()
    }
}

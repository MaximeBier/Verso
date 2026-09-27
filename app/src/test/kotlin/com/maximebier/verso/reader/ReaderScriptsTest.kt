package com.maximebier.verso.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReaderScriptsTest {
    @Test
    fun pageLayoutIsReadAsEvaluateJavascriptReturnsIt() {
        // JSON.stringify renvoie une chaîne, que evaluateJavascript encode à son tour en JSON.
        assertThat(pageLayoutOf("\"[184,12,1,null,97]\"", listOf("a", "b", "c")))
            .isEqualTo(PageLayout(184, listOf(AnchorPage("a", 1), AnchorPage("c", 97)), currentPage = 12))
        assertThat(pageLayoutOf("\"[9,null]\"")).isEqualTo(PageLayout(9))
        assertThat(pageLayoutOf("null")).isNull()
        assertThat(pageLayoutOf(null)).isNull()
        assertThat(pageLayoutOf("\"abc\"")).isNull()
        assertThat(pageLayoutOf("\"[]\"")).isNull()
        assertThat(pageLayoutOf("\"[0]\"")).isNull()
    }

    @Test
    fun pageLayoutScriptQuotesTheAnchorIds() {
        val script = pageLayoutScript(listOf("chap\"1", "c2"))
        assertThat(script).endsWith("""(["chap\"1","c2"])""")
        assertThat(pageLayoutScript(emptyList())).endsWith("([])")
    }

    @Test
    fun chapterAnchorsAreGroupedByFileInTocOrder() {
        val part = Link(href = Url("partie2.xhtml")!!, mediaType = MediaType.XHTML)
        val other = Link(href = Url("preface.xhtml")!!, mediaType = MediaType.XHTML)
        val publication = Publication(
            manifest = Manifest(
                metadata = Metadata(localizedTitle = LocalizedString("Madame Bovary")),
                readingOrder = listOf(other, part),
                tableOfContents = listOf(
                    other.copy(title = "Préface"),
                    Link(
                        href = Url("partie2.xhtml#p2")!!,
                        mediaType = MediaType.XHTML,
                        title = "Deuxième partie",
                        children = listOf(
                            Link(href = Url("partie2.xhtml#c1")!!, mediaType = MediaType.XHTML, title = "I"),
                            Link(href = Url("partie2.xhtml#c2")!!, mediaType = MediaType.XHTML, title = "II"),
                        ),
                    ),
                ),
            ),
        )
        assertThat(chapterAnchorIds(publication)).containsExactly("partie2.xhtml", listOf("p2", "c1", "c2"))
    }
}

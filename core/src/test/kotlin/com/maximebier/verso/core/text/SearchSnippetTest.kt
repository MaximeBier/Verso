package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchSnippetTest {

    @Test
    fun shortContextIsKeptWholeWithoutEllipsis() {
        val snippet = searchSnippet(before = "vers la ", match = "rivière", after = ".")
        assertThat(snippet).isEqualTo(SearchSnippet(before = "vers la ", match = "rivière", after = "."))
    }

    @Test
    fun longContextIsCutAtAWordBoundaryWithEllipsis() {
        val before = "Il y avait une fois, dans un pays lointain, au fond d’une vallée qu’arrose la Rieule, petite "
        val after = " qui se jette dans l’Andelle, après avoir fait tourner trois moulins vers son embouchure, et où il y a"
        val snippet = searchSnippet(before, "rivière", after, maxContextChars = 40)

        assertThat(snippet.before).startsWith("…")
        assertThat(snippet.before).endsWith("petite ")
        assertThat(snippet.before.length).isAtMost(41)
        assertThat(snippet.before.removePrefix("…").first()).isNotEqualTo(' ')
        assertThat(snippet.after).startsWith(" qui se jette")
        assertThat(snippet.after).endsWith("…")
        assertThat(snippet.after.length).isAtMost(41)
        // Jamais de mot coupé : le dernier mot avant « … » est entier.
        assertThat(after).contains(snippet.after.removeSuffix("…").trimEnd())
    }

    @Test
    fun whitespaceAndLineBreaksAreCollapsed() {
        val snippet = searchSnippet(before = "la\n  petite\t", match = "rivière", after = "\n qui")
        assertThat(snippet).isEqualTo(SearchSnippet(before = "la petite ", match = "rivière", after = " qui"))
    }

    @Test
    fun emptyContextStaysEmpty() {
        assertThat(searchSnippet(before = "", match = "Rivière", after = "")).isEqualTo(SearchSnippet("", "Rivière", ""))
    }
}

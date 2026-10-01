package com.maximebier.verso.core.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HighlightMergeTest {
    @Test fun noOverlapKeepsTheNewPassageAlone() {
        val plan = HighlightMerge.plan(CharSpan(10, 20), "nouvelle", listOf(PlacedHighlight(1, CharSpan(20, 30), "a")))
        assertThat(plan).isEqualTo(MergePlan(CharSpan(10, 20), "nouvelle", emptyList()))
    }

    @Test fun overlapMergesAndJoinsNotesInTextOrder() {
        val plan = HighlightMerge.plan(
            CharSpan(15, 40),
            "nouvelle",
            listOf(PlacedHighlight(1, CharSpan(10, 20), "première"), PlacedHighlight(2, CharSpan(35, 50), null)),
        )
        assertThat(plan.span).isEqualTo(CharSpan(10, 50))
        assertThat(plan.note).isEqualTo("première\n\nnouvelle")
        assertThat(plan.absorbedIds).containsExactly(1L, 2L)
    }

    @Test fun containedAndContainingPassagesMerge() {
        val inside = HighlightMerge.plan(CharSpan(12, 14), null, listOf(PlacedHighlight(1, CharSpan(10, 20), "garde")))
        assertThat(inside).isEqualTo(MergePlan(CharSpan(10, 20), "garde", listOf(1L)))
        val around = HighlightMerge.plan(CharSpan(0, 30), null, listOf(PlacedHighlight(1, CharSpan(10, 20), "garde")))
        assertThat(around).isEqualTo(MergePlan(CharSpan(0, 30), "garde", listOf(1L)))
    }

    @Test fun mergeIsTransitive() {
        // Le nouveau recoupe 1 ; l’union recoupe alors 2, qui ne touchait pas le passage seul.
        val plan = HighlightMerge.plan(
            CharSpan(18, 25),
            null,
            listOf(PlacedHighlight(1, CharSpan(10, 20), null), PlacedHighlight(2, CharSpan(5, 12), "deux")),
        )
        assertThat(plan.span).isEqualTo(CharSpan(5, 25))
        assertThat(plan.absorbedIds).containsExactly(1L, 2L)
        assertThat(plan.note).isEqualTo("deux")
    }

    @Test fun adjacentPassagesDoNotMerge() {
        val plan = HighlightMerge.plan(CharSpan(20, 25), null, listOf(PlacedHighlight(1, CharSpan(10, 20), null)))
        assertThat(plan.absorbedIds).isEmpty()
    }

    @Test fun unplacedHighlightIsNeverMerged() {
        val plan = HighlightMerge.plan(CharSpan(0, 100), null, listOf(PlacedHighlight(1, null, "perdue ?")))
        assertThat(plan.absorbedIds).isEmpty()
    }

    @Test fun joinNotesDropsBlanksAndDuplicates() {
        assertThat(HighlightMerge.joinNotes(listOf(" a ", null, "  ", "b", "a"))).isEqualTo("a\n\nb")
        assertThat(HighlightMerge.joinNotes(listOf(null, " "))).isNull()
    }
}


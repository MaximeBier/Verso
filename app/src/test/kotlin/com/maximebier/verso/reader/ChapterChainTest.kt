package com.maximebier.verso.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterChainTest {
    private val threshold = 100f
    private val bottom = ChapterEdges(atTop = false, atBottom = true)
    private val top = ChapterEdges(atTop = true, atBottom = false)
    private val middle = ChapterEdges(atTop = false, atBottom = false)

    @Test
    fun dragUpFromTheBottomEdgeOpensTheNextChapter() {
        assertThat(chapterChain(bottom, dragDyPx = -threshold, thresholdPx = threshold)).isEqualTo(ChapterChain.NEXT)
    }

    @Test
    fun dragDownFromTheTopEdgeOpensThePreviousChapter() {
        assertThat(chapterChain(top, dragDyPx = threshold, thresholdPx = threshold)).isEqualTo(ChapterChain.PREVIOUS)
    }

    @Test
    fun shortDragOrWrongDirectionDoesNothing() {
        assertThat(chapterChain(bottom, dragDyPx = -threshold + 1f, thresholdPx = threshold)).isEqualTo(ChapterChain.NONE)
        assertThat(chapterChain(bottom, dragDyPx = threshold * 3, thresholdPx = threshold)).isEqualTo(ChapterChain.NONE)
        assertThat(chapterChain(top, dragDyPx = -threshold * 3, thresholdPx = threshold)).isEqualTo(ChapterChain.NONE)
    }

    @Test
    fun gestureThatDidNotStartAtTheEdgeNeverChangesChapter() {
        // Un fling lancé au milieu qui atteint le bord : les bords sont lus à l’appui.
        assertThat(chapterChain(middle, dragDyPx = -threshold * 10, thresholdPx = threshold)).isEqualTo(ChapterChain.NONE)
        assertThat(chapterChain(null, dragDyPx = -threshold * 10, thresholdPx = threshold)).isEqualTo(ChapterChain.NONE)
    }

    @Test
    fun chapterShorterThanTheScreenFollowsTheDragDirection() {
        val both = ChapterEdges(atTop = true, atBottom = true)
        assertThat(chapterChain(both, dragDyPx = -threshold, thresholdPx = threshold)).isEqualTo(ChapterChain.NEXT)
        assertThat(chapterChain(both, dragDyPx = threshold, thresholdPx = threshold)).isEqualTo(ChapterChain.PREVIOUS)
    }
}

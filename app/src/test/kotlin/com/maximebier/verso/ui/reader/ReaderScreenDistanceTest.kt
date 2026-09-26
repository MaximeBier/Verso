package com.maximebier.verso.ui.reader

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.estimateBookScreens
import org.junit.Test

class ReaderScreenDistanceTest {

    private val phone = ReaderMetrics(viewportHeightPx = 2_000, fontScale = 1f, density = 2.625f)

    @Test
    fun lineHeightIsNineteenSpTimesOnePointSix() {
        assertThat(ReaderScreenDistance.lineHeightPx(fontScale = 1f, density = 2.625f)).isWithin(0.01f).of(79.8f)
    }

    @Test
    fun lineHeightFollowsSystemFontScale() {
        assertThat(ReaderScreenDistance.lineHeightPx(fontScale = 2f, density = 2.625f)).isWithin(0.01f).of(159.6f)
        assertThat(ReaderScreenDistance.wordsPerLine(fontScale = 2f)).isWithin(1e-9).of(3.0)
    }

    @Test
    fun bookScreensDelegatesToCoreEstimate() {
        val expected = estimateBookScreens(
            totalWords = 60_000,
            viewportHeightPx = 2_000,
            lineHeightPx = ReaderScreenDistance.lineHeightPx(1f, 2.625f),
            wordsPerLine = 6.0,
        )

        assertThat(ReaderScreenDistance.bookScreens(60_000, phone)).isWithin(1e-9).of(expected)
    }

    @Test
    fun biggerTextMeansMoreScreens() {
        val big = phone.copy(fontScale = 2f)

        assertThat(ReaderScreenDistance.bookScreens(60_000, big)).isGreaterThan(ReaderScreenDistance.bookScreens(60_000, phone))
    }

    @Test
    fun bookWithoutWordsStillHasOneScreen() {
        assertThat(ReaderScreenDistance.bookScreens(0, phone)).isEqualTo(ReaderScreenDistance.MIN_BOOK_SCREENS)
    }

    @Test
    fun distanceIsRecomputedWhenFontScaleChanges() {
        val a = BookPosition("{}", 0.10)
        val b = BookPosition("{}", 0.20)

        val normal = ReaderScreenDistance.distance(60_000, phone).screensBetween(a, b)
        val enlarged = ReaderScreenDistance.distance(60_000, phone.copy(fontScale = 2f)).screensBetween(a, b)

        assertThat(enlarged).isGreaterThan(normal)
    }

    @Test
    fun fallbackUsesReferencePhone() {
        val a = BookPosition("{}", 0.10)
        val b = BookPosition("{}", 0.20)

        assertThat(ReaderScreenDistance.fallbackDistance(60_000).screensBetween(a, b))
            .isWithin(1e-9)
            .of(ReaderScreenDistance.distance(60_000, phone).screensBetween(a, b))
    }
}

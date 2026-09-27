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
    fun largerTextAndLineSpacingMeanMoreScreens() {
        val base = ReaderScreenDistance.bookScreens(60_000, phone)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(fontSizeSp = 26.0))).isGreaterThan(base)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(lineHeight = 1.8))).isGreaterThan(base)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(marginDp = 32.0))).isGreaterThan(base)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(marginDp = 16.0))).isLessThan(base)
    }

    @Test
    fun wordsPerLineShrinkWithSizeAndMargins() {
        assertThat(ReaderScreenDistance.wordsPerLine(fontScale = 1f)).isWithin(1e-9).of(6.0)
        assertThat(ReaderScreenDistance.wordsPerLine(fontScale = 1f, fontSizeSp = 38.0)).isWithin(1e-9).of(3.0)
        assertThat(ReaderScreenDistance.wordsPerLine(fontScale = 1f, marginDp = 32.0)).isLessThan(6.0)
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

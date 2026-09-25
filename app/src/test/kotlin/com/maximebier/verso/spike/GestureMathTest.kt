package com.maximebier.verso.spike

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GestureMathTest {

    @Test
    fun screensPerSecondDividesByViewportHeight() {
        assertThat(GestureMath.screensPerSecond(velocityPxPerSecond = 4200f, viewportHeightPx = 2100))
            .isWithin(1e-9).of(2.0)
    }

    @Test
    fun screensPerSecondIgnoresDirection() {
        assertThat(GestureMath.screensPerSecond(velocityPxPerSecond = -6300f, viewportHeightPx = 2100))
            .isWithin(1e-9).of(3.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun screensPerSecondRejectsEmptyViewport() {
        GestureMath.screensPerSecond(velocityPxPerSecond = 100f, viewportHeightPx = 0)
    }

    @Test
    fun csvLineUsesSemicolonsAndRootLocale() {
        val line = GestureMath.csvLine(
            timeMs = 1_000L,
            variant = "A",
            phase = "lecture",
            event = "RELEASE",
            href = "OEBPS/ch1.xhtml",
            progression = 0.5,
            totalProgression = null,
            screensPerSecond = 1.25,
            window5sScreens = 2.0,
        )
        assertThat(line).isEqualTo("1000;A;lecture;RELEASE;OEBPS/ch1.xhtml;0.50000;;1.25000;2.00000")
    }

    @Test
    fun rollingScreensKeepsOnlyTheLastFiveSeconds() {
        val rolling = RollingScreens(windowMs = 5_000)
        rolling.add(timeMs = 0, screens = 1.0)
        rolling.add(timeMs = 3_000, screens = 1.5)
        rolling.add(timeMs = 6_000, screens = 0.5)
        assertThat(rolling.total(nowMs = 6_000)).isWithin(1e-9).of(2.0)
        assertThat(rolling.total(nowMs = 9_500)).isWithin(1e-9).of(0.5)
    }
}

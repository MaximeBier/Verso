package com.maximebier.verso.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReadingOrderPositionsTest {

    private val positions = ReadingOrderPositions.from(
        hrefs = listOf("ch1.xhtml", "ch2.xhtml", "ch3.xhtml"),
        counts = listOf(10, 30, 60),
    )!!

    private fun at(href: String, progression: Double) =
        Locator(Url(href)!!, MediaType.XHTML, locations = Locator.Locations(progression = progression))

    @Test
    fun totalProgressionWeightsChaptersByPositions() {
        assertThat(positions.totalProgression(at("ch1.xhtml", 0.0))!!).isWithin(1e-9).of(0.0)
        assertThat(positions.totalProgression(at("ch2.xhtml", 0.5))!!).isWithin(1e-9).of(0.25)
        assertThat(positions.totalProgression(at("ch3.xhtml", 1.0))!!).isWithin(1e-9).of(1.0)
    }

    @Test
    fun unknownChapterGivesNullAndLeavesLocatorUntouched() {
        val unknown = at("annexe.xhtml", 0.3)

        assertThat(positions.totalProgression(unknown)).isNull()
        assertThat(positions.withTotalProgression(unknown)).isEqualTo(unknown)
    }

    @Test
    fun withTotalProgressionFillsLocator() {
        val filled = positions.withTotalProgression(at("ch2.xhtml#note", 0.5))

        assertThat(filled.locations.totalProgression!!).isWithin(1e-9).of(0.25)
    }

    @Test
    fun emptyOrMismatchedInputGivesNull() {
        assertThat(ReadingOrderPositions.from(listOf("a"), listOf(0))).isNull()
        assertThat(ReadingOrderPositions.from(listOf("a", "b"), listOf(3))).isNull()
    }
}

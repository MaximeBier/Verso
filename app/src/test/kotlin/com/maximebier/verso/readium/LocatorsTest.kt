package com.maximebier.verso.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.html.cssSelector
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

// Robolectric : Locator s’appuie sur org.json, absent de la JVM de test simple.
@RunWith(AndroidJUnit4::class)
class LocatorsTest {

    private val locator = Locator(
        href = Url("OEBPS/chapitre-07.xhtml")!!,
        mediaType = MediaType.XHTML,
        title = "Chapitre VII",
        locations = Locator.Locations(
            progression = 0.42,
            position = 118,
            totalProgression = 0.31,
            otherLocations = mapOf("cssSelector" to "#c7 > p:nth-child(12)"),
        ),
        text = Locator.Text(highlight = "Emma descendit dans le jardin."),
    )

    @Test
    fun jsonRoundTripKeepsEveryField() {
        val back = Locators.fromJson(Locators.toJson(locator))

        assertThat(back).isNotNull()
        back!!
        assertThat(back.href.toString()).isEqualTo("OEBPS/chapitre-07.xhtml")
        assertThat(back.mediaType.toString()).isEqualTo(MediaType.XHTML.toString())
        assertThat(back.title).isEqualTo("Chapitre VII")
        assertThat(back.locations.progression).isEqualTo(0.42)
        assertThat(back.locations.position).isEqualTo(118)
        assertThat(back.locations.totalProgression).isEqualTo(0.31)
        assertThat(back.locations.cssSelector).isEqualTo("#c7 > p:nth-child(12)")
        assertThat(back.text.highlight).isEqualTo("Emma descendit dans le jardin.")
    }

    @Test
    fun jsonNeverContainsPixels() {
        val json = Locators.toJson(locator)

        assertThat(json).doesNotContain("scrollY")
        assertThat(json).doesNotContain("px")
    }

    @Test
    fun fromJsonReturnsNullForInvalidInput() {
        assertThat(Locators.fromJson("pas du JSON")).isNull()
        assertThat(Locators.fromJson("{}")).isNull()
    }

    @Test
    fun toPositionUsesTotalProgression() {
        val position = Locators.toPosition(locator)

        assertThat(position.totalProgression).isEqualTo(0.31)
        assertThat(Locators.fromJson(position.locatorJson)?.locations?.progression).isEqualTo(0.42)
    }

    @Test
    fun toPositionDefaultsToZeroWithoutTotalProgression() {
        val bare = locator.copy(locations = Locator.Locations(progression = 0.5))

        assertThat(Locators.toPosition(bare).totalProgression).isEqualTo(0.0)
    }

    @Test
    fun excerptPrefersHighlightThenAfterAndNormalizesSpaces() {
        val withAfter = locator.copy(text = Locator.Text(after = "  Charles  entra\n dans la salle. "))

        assertThat(Locators.excerptOf(locator)).isEqualTo("Emma descendit dans le jardin.")
        assertThat(Locators.excerptOf(withAfter)).isEqualTo("Charles entra dans la salle.")
        assertThat(Locators.excerptOf(locator.copy(text = Locator.Text()))).isNull()
    }

    @Test
    fun hrefKeyDropsFragment() {
        val withFragment = locator.copy(href = Url("OEBPS/chapitre-07.xhtml#note-3")!!)

        assertThat(Locators.hrefKey(withFragment)).isEqualTo("OEBPS/chapitre-07.xhtml")
    }
}

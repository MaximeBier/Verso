package com.maximebier.verso.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.testLocator
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadingStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.html.cssSelector

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PositionSaverTest {

    private data class Saved(val bookId: Long, val json: String, val progression: Double)

    private val saves = mutableListOf<Saved>()
    private val debounceMs = ReadingThresholds().saveDebounceMs

    private fun TestScope.saver() = PositionSaver(
        bookId = 7,
        save = { id, json, progression -> saves += Saved(id, json, progression) },
        scope = backgroundScope,
    )

    @Test
    fun debounceWritesOnlyTheLastRequestOfABurst() = runTest {
        val saver = saver()

        saver.requestSave(Locators.toPosition(testLocator(progression = 0.10, total = 0.10)))
        advanceTimeBy(200)
        saver.requestSave(Locators.toPosition(testLocator(progression = 0.20, total = 0.20)))
        advanceTimeBy(debounceMs - 1)
        runCurrent()
        assertThat(saves).isEmpty()

        advanceTimeBy(2)
        runCurrent()
        assertThat(saves).hasSize(1)
        assertThat(saves.single().bookId).isEqualTo(7)
        assertThat(saves.single().progression).isEqualTo(0.20)
    }

    @Test
    fun readingPositionOnScreenIsEnrichedWithVisibleText() = runTest {
        val target = testLocator(progression = 0.42, total = 0.31)
        val fake = FakeReaderController(target)
        fake.excerpt = target.copy(
            text = Locator.Text(highlight = "Emma descendit au jardin."),
            locations = target.locations.copy(otherLocations = mapOf("cssSelector" to "#c7 > p:nth-child(12)")),
        )
        val saver = saver()
        saver.attach(fake)

        saver.requestSave(Locators.toPosition(target))
        advanceTimeBy(debounceMs + 1)
        runCurrent()

        val stored = Locators.fromJson(saves.single().json)!!
        assertThat(stored.text.highlight).isEqualTo("Emma descendit au jardin.")
        assertThat(stored.locations.cssSelector).isEqualTo("#c7 > p:nth-child(12)")
        assertThat(stored.locations.progression).isEqualTo(0.42)
        assertThat(saves.single().progression).isEqualTo(0.31)
    }

    @Test
    fun neverWritesTheDisplayedPositionWhenAwayFromReading() = runTest {
        val reading = testLocator(chapter = 2, progression = 0.42, total = 0.31)
        val elsewhere = testLocator(chapter = 9, progression = 0.80, total = 0.90)
        val fake = FakeReaderController(elsewhere)
        fake.excerpt = elsewhere.copy(text = Locator.Text(highlight = "Texte d’un autre chapitre."))
        val saver = saver()
        saver.attach(fake)

        saver.requestSave(Locators.toPosition(reading))
        advanceTimeBy(debounceMs + 1)
        runCurrent()

        val stored = Locators.fromJson(saves.single().json)!!
        assertThat(stored.href.toString()).isEqualTo("chapitre-2.xhtml")
        assertThat(stored.locations.progression).isEqualTo(0.42)
        assertThat(stored.text.highlight).isNull()
        assertThat(saves.single().progression).isEqualTo(0.31)
    }

    @Test
    fun onStopFlushWritesPendingPositionWithoutWaitingForDebounce() = runTest {
        val saver = saver()

        saver.requestSave(Locators.toPosition(testLocator(progression = 0.55, total = 0.40)))
        saver.flush()
        runCurrent()
        assertThat(saves.map { it.progression }).containsExactly(0.40)

        advanceTimeBy(debounceMs * 2)
        runCurrent()
        assertThat(saves).hasSize(1) // l’anti-rebond annulé n’écrit pas une seconde fois
    }

    @Test
    fun flushWithNothingPendingWritesNothing() = runTest {
        val saver = saver()

        saver.requestSave(Locators.toPosition(testLocator(progression = 0.55, total = 0.40)))
        advanceTimeBy(debounceMs + 1)
        runCurrent()
        saver.flush()
        runCurrent()

        assertThat(saves).hasSize(1)
    }

    @Test
    fun restoredLocatorIsIndependentOfFontSize() = runTest {
        // Simulation V2 : la taille par défaut passe de 19 à 24 sp. La position restaurée ne dépend que
        // du locator écrit (fichier, progression, texte du bloc, sélecteur), jamais de pixels.
        assertThat(ReadingStyle.fontSizeFactor(24.0)).isNotEqualTo(ReadingStyle.fontSizeFactor())
        val target = testLocator(progression = 0.42, total = 0.31)
        val fake = FakeReaderController(target)
        fake.excerpt = target.copy(
            text = Locator.Text(highlight = "Emma descendit au jardin."),
            locations = target.locations.copy(otherLocations = mapOf("cssSelector" to "#c7 > p:nth-child(12)")),
        )
        val saver = saver()
        saver.attach(fake)
        saver.requestSave(Locators.toPosition(target))
        advanceTimeBy(debounceMs + 1)
        runCurrent()

        val restored = Locators.fromJson(saves.single().json)!!

        assertThat(restored.href).isEqualTo(target.href)
        assertThat(restored.locations.progression).isEqualTo(target.locations.progression)
        assertThat(restored.text.highlight).isEqualTo("Emma descendit au jardin.")
        assertThat(restored.locations.otherLocations.keys).containsExactly("cssSelector")
    }
}

package com.maximebier.verso.ui.reader

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ProgressionScreenDistance
import com.maximebier.verso.core.position.ReaderEvent
import com.maximebier.verso.core.position.ReadingPositionTracker
import com.maximebier.verso.screenshots.samples.journalSampleState
import org.junit.Test

class JournalResumeTest {

    private val reading = BookPosition("""{"href":"p2c1.xhtml","locations":{"totalProgression":0.31}}""", 0.31)

    @Test
    fun resumeHereFarFromReadingShowsReturnCard() {
        val tracker = ReadingPositionTracker(reading, ProgressionScreenDistance(bookScreens = 1_000.0))
        val target = journalSampleState.days[1].sessions.single().resumeTarget   // fin de la session d'hier, 25 %

        tracker.onEvent(ReaderEvent.Jumped(timeMs = 1_000, target = target))

        assertThat(tracker.state.showReturnCard).isTrue()
        assertThat(tracker.state.reading).isEqualTo(reading)
    }

    @Test
    fun resumeHereWithinOneScreenDoesNotShowCard() {
        val tracker = ReadingPositionTracker(reading, ProgressionScreenDistance(bookScreens = 1_000.0))
        val near = BookPosition("""{"href":"p2c1.xhtml","locations":{"totalProgression":0.3105}}""", 0.3105)  // 0,5 écran

        tracker.onEvent(ReaderEvent.Jumped(timeMs = 1_000, target = near))

        assertThat(tracker.state.showReturnCard).isFalse()
    }
}

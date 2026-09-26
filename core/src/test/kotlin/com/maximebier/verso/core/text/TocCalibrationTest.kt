package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TocCalibrationTest {

    private val chars = 200_000

    // Préordre : 0 Deuxième partie, 1 XII, 2 XIII, 3 XIV ; « Deuxième partie » et « XII » sur la même ancre.
    private val toc = listOf(
        TocNode(
            "Deuxième partie", "p2.xhtml", progression = 0.10, fileChars = chars,
            children = listOf(
                TocNode("XII", "p2.xhtml", progression = 0.10, fileChars = chars),
                TocNode("XIII", "p2.xhtml", progression = 0.50, fileChars = chars),
                TocNode("XIV", "p2.xhtml", progression = 0.80, fileChars = chars),
            ),
        ),
        TocNode("Notes", "notes.xhtml"),
    )

    private fun progressions(toc: List<TocNode>): List<Double?> =
        toc.flatMap { listOf(it.progression) + progressions(it.children) }

    @Test
    fun engineProgressionReplacesTheEstimateOfTheAnchor() {
        // Le moteur place le titre de XIII deux écrans avant l'estimation.
        val observed = 0.50 - 2_000.0 / chars

        val calibrated = calibrateAnchor(toc, index = 2, observed = observed)

        assertThat(progressions(calibrated!!)).containsExactly(0.10, 0.10, observed, 0.80, null).inOrder()
        assertThat(chapterPathAt(calibrated, "p2.xhtml", observed)).containsExactly("Deuxième partie", "XIII").inOrder()
    }

    @Test
    fun entriesSharingTheAnchorAreCalibratedTogether() {
        val calibrated = calibrateAnchor(toc, index = 1, observed = 0.105)

        assertThat(progressions(calibrated!!)).containsExactly(0.105, 0.105, 0.50, 0.80, null).inOrder()
    }

    @Test
    fun implausibleEngineProgressionIsRejected() {
        // Loin de l'estimation (début du fichier affiché pendant le chargement, défilement de l'utilisateur).
        assertThat(calibrateAnchor(toc, index = 2, observed = 0.50 - 2.0 * TocProgress.CALIBRATION_MAX_CHARS / chars)).isNull()
    }

    @Test
    fun calibrationNeverReordersTheEntriesOfTheFile() {
        val small = listOf(
            TocNode("I", "c.xhtml", progression = 0.0, fileChars = 10_000),
            TocNode("II", "c.xhtml", progression = 0.30, fileChars = 10_000),
            TocNode("III", "c.xhtml", progression = 0.40, fileChars = 10_000),
        )
        assertThat(calibrateAnchor(small, index = 1, observed = 0.45)).isNull()
        assertThat(calibrateAnchor(small, index = 1, observed = 0.0)).isNull()
        assertThat(calibrateAnchor(small, index = 1, observed = 0.35)).isNotNull()
    }

    @Test
    fun entryWithoutAnchorOrUnknownIndexIsNotCalibrated() {
        assertThat(calibrateAnchor(toc, index = 4, observed = 0.2)).isNull()
        assertThat(calibrateAnchor(toc, index = 9, observed = 0.2)).isNull()
    }
}

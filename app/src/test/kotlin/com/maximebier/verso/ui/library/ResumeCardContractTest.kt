@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.ui.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.reader.FragmentReaderController
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.ui.reader.PositionSaver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Contrat de bout en bout de la carte « Reprendre » (revue finale, I1) : le locator enrichi par le vrai
 * moteur ([FragmentReaderController.excerptLocator]), écrit par [PositionSaver], doit donner à la
 * bibliothèque un chapitre et un extrait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ResumeCardContractTest {

    @Test
    fun locatorSavedFromTheEngineGivesChapterAndExcerpt() = runTest {
        val controller = FragmentReaderController(
            scope = backgroundScope,
            readChapterHtml = { "<html><body><h1>I</h1><p>Yonville-l’Abbaye (ainsi nommé à cause d’une ancienne abbaye de Capucins)</p></body></html>" },
            onTap = {},
            adjacentChapter = { _, _ -> null },
            thresholds = ReadingThresholds(),
            uptimeMs = { testScheduler.currentTime },
            wallClockMs = { testScheduler.currentTime },
            textDispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            viewportHeightPx = 2_000
            bind(navigate = {}, probeEdges = { null }, visibleText = { null })
        }
        val reading = Locator(
            href = Url("OEBPS/partie2-chap1.xhtml")!!,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(progression = 0.0, totalProgression = 0.31),
        )
        controller.onDisplayed(reading)
        var savedJson: String? = null
        val saver = PositionSaver(
            bookId = 1,
            save = { _, json, _ -> savedJson = json },
            scope = backgroundScope,
            chapterTitle = { "Deuxième partie, chapitre I" },
        )
        saver.attach(controller)

        saver.requestSave(Locators.toPosition(controller.displayed.value!!))
        advanceTimeBy(ReadingThresholds().saveDebounceMs + 1)
        runCurrent()

        val resume = resumeInfoOf(book(requireNotNull(savedJson)))
        assertThat(resume.chapter).isEqualTo("Deuxième partie, chapitre I")
        assertThat(resume.excerpt).contains("Yonville-l’Abbaye (ainsi nommé")
    }

    @Test
    fun locatorWithoutTitleOrTextGivesNeither() {
        val json = Locators.toJson(
            Locator(href = Url("ch1.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(progression = 0.2)),
        )
        val resume = resumeInfoOf(book(json))
        assertThat(resume.chapter).isNull()
        assertThat(resume.excerpt).isNull()
    }

    private fun book(locatorJson: String) = BookEntity(
        id = 1,
        title = "Madame Bovary",
        author = "Gustave Flaubert",
        filePath = "/x.epub",
        sha256 = "sha",
        coverPath = null,
        sizeBytes = 1,
        originalFileName = "x.epub",
        importedAt = 0,
        lastOpenedAt = 1,
        readingLocatorJson = locatorJson,
        progression = 0.31,
        totalWords = 150_000,
    )
}

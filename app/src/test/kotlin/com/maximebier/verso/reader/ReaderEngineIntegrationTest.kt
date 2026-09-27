@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.importer.EpubFixtures
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadingOrderPositions
import com.maximebier.verso.readium.ReadiumOpener
import com.maximebier.verso.readium.VersoReadingPreferences
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

/**
 * Moteur de lecture sur un vrai EPUB Gutenberg (Alice). Robolectric n’exécute pas le JavaScript des
 * WebView : la mise en page et les sauts ne sont pas exercés ici, seulement ce qui les précède
 * (fabrique, cibles du sommaire, chapitres voisins, positions, extrait).
 */
@RunWith(AndroidJUnit4::class)
class ReaderEngineIntegrationTest {

    @get:Rule val tmp = TemporaryFolder()

    private val application: Application = ApplicationProvider.getApplicationContext()

    private suspend fun <T> withAlice(block: suspend (Publication) -> T): T {
        val file = EpubFixtures.resource(EpubFixtures.ALICE, File(tmp.root, "alice.epub"))
        val publication = ReadiumOpener(application).open(file).getOrThrow()
        return try {
            block(publication)
        } finally {
            publication.close()
        }
    }

    @Test
    fun gutenbergBookIsAcceptedByTheFragmentNavigator() = runTest {
        withAlice { publication ->
            val factory = EpubNavigatorFactory(publication).createFragmentFactory(
                initialLocator = publication.locatorFromLink(publication.readingOrder[1])!!.copyWithLocations(progression = 0.5),
                initialPreferences = VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS),
            )
            assertThat(factory).isNotNull()
        }
    }

    @Test
    fun everyTocEntryTargetsAReadingOrderHref() = runTest {
        withAlice { publication ->
            val readingOrderHrefs = publication.readingOrder.map { it.url().toString() }.toSet()
            val entries = publication.tableOfContents.flatten()
            assertThat(entries).isNotEmpty()

            entries.forEach { link ->
                val target = publication.locatorFromLink(link)!!
                assertThat(readingOrderHrefs).contains(target.href.removeFragment().toString())
            }
        }
    }

    @Test
    fun adjacentChaptersFollowTheReadingOrderAndStopAtTheEnds() = runTest {
        withAlice { publication ->
            val order = publication.readingOrder
            val middle = publication.locatorFromLink(order[1])!!.copyWithLocations(progression = 0.9)

            val next = adjacentChapter(publication, middle, next = true)!!
            val previous = adjacentChapter(publication, middle, next = false)!!
            assertThat(next.href.toString()).isEqualTo(order[2].url().toString())
            assertThat(next.locations.progression).isEqualTo(0.0)
            assertThat(previous.href.toString()).isEqualTo(order[0].url().toString())
            assertThat(previous.locations.progression).isEqualTo(1.0)

            val first = publication.locatorFromLink(order.first())!!
            val last = publication.locatorFromLink(order.last())!!
            assertThat(adjacentChapter(publication, first, next = false)).isNull()
            assertThat(adjacentChapter(publication, last, next = true)).isNull()
        }
    }

    @Test
    fun positionsCoverTheReadingOrderAndGrowWithIt() = runTest {
        withAlice { publication ->
            val positions = ReadingOrderPositions.load(publication)!!
            val starts = publication.readingOrder.map {
                positions.totalProgression(publication.locatorFromLink(it)!!.copyWithLocations(progression = 0.0))!!
            }

            assertThat(starts.first()).isEqualTo(0.0)
            assertThat(starts).isInStrictOrder()
            val last = publication.locatorFromLink(publication.readingOrder.last())!!.copyWithLocations(progression = 1.0)
            assertThat(positions.totalProgression(last)).isEqualTo(1.0)
        }
    }

    @Test
    fun excerptComesFromTheDisplayedChapterText() = runTest {
        withAlice { publication ->
            val controller = FragmentReaderController(
                scope = backgroundScope,
                readChapterHtml = { href -> readChapterHtml(publication, href) },
                onCenterTap = {},
                adjacentChapter = { _, _ -> null },
            )
            val chapter = publication.readingOrder[publication.readingOrder.size / 2]
            controller.onDisplayed(publication.locatorFromLink(chapter)!!.copyWithLocations(progression = 0.5))

            val excerpt = Locators.excerptOf(controller.excerptLocator()!!)

            assertThat(excerpt).isNotNull()
            assertThat(excerpt!!.length).isAtMost(ReaderGestures.EXCERPT_MAX_CHARS)
            assertThat(excerpt).doesNotContain("<")
        }
    }

    private fun List<Link>.flatten(): List<Link> = flatMap { listOf(it) + it.children.flatten() }
}

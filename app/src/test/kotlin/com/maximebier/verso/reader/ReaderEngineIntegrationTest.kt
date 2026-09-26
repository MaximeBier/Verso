@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
import org.readium.navigator.web.reflowable.ReflowableWebRenditionFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

/**
 * Moteur de lecture sur un vrai EPUB Gutenberg (Alice). Robolectric n’exécute pas le JavaScript des
 * WebView : la mise en page, `controller.goTo` et `controller.viewport` ne sont pas exercés ici, seulement
 * ce qui les précède (fabrique, état, cibles de saut, positions, extrait).
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
    fun gutenbergBookIsAcceptedByTheComposeNavigator() = runTest {
        withAlice { publication ->
            val factory = ReflowableWebRenditionFactory(
                application = application,
                publication = publication,
                configuration = VersoReadingPreferences.reflowableWebConfiguration(),
            )
            assertThat(factory).isNotNull()

            val second = publication.locatorFromLink(publication.readingOrder[1])!!
            val state = factory!!.createRenditionState(
                initialPreferences = VersoReadingPreferences.reflowableWeb(dark = false),
                initialLocation = goLocationOf(second.copyWithLocations(progression = 0.5)),
            ).getOrNull()

            assertThat(state).isNotNull()
            // Contrôleur créé seulement après la première mise en page des WebView (hors Robolectric).
            assertThat(state!!.controller).isNull()
        }
    }

    @Test
    fun everyTocEntryTargetsAReadingOrderHrefReadiumCanFind() = runTest {
        withAlice { publication ->
            val readingOrderHrefs = publication.readingOrder.map { it.url() }.toSet()
            val entries = publication.tableOfContents.flatten()
            assertThat(entries).isNotEmpty()

            entries.forEach { link ->
                val target = goLocationOf(publication.locatorFromLink(link)!!)
                assertThat(readingOrderHrefs).contains(target.href)
                assertThat(target.href.fragment).isNull()
                if (link.url().fragment != null) assertThat(target.htmlId?.value).isEqualTo(link.url().fragment)
            }
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
            val controller = ReflowableReaderController(
                scope = backgroundScope,
                readChapterHtml = { href -> readChapterHtml(publication, href) },
                onCenterTap = {},
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

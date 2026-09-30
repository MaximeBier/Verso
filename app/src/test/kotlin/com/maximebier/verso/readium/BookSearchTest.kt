package com.maximebier.verso.readium

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication

@RunWith(AndroidJUnit4::class)
class BookSearchTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var publication: Publication

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tmp.root, "bovary.epub")
        javaClass.getResourceAsStream("/epub/real/gutenberg-14155-madame-bovary-fr.epub")!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        publication = ReadiumOpener(context).open(file).getOrThrow()
    }

    @After
    fun tearDown() {
        publication.close()
    }

    private fun search() = BookSearch(publication, dispatcher = Dispatchers.Unconfined)

    @Test
    fun resultsArriveCumulatedInBookOrder() = runTest {
        val batches = search().search("rivière").toList()

        assertThat(batches).isNotEmpty()
        // Chaque lot contient le précédent : la liste s'allonge.
        batches.zipWithNext().forEach { (previous, next) ->
            assertThat(next.size).isGreaterThan(previous.size)
            assertThat(next.subList(0, previous.size)).isEqualTo(previous)
        }
        val hits = batches.last()
        assertThat(hits.map { it.progression }).isInOrder()
        hits.forEach { hit ->
            assertThat(hit.match.lowercase()).isEqualTo("rivière")
            assertThat(hit.progression).isIn(com.google.common.collect.Range.closed(0.0, 1.0))
            assertThat(hit.locator.text.highlight).isEqualTo(hit.match)
            assertThat(hit.before.length).isAtMost(com.maximebier.verso.core.text.SEARCH_CONTEXT_CHARS + 1)
        }
    }

    @Test
    fun caseAndAccentsAreIgnored() = runTest {
        val accented = search().search("rivière").toList().last()
        val plain = search().search("RIVIERE").toList().last()
        assertThat(plain.map { it.locator.locations.totalProgression }).isEqualTo(accented.map { it.locator.locations.totalProgression })
    }

    @Test
    fun tooShortQueryEmitsNothing() = runTest {
        assertThat(search().search("a").toList()).isEmpty()
        assertThat(search().search("  r  ").toList()).isEmpty()
    }

    @Test
    fun absentWordEmitsNothing() = runTest {
        assertThat(search().search("xylophonique").toList()).isEmpty()
    }

    @Test
    fun collectingOnlyTheFirstBatchCancelsTheSearch() = runTest {
        // Mot très fréquent : premier lot pris, puis la collecte s'arrête ; l'itérateur est fermé (pas d'exception).
        val first = search().search("de").first()
        assertThat(first).isNotEmpty()
    }

    @Test
    fun chapterTitleComesFromTheTableOfContents() = runTest {
        val hits = search().search("Yonville").toList().last()
        assertThat(hits.mapNotNull { it.chapter }).isNotEmpty()
    }
}

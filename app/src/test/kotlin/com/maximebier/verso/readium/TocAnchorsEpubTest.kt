package com.maximebier.verso.readium

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.core.text.preorder
import com.maximebier.verso.importer.EpubFixtures
import com.maximebier.verso.ui.reader.ChapterStatus
import com.maximebier.verso.ui.reader.TocRow
import com.maximebier.verso.ui.reader.buildTocRows
import com.maximebier.verso.ui.reader.toTocNodes
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication

/** Progression des ancres du sommaire sur de vrais EPUB ouverts par Readium (défaut C). */
@RunWith(AndroidJUnit4::class)
class TocAnchorsEpubTest {

    @get:Rule val tmp = TemporaryFolder()

    private val application: Application = ApplicationProvider.getApplicationContext()

    private suspend fun <T> withBook(file: File, block: suspend (Publication, List<TocNode>) -> T): T {
        val publication = ReadiumOpener(application).open(file).getOrThrow()
        return try {
            val toc = publication.tableOfContents
            block(publication, toc.toTocNodes(TocAnchors.load(publication, toc)))
        } finally {
            publication.close()
        }
    }

    private fun readingOrder(publication: Publication) =
        publication.readingOrder.map { it.url().removeFragment().toString() }

    private fun leaves(toc: List<TocNode>) = preorder(toc) { it.children }.filter { it.children.isEmpty() }

    @Test
    fun generatedBookWithSeveralAnchoredChaptersPerFile() = runTest {
        withBook(EpubFixtures.anchoredEpub(File(tmp.root, "ancres.epub"))) { publication, toc ->
            val chapters = leaves(toc)
            assertThat(chapters.map { it.title }).containsExactly("I", "II", "III", "IV", "V").inOrder()
            assertThat(chapters.map { it.href }.distinct()).hasSize(2)
            assertThat(chapters[0].progression).isWithin(0.01).of(0.0)
            assertThat(chapters[1].progression).isWithin(0.02).of(1.0 / 3)
            assertThat(chapters[2].progression).isWithin(0.02).of(2.0 / 3)
            // Titre courant de 50 caractères avant l'ancre de « IV » : ramenée au début du fichier.
            assertThat(chapters[3].progression).isEqualTo(0.0)
            // Id accentué, encodé dans le lien du sommaire : l'ancre est trouvée.
            assertThat(chapters[4].progression).isWithin(0.02).of(0.5)
            assertThat(chapters.map { it.fileChars }.distinct()).hasSize(2)
            assertThat(chapters[0].fileChars!!).isWithin(100).of(12_000)
            assertThat(chapters[3].fileChars!!).isWithin(150).of(8_100)

            val p1 = chapters[0].href
            val p2 = chapters[3].href
            assertThat(chapterPathAt(toc, p1, 0.0)).containsExactly("Première partie", "I").inOrder()
            assertThat(chapterPathAt(toc, p1, 0.5)).containsExactly("Première partie", "II").inOrder()
            assertThat(chapterPathAt(toc, p1, 0.9)).containsExactly("Première partie", "III").inOrder()
            assertThat(chapterPathAt(toc, p2, 0.7)).containsExactly("Deuxième partie", "V").inOrder()

            val statuses = buildTocRows(toc, readingOrder(publication), p1, 0.5)
                .filterIsInstance<TocRow.Chapter>().map { it.status }
            assertThat(statuses).containsExactly(
                ChapterStatus.READ, ChapterStatus.CURRENT, ChapterStatus.UNREAD, ChapterStatus.UNREAD, ChapterStatus.UNREAD,
            ).inOrder()
        }
    }

    @Test
    fun candideAnchorsAreAllFoundAndOrderedInTheirFile() = runTest {
        val file = EpubFixtures.resource(EpubFixtures.CANDIDE, File(tmp.root, "candide.epub"))
        withBook(file) { _, toc ->
            val entries = preorder(toc) { it.children }
            assertThat(entries.all { it.progression != null }).isTrue()
            entries.groupBy { it.href }.values.forEach { inFile ->
                val progressions = inFile.map { it.progression!! }
                assertThat(progressions).isInOrder() // ancres proches du début : ramenées à 0
            }
        }
    }

    @Test
    fun madameBovaryCurrentChapterFollowsTheReadingPosition() = runTest {
        val real = javaClass.classLoader?.getResource(BOVARY)
        assumeTrue("$BOVARY absent (corpus réel de 7.2)", real != null)
        val file = File(tmp.root, "bovary.epub").also { target -> real!!.openStream().use { target.outputStream().use(it::copyTo) } }
        withBook(file) { publication, toc ->
            val entries = preorder(toc) { it.children }
            val files = entries.map { it.href }.distinct()
            val first = entries.filter { it.href == files[0] }
            assertThat(first).hasSize(14)
            assertThat(first.map { it.progression!! }).isInOrder()
            // Page de titre et table (moins d’un écran de texte après l’en-tête) : début du fichier.
            assertThat(first.take(2).map { it.progression }).containsExactly(0.0, 0.0)
            assertThat(first.drop(1).map { it.progression!! }).isInStrictOrder()

            // Deuxième partie, chapitre XIII : 13ᵉ entrée « XIII » du sommaire (la partie commence par « DEUXIÈME PARTIE »).
            val thirteenth = entries.last { it.title.trim() == "XIII" }
            val next = entries[entries.indexOf(thirteenth) + 1]
            val middle = if (next.href == thirteenth.href) (thirteenth.progression!! + next.progression!!) / 2 else 0.99
            assertThat(chapterPathAt(toc, thirteenth.href, middle).last().trim()).isEqualTo("XIII")
            // Après un saut, le moteur peut placer le haut de l'écran un écran (≈ 1 200 caractères) avant l'estimation.
            val oneScreen = 1_200.0 / thirteenth.fileChars!!
            assertThat(chapterPathAt(toc, thirteenth.href, thirteenth.progression!! - oneScreen).last().trim()).isEqualTo("XIII")

            // À 0 % (en-tête Gutenberg), la page de titre est en cours, pas « III » de la deuxième partie ; rien de « Lu ».
            val rows = buildTocRows(toc, readingOrder(publication), files[0], 0.0).filterIsInstance<TocRow.Chapter>()
            assertThat(rows.indexOfFirst { it.status == ChapterStatus.CURRENT }).isEqualTo(0)
            assertThat(rows.none { it.status == ChapterStatus.READ }).isTrue()

            // Coût ajouté à l'ouverture (JVM, pour le rapport) : médiane de 5 calculs.
            val times = (1..5).map {
                val startNs = System.nanoTime()
                TocAnchors.load(publication, publication.tableOfContents)
                (System.nanoTime() - startNs) / 1_000_000
            }.sorted()
            System.err.println("TIMING TocAnchors.load Bovary : médiane ${times[2]} ms, tous $times")

            // Deuxième fichier au début : « IV » de la deuxième partie (ancre en tête du fichier).
            assertThat(chapterPathAt(toc, files[1], 0.0).last().trim()).isEqualTo("IV")
        }
    }

    private companion object {
        const val BOVARY = "epub/real/gutenberg-14155-madame-bovary-fr.epub"
    }
}

package com.maximebier.verso.readium

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.importer.EpubFixtures
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadiumOpenerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opener = ReadiumOpener(context)

    private fun Result<*>.failure(): ReadiumOpener.Failure? =
        (exceptionOrNull() as? OpenFailureException)?.failure

    private fun file(name: String) = File(tmp.root, name)

    @Test
    fun minimalEpubIsReadable() = runTest {
        val info = opener.inspect(EpubFixtures.epub(file("petit.epub"))).getOrThrow()
        assertThat(info.title).isEqualTo("Un petit livre")
        assertThat(info.author).isNull()
        assertThat(info.cover).isNull()
        assertThat(info.totalWords).isGreaterThan(0L)
    }

    @Test
    fun epubWithoutTitleGivesNullTitle() = runTest {
        val info = opener.inspect(EpubFixtures.epub(file("sans-titre.epub"), title = null)).getOrThrow()
        assertThat(info.title).isNull()
    }

    @Test
    fun epubWithoutMimetypeIsAccepted() = runTest {
        val info = opener.inspect(EpubFixtures.epub(file("sans-mimetype.bin"), withMimetype = false)).getOrThrow()
        assertThat(info.title).isEqualTo("Un petit livre")
    }

    @Test
    fun textFileRenamedEpubIsNotEpub() = runTest {
        assertThat(opener.inspect(EpubFixtures.textFile(file("texte.epub"))).failure())
            .isEqualTo(ReadiumOpener.Failure.NotEpub)
    }

    @Test
    fun imagesZipIsNotEpub() = runTest {
        assertThat(opener.inspect(EpubFixtures.imagesZip(file("images.epub"))).failure())
            .isEqualTo(ReadiumOpener.Failure.NotEpub)
    }

    @Test
    fun adobeAdeptIsDrm() = runTest {
        assertThat(opener.inspect(EpubFixtures.adobeDrm(file("adept.epub"))).failure())
            .isEqualTo(ReadiumOpener.Failure.Drm)
    }

    @Test
    fun lcpIsDrm() = runTest {
        assertThat(opener.inspect(EpubFixtures.lcpDrm(file("lcp.epub"))).failure())
            .isEqualTo(ReadiumOpener.Failure.Drm)
    }

    @Test
    fun containerWithoutPackageIsUnreadable() = runTest {
        assertThat(opener.inspect(EpubFixtures.brokenEpub(file("casse.epub"))).failure())
            .isInstanceOf(ReadiumOpener.Failure.Unreadable::class.java)
    }

    @Test
    fun gutenbergMetadataCoverAndWords() = runTest {
        val info = opener.inspect(EpubFixtures.resource(EpubFixtures.CANDIDE, file("candide.epub"))).getOrThrow()
        assertThat(info.title).startsWith("Candide")
        assertThat(info.author).isEqualTo("Voltaire")
        val cover = requireNotNull(info.cover)
        assertThat(cover.width).isAtMost(600)
        assertThat(cover.height).isAtMost(900)
        assertThat(info.totalWords).isGreaterThan(10_000L)
    }

    @Test
    fun openGivesPublicationWithReadingOrder() = runTest {
        val publication = opener.open(EpubFixtures.resource(EpubFixtures.ALICE, file("alice.epub"))).getOrThrow()
        try {
            assertThat(publication.readingOrder).isNotEmpty()
            assertThat(publication.metadata.title).startsWith("Alice")
        } finally {
            publication.close()
        }
    }

    @Test
    fun offlineHttpClientNeverReachesNetwork() = runTest {
        val request = HttpRequest(requireNotNull(AbsoluteUrl("https://www.gutenberg.org/")))
        val result = OfflineHttpClient.stream(request)
        assertThat(result.failureOrNull()).isInstanceOf(HttpError.Unreachable::class.java)
    }
}

package com.maximebier.verso.importer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class IncomingIntentTest {

    private val epubUri = Uri.parse("content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D42")

    @Before
    fun setUp() {
        IncomingImports.clear()
    }

    @Test
    fun viewIntentGivesData() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(epubUri, IncomingIntent.EPUB_MIME_TYPE)
        assertThat(IncomingIntent.parse(intent)).isEqualTo(epubUri)
    }

    @Test
    fun sendIntentGivesStreamOnApi33AndLater() {
        val intent = Intent(Intent.ACTION_SEND).setType(IncomingIntent.EPUB_MIME_TYPE).putExtra(Intent.EXTRA_STREAM, epubUri)
        assertThat(IncomingIntent.parse(intent)).isEqualTo(epubUri)
    }

    @Test
    @Config(sdk = [32])
    fun sendIntentGivesStreamBeforeApi33() {
        val intent = Intent(Intent.ACTION_SEND).setType(IncomingIntent.EPUB_MIME_TYPE).putExtra(Intent.EXTRA_STREAM, epubUri)
        assertThat(IncomingIntent.parse(intent)).isEqualTo(epubUri)
    }

    @Test
    fun sendIntentFallsBackToClipData() {
        val intent = Intent(Intent.ACTION_SEND).setType(IncomingIntent.EPUB_MIME_TYPE)
        intent.clipData = ClipData.newRawUri("livre", epubUri)
        assertThat(IncomingIntent.parse(intent)).isEqualTo(epubUri)
    }

    @Test
    fun otherActionsAndSchemesAreIgnored() {
        assertThat(IncomingIntent.parse(null)).isNull()
        assertThat(IncomingIntent.parse(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(IncomingIntent.parse(Intent(Intent.ACTION_SEND).setType(IncomingIntent.EPUB_MIME_TYPE))).isNull()
        assertThat(IncomingIntent.parse(Intent(Intent.ACTION_VIEW, Uri.parse("https://exemple.org/livre.epub")))).isNull()
    }

    @Test
    fun fileSchemeIsAccepted() {
        val uri = Uri.parse("file:///storage/emulated/0/Download/candide.epub")
        assertThat(IncomingIntent.parse(Intent(Intent.ACTION_VIEW, uri))).isEqualTo(uri)
    }

    @Test
    fun incomingImportsIsAFifoQueue() {
        val a = Uri.parse("content://a/1")
        val b = Uri.parse("content://b/2")
        assertThat(IncomingImports.hasPending()).isFalse()
        IncomingImports.submit(a)
        IncomingImports.submit(b)
        assertThat(IncomingImports.pending.value).containsExactly(a, b).inOrder()
        assertThat(IncomingImports.hasPending()).isTrue()
        assertThat(IncomingImports.take()).isEqualTo(a)
        assertThat(IncomingImports.take()).isEqualTo(b)
        assertThat(IncomingImports.take()).isNull()
        assertThat(IncomingImports.hasPending()).isFalse()
    }

    @Test
    fun mainActivitySubmitsIncomingUriOnCreateAndOnNewIntent() {
        val seen = mutableListOf<Uri>()
        val watcher = CoroutineScope(Dispatchers.Unconfined).launch { IncomingImports.pending.collect { seen += it } }

        val first = Intent(Intent.ACTION_VIEW).setDataAndType(epubUri, IncomingIntent.EPUB_MIME_TYPE)
        val controller = Robolectric.buildActivity(MainActivity::class.java, first).setup()
        assertThat(seen).contains(epubUri)

        val other = Uri.parse("content://fichiers/document/germinal.epub")
        controller.newIntent(Intent(Intent.ACTION_SEND).setType(IncomingIntent.EPUB_MIME_TYPE).putExtra(Intent.EXTRA_STREAM, other))
        assertThat(seen).contains(other)

        controller.pause().stop().destroy()
        watcher.cancel()
    }
}

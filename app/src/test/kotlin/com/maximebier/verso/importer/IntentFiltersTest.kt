package com.maximebier.verso.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/** Vérifie les intent-filters du manifeste fusionné (résolution par le PackageManager de Robolectric). */
@RunWith(AndroidJUnit4::class)
class IntentFiltersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun resolvesToMainActivity(intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent.addCategory(Intent.CATEGORY_DEFAULT), 0)
            .any { it.activityInfo.name == MainActivity::class.java.name }

    private fun view(uri: String, type: String) = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), type)

    @Test
    fun viewEpubResolves() {
        assertThat(resolvesToMainActivity(view("content://fournisseur/document/42", "application/epub+zip"))).isTrue()
    }

    @Test
    fun sendEpubResolves() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/epub+zip")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://fournisseur/document/42"))
        assertThat(resolvesToMainActivity(intent)).isTrue()
    }

    @Test
    fun octetStreamWithEpubPathResolves() {
        assertThat(resolvesToMainActivity(view("content://fichiers/document/Download/candide.epub", "application/octet-stream"))).isTrue()
        assertThat(resolvesToMainActivity(view("content://fichiers/document/v1.2/candide.EPUB", "application/octet-stream"))).isTrue()
    }

    @Test
    fun octetStreamWithOtherPathDoesNotResolve() {
        assertThat(resolvesToMainActivity(view("content://fichiers/document/Download/facture.pdf", "application/octet-stream"))).isFalse()
    }
}

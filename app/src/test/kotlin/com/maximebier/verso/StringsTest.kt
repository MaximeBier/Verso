package com.maximebier.verso

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun percentUsesNarrowNoBreakSpace() {
        assertThat(context.getString(R.string.common_percent_read, 31)).isEqualTo("31 % lu")
    }

    @Test
    fun guillemetsUseNoBreakSpaces() {
        assertThat(context.getString(R.string.import_success, "Germinal")).isEqualTo("« Germinal » a été ajouté.")
    }

    @Test
    fun questionMarkUsesNarrowNoBreakSpace() {
        assertThat(context.getString(R.string.details_delete_dialog_title, "Madame Bovary"))
            .isEqualTo("Supprimer « Madame Bovary » ?")
    }

    @Test
    fun durationsUseNoBreakSpaces() {
        assertThat(context.getString(R.string.common_duration_hours_minutes, 5, 30)).isEqualTo("5 h 30")
        assertThat(context.getString(R.string.common_time_remaining, "5 h 30")).isEqualTo("Environ 5 h 30 restantes")
    }

    @Test
    fun apostropheIsTypographic() {
        assertThat(context.getString(R.string.import_error_title)).isEqualTo("Impossible d’importer ce fichier")
    }

    @Test
    fun sourceCodeLinkPointsToTheRepository() {
        assertThat(context.getString(R.string.settings_source_code_url)).isEqualTo("https://github.com/MaximeBier/Verso")
        assertThat(context.getString(R.string.settings_source_code_url_label)).isEqualTo("github.com/MaximeBier/Verso")
    }

    @Test
    fun emptyLibraryLabelsMatchTheMockup() {
        assertThat(context.getString(R.string.library_empty_title)).isEqualTo("Votre bibliothèque est vide")
        assertThat(context.getString(R.string.library_empty_import)).isEqualTo("Importer un EPUB")
    }
}

package com.maximebier.verso.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.PixelCopy
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Photos d’avance du tour de page : photo de l’écran et pause après un changement de style. */
@RunWith(AndroidJUnit4::class)
class PageTurnCaptureTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val box = Rect(0, 0, 120, 200)

    private fun capture(request: PixelCopyRequest): List<Bitmap?> {
        val results = mutableListOf<Bitmap?>()
        capturePageScreen(activity, box, { results += it }, request)
        return results
    }

    @Test
    fun aWindowWithoutSurfaceGivesNoPhotoInsteadOfCrashing() {
        // Application passée en arrière-plan ou écran éteint juste avant la photo : PixelCopy refuse la fenêtre.
        val results = capture { _, _, _, _, _ -> throw IllegalArgumentException("Window doesn't have a backing surface!") }
        assertThat(results).containsExactly(null)
    }

    @Test
    fun aSuccessfulCopyGivesThePhotoOfTheBox() {
        val results = capture { _, _, bitmap, listener, _ -> assertThat(bitmap.width).isEqualTo(120); listener.onPixelCopyFinished(PixelCopy.SUCCESS) }
        assertThat(results).hasSize(1)
        assertThat(results.single()!!.height).isEqualTo(200)
    }

    @Test
    fun aFailedCopyGivesNoPhoto() {
        assertThat(capture { _, _, _, listener, _ -> listener.onPixelCopyFinished(PixelCopy.ERROR_SOURCE_INVALID) }).containsExactly(null)
    }

    @Test
    fun anEmptyBoxOrNoActivityGivesNoPhotoWithoutAsking() {
        var asked = false
        val results = mutableListOf<Bitmap?>()
        capturePageScreen(activity, Rect(), { results += it }) { _, _, _, _, _ -> asked = true }
        capturePageScreen(null, box, { results += it }) { _, _, _, _, _ -> asked = true }
        assertThat(results).containsExactly(null, null)
        assertThat(asked).isFalse()
    }

    @Test
    fun noPrefetchUntilTheNextPageAfterAStyleChange() {
        val style = ReaderStyle(ReadingSettings(), AppTheme.LIGHT, ScrollMode.PAGES)
        // Premier affichage du livre : photos d’avance de la page affichée.
        assertThat(displayedToSkipBeforePrefetch(null, style)).isEqualTo(0)
        // Navigateur recréé, même style : pas de pause.
        assertThat(displayedToSkipBeforePrefetch(style, style)).isEqualTo(0)
        // Thème ou mise en page changés : la page affichée garde son locator, on attend la suivante.
        assertThat(displayedToSkipBeforePrefetch(style, style.copy(theme = AppTheme.NIGHT))).isEqualTo(1)
        assertThat(displayedToSkipBeforePrefetch(style, style.copy(settings = ReadingSettings(fontSizeSp = style.settings.fontSizeSp + 1)))).isEqualTo(1)
    }
}

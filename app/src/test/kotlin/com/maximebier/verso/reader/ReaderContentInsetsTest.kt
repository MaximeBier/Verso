package com.maximebier.verso.reader

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Anomalie G : afficher la barre de lecture rétablit les barres système (mode immersif). Les insets
 * donnés au navigateur ne doivent pas bouger pour autant : Readium en tire les marges de défilement
 * du document, et un document qui change de hauteur change la progression du haut de l’écran sans
 * que le texte ait bougé (lu comme de la lecture par le suivi).
 */
@RunWith(AndroidJUnit4::class)
class ReaderContentInsetsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View
    private var readerTop = -1
    private var readerBottom = -1
    private var safeDrawingBottom = -1

    @Test
    fun showingOrHidingTheSystemBarsNeverChangesTheReaderInsets() {
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            val reader = readerContentInsets
            val safe = WindowInsets.safeDrawing
            // Lus pendant la composition, pour recomposer à chaque nouvel inset.
            val top = reader.getTop(density)
            val bottom = reader.getBottom(density)
            val safeBottom = safe.getBottom(density)
            SideEffect {
                readerTop = top
                readerBottom = bottom
                safeDrawingBottom = safeBottom
            }
        }

        dispatch(systemBarsVisible = true)
        val shownTop = readerTop
        val shownBottom = readerBottom
        assertThat(safeDrawingBottom).isEqualTo(NAVIGATION_BAR)

        dispatch(systemBarsVisible = false)
        // Contrôle : les insets ont bien été appliqués (les barres visibles ont disparu).
        assertThat(safeDrawingBottom).isEqualTo(0)
        assertThat(readerTop).isEqualTo(shownTop)
        assertThat(readerBottom).isEqualTo(shownBottom)
        assertThat(readerTop).isEqualTo(STATUS_BAR)
        assertThat(readerBottom).isEqualTo(NAVIGATION_BAR)
    }

    private fun dispatch(systemBarsVisible: Boolean) {
        val status = Insets.of(0, STATUS_BAR, 0, 0)
        val navigation = Insets.of(0, 0, 0, NAVIGATION_BAR)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), if (systemBarsVisible) status else Insets.NONE)
            .setInsets(WindowInsetsCompat.Type.navigationBars(), if (systemBarsVisible) navigation else Insets.NONE)
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(), status)
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), navigation)
            .setVisible(WindowInsetsCompat.Type.systemBars(), systemBarsVisible)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view.rootView, insets) }
        compose.waitForIdle()
    }

    private companion object {
        const val STATUS_BAR = 110
        const val NAVIGATION_BAR = 126
    }
}

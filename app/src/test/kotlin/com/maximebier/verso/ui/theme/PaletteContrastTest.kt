package com.maximebier.verso.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Test

class PaletteContrastTest {

    private class ColorPair(val label: String, val foreground: (VersoColors) -> Color, val background: (VersoColors) -> Color)

    /** Les quatre palettes (V1 : clair et sombre ; V2 : sépia et noir). */
    private val themes = listOf(
        "clair" to VersoPalette.Light,
        "sépia" to VersoPalette.Sepia,
        "sombre" to VersoPalette.Dark,
        "noir" to VersoPalette.Black,
    )

    /** Couples texte/fond réellement utilisés par les écrans : ≥ 7:1 (WCAG AAA). */
    private val textPairs = listOf(
        ColorPair("text/background", { it.text }, { it.background }),
        ColorPair("text/surface", { it.text }, { it.surface }),
        ColorPair("text/surfaceHigh", { it.text }, { it.surfaceHigh }),
        ColorPair("text/selection", { it.text }, { it.selection }),
        ColorPair("textSecondary/background", { it.textSecondary }, { it.background }),
        ColorPair("textSecondary/surface", { it.textSecondary }, { it.surface }),
        ColorPair("textSecondary/surfaceHigh", { it.textSecondary }, { it.surfaceHigh }),
        ColorPair("accent/background", { it.accent }, { it.background }),
        ColorPair("accent/surface", { it.accent }, { it.surface }),
        ColorPair("onAccent/accent", { it.onAccent }, { it.accent }),
        ColorPair("onSelection/selection", { it.onSelection }, { it.selection }),
        ColorPair("danger/background", { it.danger }, { it.background }),
        ColorPair("danger/surface", { it.danger }, { it.surface }),
        ColorPair("onDanger/danger", { it.onDanger }, { it.danger }),
        ColorPair("onInverse/inverse", { it.onInverse }, { it.inverse }),
        ColorPair("inverseAccent/inverse", { it.inverseAccent }, { it.inverse }),
        ColorPair("onInverseAccent/inverseAccent", { it.onInverseAccent }, { it.inverseAccent }),
        ColorPair("text/highlight", { it.text }, { it.highlight }),
    )

    /** Éléments non textuels (contours, barres, interrupteur) : ≥ 3:1. */
    private val nonTextPairs = listOf(
        ColorPair("outline/background", { it.outline }, { it.background }),
        ColorPair("outline/surface", { it.outline }, { it.surface }),
        ColorPair("outline/surfaceHigh", { it.outline }, { it.surfaceHigh }),
        ColorPair("accent/progressTrack", { it.accent }, { it.progressTrack }),
        ColorPair("progressInk/progressTrack", { it.progressInk }, { it.progressTrack }),
        ColorPair("accent/surfaceHigh", { it.accent }, { it.surfaceHigh }),
    )

    @Test
    fun paletteIsGeneratedFromTokensJson() {
        assertThat(VersoPalette.Light.background).isEqualTo(Color(0xFFF5F1E8))
        assertThat(VersoPalette.Dark.accent).isEqualTo(Color(0xFFE8C48E))
        assertThat(VersoPalette.Sepia.background).isEqualTo(Color(0xFFEFE3CC))
        assertThat(VersoPalette.Black.background).isEqualTo(Color(0xFF000000))
        assertThat(VersoPalette.Light.scrim).isEqualTo(Color(0x6B1F1B16))
        assertThat(VersoPalette.Dark.scrim).isEqualTo(Color(0x94000000))
        assertThat(VersoPalette.CoverLight).hasSize(6)
        assertThat(VersoPalette.CoverDark).hasSize(6)
    }

    @Test
    fun everyTextPairReachesSevenToOne() {
        for ((theme, colors) in themes) {
            for (pair in textPairs) {
                assertWithMessage("$theme ${pair.label}")
                    .that(contrast(pair.foreground(colors), pair.background(colors)))
                    .isAtLeast(7.0)
            }
        }
    }

    @Test
    fun everyNonTextPairReachesThreeToOne() {
        for ((theme, colors) in themes) {
            for (pair in nonTextPairs) {
                assertWithMessage("$theme ${pair.label}")
                    .that(contrast(pair.foreground(colors), pair.background(colors)))
                    .isAtLeast(3.0)
            }
        }
    }

    /** Spec, « Vignettes générées » : texte clair sur couleur sourde, au moins 4,5:1. */
    @Test
    fun generatedCoversReachFourAndAHalfToOne() {
        for (cover in VersoPalette.CoverLight) {
            assertWithMessage("clair $cover").that(contrast(VersoPalette.Light.onCover, cover)).isAtLeast(4.5)
        }
        for (cover in VersoPalette.CoverDark) {
            assertWithMessage("sombre $cover").that(contrast(VersoPalette.Dark.onCover, cover)).isAtLeast(4.5)
        }
        for (cover in VersoPalette.CoverLight) {
            assertWithMessage("sépia $cover").that(contrast(VersoPalette.Sepia.onCover, cover)).isAtLeast(4.5)
        }
        for (cover in VersoPalette.CoverDark) {
            assertWithMessage("noir $cover").that(contrast(VersoPalette.Black.onCover, cover)).isAtLeast(4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}

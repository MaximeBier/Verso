package com.maximebier.verso.spike

import java.util.Locale
import kotlin.math.abs

/** Calculs purs du prototype : conversion des vitesses en écrans par seconde et format du journal CSV. */
object GestureMath {

    /** Vitesse de relâchement (px/s) convertie en écrans par seconde, rapportée à la hauteur de la zone de lecture. */
    fun screensPerSecond(velocityPxPerSecond: Float, viewportHeightPx: Int): Double {
        require(viewportHeightPx > 0) { "viewportHeightPx doit être strictement positif" }
        return abs(velocityPxPerSecond.toDouble()) / viewportHeightPx
    }

    /**
     * Ligne du journal : horodatage;variante;phase;événement;href;progression;totalProgression;écrans_par_s;écrans_sur_5s.
     * Les valeurs absentes restent vides pour que le fichier s'ouvre tel quel dans un tableur.
     */
    fun csvLine(
        timeMs: Long,
        variant: String,
        phase: String,
        event: String,
        href: String,
        progression: Double?,
        totalProgression: Double?,
        screensPerSecond: Double?,
        window5sScreens: Double?,
    ): String = listOf(
        timeMs.toString(),
        variant,
        phase,
        event,
        href,
        progression.format5(),
        totalProgression.format5(),
        screensPerSecond.format5(),
        window5sScreens.format5(),
    ).joinToString(";")

    private fun Double?.format5(): String = this?.let { String.format(Locale.ROOT, "%.5f", it) } ?: ""
}

/** Somme des écrans parcourus sur une fenêtre glissante (« plus de 3 écrans en moins de 5 s »). */
class RollingScreens(private val windowMs: Long = 5_000) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    fun add(timeMs: Long, screens: Double) {
        samples.addLast(timeMs to screens)
        trim(timeMs)
    }

    fun total(nowMs: Long): Double {
        trim(nowMs)
        return samples.sumOf { it.second }
    }

    private fun trim(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().first > windowMs) {
            samples.removeFirst()
        }
    }
}

/** Affichage court d'une progression dans l'overlay. */
internal fun Double?.format3(): String = this?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "?"

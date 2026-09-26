package com.maximebier.verso.core.position

import com.maximebier.verso.core.model.BookPosition
import kotlin.math.max

/**
 * Événements venus du lecteur. Le temps (`timeMs`, horloge monotone en millisecondes) est toujours
 * fourni par l'événement : la machine à états ne lit aucune horloge et reste déterministe.
 */
sealed interface ReaderEvent {
    val timeMs: Long

    /** La position affichée a changé (ou est confirmée). */
    data class Displayed(override val timeMs: Long, val position: BookPosition) : ReaderEvent

    /** Fin d'un geste (doigt levé) ; `isFling` si le geste a été lancé. */
    data class GestureEnded(override val timeMs: Long, val position: BookPosition, val isFling: Boolean) : ReaderEvent

    /**
     * Saut explicite : sommaire, journal, carte, lien interne. `approximate` : la cible n'a pas de
     * progression fiable (ancre d'un lien ou du sommaire, rapportée au début du fichier) ; l'arrivée est
     * alors la prochaine position affichée stabilisée, où qu'elle soit.
     */
    data class Jumped(
        override val timeMs: Long,
        val target: BookPosition,
        val approximate: Boolean = false,
    ) : ReaderEvent

    /** « Rester ici » sur la carte de retour. */
    data class StayHere(override val timeMs: Long) : ReaderEvent

    /** « Revenir » sur la carte de retour. */
    data class GoBack(override val timeMs: Long) : ReaderEvent

    /** Battement d'horloge (environ une fois par seconde pendant la lecture) : détecte le repos. */
    data class Tick(override val timeMs: Long) : ReaderEvent
}

enum class TrackerMode { FOLLOWING, AWAY }

data class TrackerState(
    val reading: BookPosition,
    val displayed: BookPosition,
    val mode: TrackerMode,
    val showReturnCard: Boolean,
)

sealed interface TrackerEffect {
    /** Position de lecture à persister. Émise au repos seulement ; l'app peut encore la regrouper (debounce). */
    data class SaveReading(val position: BookPosition) : TrackerEffect

    /** Faire défiler le lecteur jusqu'à cette position (« Revenir »). */
    data class ScrollTo(val position: BookPosition) : TrackerEffect

    /** Mouvement de lecture validé, de `from` à `to` : alimente les sessions (mots lus). */
    data class ReadingMoved(val from: BookPosition, val to: BookPosition) : TrackerEffect
}

/**
 * Machine à états « position de lecture / position affichée » (spec, « Marque-page et progression »).
 *
 * Règles :
 * - **FOLLOWING** : la lecture suit l'affiché tant que le mouvement ressemble à de la lecture. Elle
 *   n'est mise à jour qu'**au repos** : `GestureEnded` sans fling, ou aucun `Displayed` depuis
 *   [ReadingThresholds.saveDebounceMs] (constaté au `Tick` ou à l'événement suivant). Émet alors
 *   `SaveReading` + `ReadingMoved`.
 * - **Navigation** = fling (`GestureEnded.isFling`, ou vitesse entre deux `Displayed` consécutifs
 *   supérieure à [ReadingThresholds.displayedSpeedNavigationScreensPerSecond]), déplacement net supérieur à
 *   [ReadingThresholds.navigationWindowScreens] dans une fenêtre glissante de
 *   [ReadingThresholds.navigationWindowMs], ou `Jumped`. Elle ne touche pas la lecture. Si la fenêtre
 *   révèle une navigation après que de petits mouvements ont déjà avancé la lecture, la lecture est
 *   **restaurée** à la dernière position validée au plus tard au début de la fenêtre (émet `SaveReading`).
 * - Au repos après une navigation : affiché à plus de [ReadingThresholds.returnCardMinScreens] de la
 *   lecture → **AWAY** avec la carte ; sinon FOLLOWING, sans mise à jour par la navigation elle-même.
 * - **AWAY** : pas de minuterie. `StayHere` → lecture := affiché. `GoBack` → `ScrollTo(lecture)` ;
 *   les `Displayed` suivants sont ignorés jusqu'à l'arrivée près de la cible (« saut en cours »), qui
 *   ne compte ni comme navigation ni comme lecture. Retour manuel par petits mouvements à au plus un
 *   écran de la lecture → FOLLOWING. Confirmation : un mouvement de lecture survenant au moins
 *   [ReadingThresholds.confirmReadingMs] après le point d'arrivée, à au plus
 *   [ReadingThresholds.confirmMaxDriftScreens] de ce point, fait passer la lecture à l'affiché ; si la
 *   dérive dépasse, le point d'arrivée se déplace et le chrono repart. Les `Tick` seuls ne confirment
 *   jamais : un téléphone posé avec la carte la garde.
 * - Saut vers une cible approximative (`Jumped.approximate` : ancre, rapportée au début du fichier) :
 *   chaque `Displayed` est gardé comme position affichée, et la première position stabilisée
 *   ([ReadingThresholds.saveDebounceMs] sans nouveau `Displayed`) est l'arrivée ; la carte est alors
 *   réévaluée depuis elle. `StayHere` enregistre toujours la dernière position affichée.
 * - Le premier `Displayed` reçu est l'arrivée à la position initiale : il ne modifie jamais la lecture.
 * - `SaveReading` sans `ReadingMoved` (restauration, « Rester ici », confirmation) : la lecture change
 *   sans mouvement de lecture, aucun mot n'est compté.
 */
class ReadingPositionTracker(
    initial: BookPosition,
    distance: ScreenDistance,
    private val thresholds: ReadingThresholds = ReadingThresholds(),
) {
    /** Position horodatée. */
    private class Stamped(val timeMs: Long, val position: BookPosition)

    private var screenDistance: ScreenDistance = distance
    private var reading: BookPosition = initial
    private var displayed: BookPosition = initial
    private var mode: TrackerMode = TrackerMode.FOLLOWING

    /** Dernière position connue en mouvement (Displayed ou fin de geste) ; null avant le premier Displayed. */
    private var lastMotion: Stamped? = null

    /** Instant où l'affiché a atteint sa valeur actuelle (horodatage des positions validées). */
    private var displayedAtMs: Long = Long.MIN_VALUE

    /** Mouvement en cours, pas encore traité au repos. */
    private var motionPending = false

    /** Navigation détectée, en attente du repos pour décider de la carte. */
    private var navigating = false

    /** Saut en cours (Jumped, GoBack) : Displayed ignorés jusqu'à l'arrivée près de cette cible. */
    private var jumpTarget: BookPosition? = null

    /** Le saut en cours vise une cible approximative (ancre) : l'arrivée est la position stabilisée. */
    private var jumpApproximate = false

    /** Au moins une position affichée reçue depuis le début du saut approximatif. */
    private var jumpDisplayedSeen = false

    /** Point d'arrivée en AWAY et début du chrono de confirmation. */
    private var anchor: Stamped = Stamped(0L, initial)

    /** Positions affichées de la fenêtre glissante. */
    private val samples = ArrayDeque<Stamped>()

    /** Historique horodaté des positions de lecture validées (pour la restauration). */
    private val history = ArrayDeque<Stamped>().apply { addLast(Stamped(Long.MIN_VALUE, initial)) }

    val state: TrackerState
        get() = TrackerState(
            reading = reading,
            displayed = displayed,
            mode = mode,
            showReturnCard = mode == TrackerMode.AWAY,
        )

    /** Remplace la conversion en écrans (livre ou taille de texte changés). */
    fun updateDistance(distance: ScreenDistance) {
        screenDistance = distance
    }

    fun onEvent(event: ReaderEvent): List<TrackerEffect> {
        val effects = mutableListOf<TrackerEffect>()
        settleIfResting(event.timeMs, effects)
        when (event) {
            is ReaderEvent.Displayed -> onDisplayed(event.timeMs, event.position, effects)
            is ReaderEvent.GestureEnded -> onGestureEnded(event.timeMs, event.position, event.isFling, effects)
            is ReaderEvent.Jumped -> onJumped(event.timeMs, event.target, event.approximate, effects)
            is ReaderEvent.StayHere -> onStayHere(event.timeMs, effects)
            is ReaderEvent.GoBack -> onGoBack(event.timeMs, effects)
            is ReaderEvent.Tick -> Unit
        }
        pruneHistory(event.timeMs)
        return effects
    }

    private fun onDisplayed(timeMs: Long, position: BookPosition, effects: MutableList<TrackerEffect>) {
        val target = jumpTarget
        if (target != null) {
            if (jumpApproximate) {
                // Cible sans progression fiable : on garde la dernière position rapportée par le moteur ;
                // l'arrivée sera la prochaine position stabilisée (voir settleIfResting).
                displayed = position
                displayedAtMs = timeMs
                lastMotion = Stamped(timeMs, position)
                jumpDisplayedSeen = true
                return
            }
            // Saut en cours : seule une position proche de la cible marque l'arrivée.
            if (screens(position, target) <= thresholds.returnCardMinScreens) arriveAt(timeMs, position)
            return
        }
        val previous = lastMotion
        if (previous == null) {
            // Premier Displayed : arrivée à la position initiale.
            arriveAt(timeMs, position)
            return
        }
        if (position == displayed) return // Displayed stable : pas un mouvement.
        val fast = speedScreensPerSecond(previous, timeMs, position) > thresholds.displayedSpeedNavigationScreensPerSecond
        lastMotion = Stamped(timeMs, position)
        move(timeMs, position, forcedNavigation = fast, effects = effects)
    }

    private fun onGestureEnded(
        timeMs: Long,
        position: BookPosition,
        isFling: Boolean,
        effects: MutableList<TrackerEffect>,
    ) {
        if (jumpTarget != null) {
            // L'utilisateur reprend la main pendant un saut : sa position devient la référence.
            if (jumpApproximate) arriveFromApproximateJump(timeMs, position) else arriveAt(timeMs, position)
            if (isFling) navigating = true
            return
        }
        val moved = position != displayed
        lastMotion = Stamped(timeMs, position)
        if (moved) {
            move(timeMs, position, forcedNavigation = isFling, effects = effects)
        } else if (isFling && !navigating) {
            startNavigation(timeMs, windowStartMs = null, effects = effects)
        }
        if (!isFling) settle(effects)
    }

    private fun onJumped(
        timeMs: Long,
        target: BookPosition,
        approximate: Boolean,
        effects: MutableList<TrackerEffect>,
    ) {
        settle(effects)
        navigating = false
        motionPending = false
        displayed = target
        displayedAtMs = timeMs
        jumpTarget = target
        jumpApproximate = approximate
        jumpDisplayedSeen = false
        lastMotion = Stamped(timeMs, target)
        resetWindow(timeMs, target)
        if (screens(target, reading) > thresholds.returnCardMinScreens) enterAway(timeMs) else mode = TrackerMode.FOLLOWING
    }

    /**
     * Fin d'un saut approximatif : la dernière position affichée par le moteur est l'arrivée, et la carte
     * est réévaluée depuis elle (la cible, début du fichier, n'était qu'une estimation).
     */
    private fun arriveFromApproximateJump(timeMs: Long, position: BookPosition) {
        arriveAt(timeMs, position)
        if (screens(position, reading) > thresholds.returnCardMinScreens) enterAway(timeMs) else mode = TrackerMode.FOLLOWING
    }

    private fun onStayHere(timeMs: Long, effects: MutableList<TrackerEffect>) {
        // « Rester ici » enregistre toujours la position réellement affichée, même avant la stabilisation.
        if (jumpTarget != null && jumpApproximate && jumpDisplayedSeen) arriveFromApproximateJump(timeMs, displayed)
        settle(effects)
        if (mode != TrackerMode.AWAY) return
        commit(displayed, timeMs, reportMove = false, effects = effects)
        mode = TrackerMode.FOLLOWING
        motionPending = false
        resetWindow(timeMs, displayed)
    }

    private fun onGoBack(timeMs: Long, effects: MutableList<TrackerEffect>) {
        settle(effects)
        if (mode != TrackerMode.AWAY) return
        mode = TrackerMode.FOLLOWING
        navigating = false
        motionPending = false
        displayed = reading
        displayedAtMs = timeMs
        jumpTarget = reading
        jumpApproximate = false
        lastMotion = Stamped(timeMs, reading)
        resetWindow(timeMs, reading)
        effects += TrackerEffect.ScrollTo(reading)
    }

    /** Mouvement de l'affiché (hors saut) : classe lecture / navigation. */
    private fun move(
        timeMs: Long,
        position: BookPosition,
        forcedNavigation: Boolean,
        effects: MutableList<TrackerEffect>,
    ) {
        displayed = position
        displayedAtMs = timeMs
        motionPending = true
        if (navigating) return
        val windowStartMs = recordSample(timeMs, position)
        if (forcedNavigation || windowStartMs != null) {
            startNavigation(timeMs, windowStartMs, effects)
            return
        }
        if (mode == TrackerMode.AWAY) moveWhileAway(timeMs, position, effects)
    }

    /** Mouvement de lecture en AWAY : retour manuel, dérive du point d'arrivée, confirmation. */
    private fun moveWhileAway(timeMs: Long, position: BookPosition, effects: MutableList<TrackerEffect>) {
        if (screens(position, reading) <= thresholds.returnCardMinScreens) {
            // Retour manuel près de la lecture : la carte disparaît, la lecture suivra au repos.
            mode = TrackerMode.FOLLOWING
            return
        }
        if (screens(position, anchor.position) > thresholds.confirmMaxDriftScreens) {
            anchor = Stamped(timeMs, position)
            return
        }
        if (timeMs - anchor.timeMs >= thresholds.confirmReadingMs) {
            commit(position, timeMs, reportMove = false, effects = effects)
            mode = TrackerMode.FOLLOWING
            motionPending = false
            resetWindow(timeMs, position)
        }
    }

    /**
     * Ajoute l'échantillon à la fenêtre glissante et renvoie l'instant du plus ancien échantillon situé à plus de
     * [ReadingThresholds.navigationWindowScreens] de la position actuelle (début de la navigation), ou null.
     */
    private fun recordSample(timeMs: Long, position: BookPosition): Long? {
        while (samples.isNotEmpty() && samples.first().timeMs < timeMs - thresholds.navigationWindowMs) {
            samples.removeFirst()
        }
        samples.addLast(Stamped(timeMs, position))
        return samples.firstOrNull { screens(it.position, position) > thresholds.navigationWindowScreens }?.timeMs
    }

    private fun startNavigation(timeMs: Long, windowStartMs: Long?, effects: MutableList<TrackerEffect>) {
        navigating = true
        if (windowStartMs == null) return
        val restored = (history.lastOrNull { it.timeMs <= windowStartMs } ?: history.first()).position
        if (restored != reading) {
            reading = restored
            history.addLast(Stamped(timeMs, restored))
            effects += TrackerEffect.SaveReading(restored)
        }
    }

    private fun settleIfResting(nowMs: Long, effects: MutableList<TrackerEffect>) {
        val last = lastMotion ?: return
        val resting = nowMs - last.timeMs >= thresholds.saveDebounceMs
        if (jumpTarget != null && jumpApproximate && jumpDisplayedSeen && resting) {
            // Saut approximatif : la position affichée stabilisée est l'arrivée.
            arriveFromApproximateJump(last.timeMs, last.position)
            return
        }
        if ((motionPending || navigating) && nowMs - last.timeMs >= thresholds.saveDebounceMs) settle(effects)
    }

    /** Traitement du repos : fin de navigation (carte ou non) ou validation de la lecture. */
    private fun settle(effects: MutableList<TrackerEffect>) {
        val restMs = lastMotion?.timeMs ?: return
        if (navigating) {
            navigating = false
            motionPending = false
            resetWindow(restMs, displayed)
            if (screens(displayed, reading) > thresholds.returnCardMinScreens) {
                enterAway(restMs)
            } else {
                mode = TrackerMode.FOLLOWING
            }
        } else if (motionPending) {
            motionPending = false
            if (mode == TrackerMode.FOLLOWING && displayed != reading) {
                commit(displayed, displayedAtMs, reportMove = true, effects = effects)
            }
        }
    }

    private fun enterAway(timeMs: Long) {
        mode = TrackerMode.AWAY
        anchor = Stamped(timeMs, displayed)
    }

    private fun arriveAt(timeMs: Long, position: BookPosition) {
        jumpApproximate = false
        jumpDisplayedSeen = false
        jumpTarget = null
        displayed = position
        displayedAtMs = timeMs
        lastMotion = Stamped(timeMs, position)
        motionPending = false
        resetWindow(timeMs, position)
    }

    private fun commit(
        position: BookPosition,
        timeMs: Long,
        reportMove: Boolean,
        effects: MutableList<TrackerEffect>,
    ) {
        val from = reading
        reading = position
        history.addLast(Stamped(timeMs, position))
        effects += TrackerEffect.SaveReading(position)
        if (reportMove) effects += TrackerEffect.ReadingMoved(from, position)
    }

    private fun resetWindow(timeMs: Long, position: BookPosition) {
        samples.clear()
        samples.addLast(Stamped(timeMs, position))
    }

    /** Garde l'historique utile à la restauration (deux fenêtres), et toujours au moins une entrée plus ancienne. */
    private fun pruneHistory(nowMs: Long) {
        val cutoff = nowMs - 2 * thresholds.navigationWindowMs
        while (history.size > 1 && history[1].timeMs <= cutoff) history.removeFirst()
    }

    private fun speedScreensPerSecond(from: Stamped, timeMs: Long, position: BookPosition): Double {
        val elapsedMs = max(1L, timeMs - from.timeMs)
        return screens(from.position, position) * 1_000.0 / elapsedMs
    }

    private fun screens(a: BookPosition, b: BookPosition): Double = screenDistance.screensBetween(a, b)
}

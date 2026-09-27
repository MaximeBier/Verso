package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ReaderEvent
import com.maximebier.verso.core.position.ReadingPositionTracker
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.core.position.ScreenDistance
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.core.position.TrackerState
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.readium.Locators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

data class PositionState(
    val reading: BookPosition,
    val displayed: BookPosition,
    val showReturnCard: Boolean,
)

/**
 * Traduit ce que fait la surface de lecture en événements pour la machine à états (`:core`) et
 * applique ses effets. Aucune règle de lecture/navigation ici : elles sont toutes dans le tracker.
 */
class ReadingPositionCoordinator(
    initial: BookPosition,
    distance: ScreenDistance,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val onSave: (BookPosition) -> Unit,
    thresholds: ReadingThresholds = ReadingThresholds(),
) {
    private val tracker = ReadingPositionTracker(initial, distance, thresholds)
    private val _state = MutableStateFlow(tracker.state.toPositionState())
    val state: StateFlow<PositionState> = _state.asStateFlow()

    private val _readingEffects = MutableSharedFlow<TrackerEffect>(extraBufferCapacity = 64)

    /**
     * `SaveReading` et `ReadingMoved`, dans l’ordre de la liste renvoyée par le tracker (jamais `ScrollTo`).
     * `SaveReading` sans `ReadingMoved` : restauration, « Rester ici » ou confirmation (aucun mot lu).
     */
    val readingEffects: SharedFlow<TrackerEffect> = _readingEffects.asSharedFlow()

    private var controller: ReaderController? = null
    private var jobs: List<Job> = emptyList()
    private var ticker: Job? = null
    private var stopped = false
    private var lastDisplayed: BookPosition = initial

    /** « Revenir » touché sans surface branchée (recréation) : rejoué au prochain [attach]. */
    private var pendingScrollTo: Locator? = null

    /** Branche la surface de lecture (et le battement d’horloge) ; remplace la précédente. */
    fun attach(readerController: ReaderController) {
        if (controller === readerController) return
        detach()
        controller = readerController
        jobs = listOf(
            scope.launch {
                readerController.displayed.filterNotNull().collect { locator ->
                    val position = Locators.toPosition(locator)
                    lastDisplayed = position
                    dispatch(ReaderEvent.Displayed(clock(), position))
                }
            },
            scope.launch {
                readerController.gestures.collect { signal ->
                    dispatch(ReaderEvent.GestureEnded(signal.timeMs, lastDisplayed, signal.isFling, signal.chapterTurn))
                }
            },
        )
        pendingScrollTo?.let { target -> scope.launch { readerController.go(target) } }
        pendingScrollTo = null
        if (!stopped) startTicker()
    }

    /** Lecteur en arrière-plan (`ON_STOP`) : plus de battement d’horloge jusqu’à [onStarted]. */
    fun onStopped() {
        dispatch(ReaderEvent.Rest(clock()))
        stopped = true
        stopTicker()
    }

    /** Lecteur au premier plan (`ON_START`) : le battement reprend si une surface est branchée. */
    fun onStarted() {
        stopped = false
        if (controller != null) startTicker()
    }

    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                dispatch(ReaderEvent.Tick(clock()))
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    /**
     * Surface retirée (activité recréée) : plus rien n’est lu de l’ancienne rendition jusqu’au prochain
     * [attach]. L’état du tracker (lecture, carte) est gardé.
     */
    fun detach() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        stopTicker()
        controller = null
    }

    /** Saut explicite (sommaire, journal) : annoncé au tracker avant le déplacement. */
    fun onJump(target: Locator) {
        dispatch(jumpedTo(target))
        val current = controller
        scope.launch { current?.go(target) }
    }

    /**
     * Lien interne suivi par le moteur lui-même (spec : saut explicite) : annoncé au tracker, sans
     * `go()`. Appelé avant que le moteur ne se déplace, pour que l’affichage qui suit soit une navigation.
     */
    fun onLinkFollowed(target: Locator) {
        dispatch(jumpedTo(target))
    }

    /**
     * Cible sans progression fiable : ancre (lien interne, entrée de sommaire) que `locatorFromLink`
     * rapporte sans progression, au début du fichier. Le tracker prend alors l’affiché stabilisé pour l’arrivée.
     */
    private fun jumpedTo(target: Locator): ReaderEvent.Jumped {
        val hasAnchor = target.href.fragment != null || target.locations.fragments.isNotEmpty()
        val approximate = hasAnchor || target.locations.progression == null
        return ReaderEvent.Jumped(clock(), Locators.toPosition(target), approximate)
    }

    fun onStayHere() = dispatch(ReaderEvent.StayHere(clock()))

    fun onGoBack() = dispatch(ReaderEvent.GoBack(clock()))

    fun updateDistance(distance: ScreenDistance) {
        tracker.updateDistance(distance)
        _state.value = tracker.state.toPositionState()
    }

    /** Seuils du mode de défilement courant (`ReadingThresholds.forScrollMode`). */
    fun updateThresholds(thresholds: ReadingThresholds) {
        tracker.updateThresholds(thresholds)
        _state.value = tracker.state.toPositionState()
    }

    private fun dispatch(event: ReaderEvent) {
        val effects = tracker.onEvent(event)
        _state.value = tracker.state.toPositionState()
        effects.forEach { effect ->
            when (effect) {
                is TrackerEffect.SaveReading -> {
                    onSave(effect.position)
                    _readingEffects.tryEmit(effect)
                }
                is TrackerEffect.ScrollTo -> {
                    val target = Locators.fromJson(effect.position.locatorJson)
                    val current = controller
                    when {
                        target == null -> Unit
                        current != null -> scope.launch { current.go(target) }
                        else -> pendingScrollTo = target
                    }
                }
                is TrackerEffect.ReadingMoved -> _readingEffects.tryEmit(effect)
            }
        }
    }

    companion object {
        const val TICK_INTERVAL_MS = 1_000L
    }
}

private fun TrackerState.toPositionState() = PositionState(reading, displayed, showReturnCard)

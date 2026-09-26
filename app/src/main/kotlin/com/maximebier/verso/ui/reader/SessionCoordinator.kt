package com.maximebier.verso.ui.reader

import android.util.Log
import com.maximebier.verso.core.journal.SessionEvent
import com.maximebier.verso.core.journal.SessionRecord
import com.maximebier.verso.core.journal.SessionThresholds
import com.maximebier.verso.core.journal.SessionTracker
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.data.db.SessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Alimente le SessionTracker (:core, 6.1) depuis le lecteur et écrit chaque session via [upsert].
 *
 * - Les événements sont horodatés à l'appel ([clock]) puis traités un par un par un seul consommateur :
 *   l'ordre des écritures est garanti et le tracker n'est jamais touché par deux fils à la fois.
 * - Effets de la machine à états (5.1) : `ReadingMoved(from, to)` devient `SessionEvent.ReadingMoved` avec les mots
 *   parcourus vers l'avant ; `SaveReading(p)` devient `SessionEvent.ReadingMoved(p, 0)` (la fin de session suit la
 *   lecture sans compter de mots) ; `ScrollTo` est ignoré. Pour le tracker, un `ReadingMoved` est une interaction.
 * - Entre `onBackgrounded` et `onStarted`, rien n'est transmis au tracker (aucune session ne s'ouvre écran éteint) ;
 *   seule la dernière position de lecture est retenue, pour la session suivante.
 * - Le coordinateur possède sa propre portée : [close] (sortie du lecteur, onCleared du ViewModel)
 *   envoie Closed et laisse le consommateur écrire la dernière session même si viewModelScope est annulé.
 */
class SessionCoordinator(
    bookId: Long,
    private val totalWords: Long,
    private val upsert: suspend (SessionEntity) -> Long,
    private val clock: () -> Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    thresholds: SessionThresholds = SessionThresholds(),
    private val tickIntervalMs: Long = TICK_INTERVAL_MS,
    private val onWriteFailed: (Exception) -> Unit = { e -> Log.w(TAG, "Écriture de la session impossible", e) },
) {
    private val tracker = SessionTracker(bookId, thresholds)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val events = Channel<SessionEvent>(Channel.UNLIMITED)
    private val _current = MutableStateFlow<SessionRecord?>(null)

    /** Session en cours (« En cours » dans le journal), null entre deux sessions. */
    val current: StateFlow<SessionRecord?> = _current.asStateFlow()

    // Appels venant du fil principal (ViewModel) ; @Volatile par prudence si un appelant change de fil.
    @Volatile private var lastPosition: BookPosition? = null
    @Volatile private var backgrounded = false
    private var ticker: Job? = null

    /** Seul consommateur des événements ; se termine quand [close] a fermé la file et qu'elle est vidée. */
    private val consumer: Job = scope.launch {
        for (event in events) {
            for (record in tracker.onEvent(event)) persist(record)
            _current.value = tracker.current
        }
    }

    /** Livre chargé, position de lecture restaurée. */
    fun onOpened(position: BookPosition) {
        lastPosition = position
        send(SessionEvent.Opened(clock(), position))
    }

    /** Scroll (Displayed), fin de geste ou toucher. */
    fun onInteraction() {
        if (backgrounded) return
        send(SessionEvent.Interaction(clock()))
    }

    /** Effet de la machine à états (ReaderViewModel.readingEffects) : seuls les ReadingMoved comptent des mots. */
    fun onTrackerEffect(effect: TrackerEffect) {
        val event = when (effect) {
            is TrackerEffect.ReadingMoved -> {
                val delta = (effect.to.totalProgression - effect.from.totalProgression).coerceAtLeast(0.0)
                lastPosition = effect.to
                SessionEvent.ReadingMoved(clock(), effect.to, (delta * totalWords).roundToLong())
            }
            is TrackerEffect.SaveReading -> {
                lastPosition = effect.position
                SessionEvent.ReadingMoved(clock(), effect.position, wordsDelta = 0L)
            }
            is TrackerEffect.ScrollTo -> return
        }
        if (backgrounded) return
        send(event)
    }

    /** onStop de l'écran de lecture. */
    fun onBackgrounded() {
        backgrounded = true
        send(SessionEvent.Backgrounded(clock()))
    }

    /** onStart de l'écran de lecture : après une mise en arrière-plan, une nouvelle session commence. */
    fun onStarted() {
        if (!backgrounded) return
        backgrounded = false
        lastPosition?.let { send(SessionEvent.Opened(clock(), it)) }
    }

    /** Démarre le Tick (fin de session après 5 min sans interaction, temps actif à jour). */
    fun start() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                send(SessionEvent.Tick(clock()))
            }
        }
    }

    /** Sortie du lecteur : dernière écriture puis arrêt. Les appels suivants sont ignorés. */
    fun close() {
        ticker?.cancel()
        ticker = null
        send(SessionEvent.Closed(clock()))
        events.close()
    }

    /** Attend la dernière écriture après [close] (tests : avant de fermer la base). */
    suspend fun awaitClosed() = consumer.join()

    private fun send(event: SessionEvent) {
        events.trySend(event)
    }

    private suspend fun persist(record: SessionRecord) {
        // Une écriture ratée (disque plein, base corrompue) ne doit jamais faire tomber la lecture : journalisée
        // ([onWriteFailed]), la session reste en mémoire et la prochaine écriture la retente.
        val id = try {
            upsert(record.toEntity())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onWriteFailed(e)
            return
        }
        val current = tracker.current
        if (record.id == 0L && current != null && current.id == 0L && current.startedAt == record.startedAt) {
            tracker.assignId(id)
        }
    }

    companion object {
        const val TICK_INTERVAL_MS: Long = 1_000
        private const val TAG = "SessionCoordinator"
    }
}

internal fun SessionRecord.toEntity(): SessionEntity = SessionEntity(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = endedAt,
    activeMs = activeMs,
    startLocatorJson = start.locatorJson,
    endLocatorJson = end.locatorJson,
    startProgression = start.totalProgression,
    endProgression = end.totalProgression,
    wordsRead = wordsRead,
)

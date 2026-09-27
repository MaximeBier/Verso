package com.maximebier.verso.ui.reader

import android.util.Log
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.readium.Locators
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.shared.publication.Locator

/**
 * Écrit la position de LECTURE en base : anti-rebond de 500 ms, écriture immédiate à la mise en
 * arrière-plan, locator enrichi du texte visible quand la lecture est à l’écran. [chapterTitle] : titre de chapitre
 * donné au locator écrit (carte « Reprendre »), null si la position est hors sommaire.
 * La position affichée n’est jamais écrite.
 */
class PositionSaver(
    private val bookId: Long,
    private val save: suspend (bookId: Long, locatorJson: String, progression: Double) -> Unit,
    private val scope: CoroutineScope,
    private val debounceMs: Long = ReadingThresholds().saveDebounceMs,
    private val chapterTitle: (Locator) -> String? = { null },
) {
    private var controller: ReaderController? = null
    private var pending: BookPosition? = null
    private var debounceJob: Job? = null
    private val writeMutex = Mutex()

    /** Rang de la dernière écriture demandée et de la dernière écrite. */
    private var requested = 0L
    private var written = 0L

    fun attach(controller: ReaderController) {
        this.controller = controller
    }

    /** Position de lecture à écrire (effet `SaveReading` de la machine à états). */
    fun requestSave(position: BookPosition) {
        pending = position
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            // Seule l’attente s’annule : une écriture commencée va au bout (sinon la position serait perdue).
            withContext(NonCancellable) { writePending() }
        }
    }

    /** Mise en arrière-plan (`onStop`) : écrit tout de suite ce qui attend, sans être annulable. */
    fun flush() {
        debounceJob?.cancel()
        debounceJob = null
        scope.launch {
            withContext(NonCancellable) { writePending() }
        }
    }

    private suspend fun writePending() {
        val position = pending ?: return
        pending = null
        val order = ++requested
        val locator = Locators.fromJson(position.locatorJson)
        // Extrait calculé hors du verrou et borné : la lecture JavaScript ne s’annule pas et peut ne jamais répondre
        // (WebView détruite pendant l’appel) ; elle ne bloque alors ni cette écriture ni les suivantes.
        val json = if (locator != null) Locators.toJson(enrichWithin(locator)) else position.locatorJson
        writeMutex.withLock {
            // Une écriture plus récente est déjà passée : celle-ci, plus ancienne, ne l’écrase pas.
            if (order < written) return
            written = order
            // Une écriture ratée (disque plein, base corrompue) ne doit jamais faire tomber la lecture : journalisée,
            // la position suivante sera réécrite par la prochaine sauvegarde.
            try {
                save(bookId, json, position.totalProgression)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Écriture de la position de lecture impossible", e)
            }
        }
    }

    private suspend fun enrichWithin(position: Locator): Locator {
        val titled = chapterTitle(position)?.let { position.copy(title = it) } ?: position
        val enriching = scope.async { enrich(position) }
        return withTimeoutOrNull(EXCERPT_TIMEOUT_MS) { enriching.await() } ?: titled.also { enriching.cancel() }
    }

    /**
     * Titre du chapitre de la position de lecture ([chapterTitle], carte « Reprendre » ; Readium l’ignore à la
     * restauration). Texte visible et sélecteur seulement si la position de lecture est celle à l’écran.
     */
    private suspend fun enrich(position: Locator): Locator {
        val target = chapterTitle(position)?.let { position.copy(title = it) } ?: position
        val reader = controller ?: return target
        val shown = reader.displayed.value ?: return target
        if (!sameSpot(target, shown)) return target
        val excerpt = reader.excerptLocator() ?: return target
        if (Locators.hrefKey(excerpt) != Locators.hrefKey(target)) return target
        return target.copy(
            text = excerpt.text,
            locations = target.locations.copy(
                otherLocations = target.locations.otherLocations + excerpt.locations.otherLocations,
            ),
        )
    }

    private fun sameSpot(a: Locator, b: Locator): Boolean =
        Locators.hrefKey(a) == Locators.hrefKey(b) &&
            abs((a.locations.progression ?: 0.0) - (b.locations.progression ?: 0.0)) <= SAME_SPOT_TOLERANCE

    companion object {
        /** Écart de progression dans le chapitre en dessous duquel lecture et affichage coïncident. */
        const val SAME_SPOT_TOLERANCE = 0.000_5

        /** Au-delà, la position est écrite sans extrait (le navigateur ne répond pas). */
        const val EXCERPT_TIMEOUT_MS = 1_000L
        private const val TAG = "PositionSaver"
    }
}

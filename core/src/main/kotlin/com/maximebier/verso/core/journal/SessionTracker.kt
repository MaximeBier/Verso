package com.maximebier.verso.core.journal

import com.maximebier.verso.core.model.BookPosition
import kotlin.math.max

/** Session de lecture telle qu'écrite en base. Positions de début et de fin = positions de LECTURE. */
data class SessionRecord(
    val id: Long, // 0 tant que non persistée
    val bookId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val activeMs: Long,
    val start: BookPosition,
    val end: BookPosition,
    val wordsRead: Long,
)

/** Événements du lecteur utiles au journal ; le temps est fourni par l'événement. */
sealed interface SessionEvent {
    val timeMs: Long

    /** Livre ouvert (ou revenu au premier plan) à cette position de lecture. */
    data class Opened(override val timeMs: Long, val position: BookPosition) : SessionEvent

    /** Scroll ou toucher. */
    data class Interaction(override val timeMs: Long) : SessionEvent

    /** Mouvement de lecture validé (effet `ReadingMoved` de la machine à états), avec les mots parcourus. */
    data class ReadingMoved(override val timeMs: Long, val to: BookPosition, val wordsDelta: Long) : SessionEvent

    data class Backgrounded(override val timeMs: Long) : SessionEvent

    data class Closed(override val timeMs: Long) : SessionEvent

    data class Tick(override val timeMs: Long) : SessionEvent
}

/**
 * Découpe la lecture d'un livre en sessions (spec, « Journal de lecture »).
 *
 * - Début : à l'ouverture (`Opened`), ou à la première interaction après une fin (arrière-plan,
 *   plus de [SessionThresholds.inactivityEndMs] d'inactivité).
 * - Fin : `Backgrounded`, `Closed`, ou plus de [SessionThresholds.inactivityEndMs] sans interaction
 *   (constaté au `Tick` ou à l'interaction suivante). La fin est datée de la dernière interaction.
 * - Temps actif : somme des intervalles entre interactions (l'ouverture compte comme la première),
 *   hors intervalles de plus de [SessionThresholds.activeGapMs].
 * - Mots lus : somme des `wordsDelta` des `ReadingMoved` uniquement ; un delta négatif compte pour 0.
 *   La fin de session suit la position de lecture (`ReadingMoved.to`) ; l'app envoie
 *   `ReadingMoved(to, 0)` quand la lecture change sans mouvement de lecture (« Rester ici »…).
 * - Chaque changement renvoie la session en cours pour upsert, pour qu'une fermeture brutale ne perde
 *   rien. Une session sans aucune interaction n'est jamais renvoyée (ouverture puis fermeture immédiate).
 *
 * Côté app : pour chaque session renvoyée, `val id = upsert(record)` puis, si `record.id == 0L` et que
 * `record.startedAt == current?.startedAt`, `assignId(id)`.
 */
class SessionTracker(
    private val bookId: Long,
    private val thresholds: SessionThresholds = SessionThresholds(),
) {
    private var session: SessionRecord? = null
    private var hasInteraction = false
    private var lastInteractionAt = 0L

    /** Position de lecture connue ; null tant que le livre n'est pas ouvert (ou après `Closed`). */
    private var readingPosition: BookPosition? = null

    /** Session en cours (« En cours »), null sinon. */
    val current: SessionRecord?
        get() = session

    /** Renvoie les sessions à écrire (upsert), dans l'ordre : la session close éventuelle, puis la session en cours. */
    fun onEvent(event: SessionEvent): List<SessionRecord> {
        val out = mutableListOf<SessionRecord>()
        when (event) {
            is SessionEvent.Opened -> {
                close(out)
                readingPosition = event.position
                start(event.timeMs, event.position)
            }
            is SessionEvent.Interaction -> interact(event.timeMs, out)?.let { out += it }
            is SessionEvent.ReadingMoved -> interact(event.timeMs, out)?.let { s ->
                val updated = s.copy(end = event.to, wordsRead = s.wordsRead + event.wordsDelta.coerceAtLeast(0L))
                session = updated
                readingPosition = event.to
                out += updated
            }
            is SessionEvent.Backgrounded -> close(out)
            is SessionEvent.Closed -> {
                close(out)
                readingPosition = null
            }
            is SessionEvent.Tick ->
                if (session != null && event.timeMs - lastInteractionAt > thresholds.inactivityEndMs) close(out)
        }
        return out
    }

    /** À appeler après la première insertion de la session en cours. */
    fun assignId(id: Long) {
        session = session?.copy(id = id)
    }

    /** Enregistre une interaction ; ferme et rouvre si l'inactivité a dépassé le seuil. Null si le livre est fermé. */
    private fun interact(timeMs: Long, out: MutableList<SessionRecord>): SessionRecord? {
        val position = readingPosition ?: return null
        if (session != null && timeMs - lastInteractionAt > thresholds.inactivityEndMs) close(out)
        val s = session ?: start(timeMs, position)
        val gap = (timeMs - lastInteractionAt).coerceAtLeast(0L)
        val active = if (gap <= thresholds.activeGapMs) gap else 0L
        val updated = s.copy(endedAt = max(s.endedAt, timeMs), activeMs = s.activeMs + active)
        session = updated
        lastInteractionAt = max(lastInteractionAt, timeMs)
        hasInteraction = true
        return updated
    }

    private fun start(timeMs: Long, position: BookPosition): SessionRecord {
        val s = SessionRecord(
            id = 0L,
            bookId = bookId,
            startedAt = timeMs,
            endedAt = timeMs,
            activeMs = 0L,
            start = position,
            end = position,
            wordsRead = 0L,
        )
        session = s
        lastInteractionAt = timeMs
        hasInteraction = false
        return s
    }

    /** Ferme la session en cours ; `endedAt` vaut déjà la dernière interaction. */
    private fun close(out: MutableList<SessionRecord>) {
        val s = session ?: return
        if (hasInteraction) out += s
        session = null
        hasInteraction = false
    }
}

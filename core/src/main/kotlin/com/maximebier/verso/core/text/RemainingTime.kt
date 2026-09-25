package com.maximebier.verso.core.text

import kotlin.math.roundToLong

/**
 * Durée découpée pour l'affichage (`v1-ui-reference.md`, formatage des valeurs) :
 * `hours > 0 && minutes > 0` → `common_duration_hours_minutes` (« 5 h 30 », « 1 h 05 ») ;
 * `minutes == 0` → `common_duration_hours` (« 5 h ») ; `hours == 0` → `common_duration_minutes` (« 45 min »).
 */
data class DurationParts(val hours: Int, val minutes: Int)

/** Vitesse de lecture V1 (spec : 250 mots/min ; la vitesse mesurée arrive en V2). */
const val DEFAULT_WORDS_PER_MINUTE = 250

/**
 * Minutes restantes : `totalWords × (1 − progression) / wordsPerMinute`, progression bornée à 0..1
 * (NaN = 0). Moins d'une minute exacte → 0, affiché « Moins d'une minute restante »
 * (`common_time_remaining_less_than_minute`) ; sinon arrondi à la minute la plus proche, demi-minute
 * vers le haut. Lève [IllegalArgumentException] si `wordsPerMinute ≤ 0`.
 */
fun remainingMinutes(totalWords: Long, progression: Double, wordsPerMinute: Int = DEFAULT_WORDS_PER_MINUTE): Int {
    require(wordsPerMinute > 0) { "wordsPerMinute doit être strictement positif : $wordsPerMinute" }
    if (totalWords <= 0) return 0
    val read = if (progression.isNaN()) 0.0 else progression.coerceIn(0.0, 1.0)
    val exactMinutes = totalWords * (1.0 - read) / wordsPerMinute
    if (exactMinutes < 1.0) return 0
    return exactMinutes.roundToLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Découpe en heures et minutes ; une valeur négative donne 0 h 0 min. */
fun durationOfMinutes(minutes: Int): DurationParts {
    val total = minutes.coerceAtLeast(0)
    return DurationParts(hours = total / 60, minutes = total % 60)
}

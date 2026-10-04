package com.maximebier.verso.core.translation

/**
 * Service de traduction, de l’anglais vers le français (V3). L’interface ne connaît pas le service : Azure dans l’app,
 * un faux traducteur dans les tests. Les échecs passent par [TranslationFailure].
 */
interface Translator {
    /** Sens d’un mot ou d’une expression courte, du plus probable au moins probable ; vide si le dictionnaire ne sait pas. */
    suspend fun lookup(word: String): List<String>

    /** Traduction d’un passage. */
    suspend fun translate(text: String): String
}

/** Échec d’un appel : [Offline] sans réseau, [Unavailable] pour tout le reste (quota épuisé, réponse invalide…). */
sealed class TranslationFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Offline(cause: Throwable? = null) : TranslationFailure("Pas de connexion", cause)
    class Unavailable(message: String, cause: Throwable? = null) : TranslationFailure(message, cause)
}

/** Ce que montre la feuille de traduction une fois la réponse arrivée. */
sealed interface TranslationResult {
    /** Mot ou expression courte : la traduction la plus probable, puis au plus [Translation.MAX_ALTERNATIVES] autres sens. */
    data class Word(val primary: String, val alternatives: List<String>) : TranslationResult
    data class Passage(val text: String) : TranslationResult
    data object Offline : TranslationResult
    data object Unavailable : TranslationResult
    data object TooLong : TranslationResult
}

/** Règles de la traduction de la sélection (V3). */
object Translation {
    /** Au plus ce nombre de mots : dictionnaire (plusieurs sens) ; au-delà, traduction d’un passage. */
    const val MAX_LOOKUP_WORDS = 3

    /** Autres sens affichés sous la traduction principale. */
    const val MAX_ALTERNATIVES = 3

    /** Au-delà, rien n’est envoyé : « Sélectionnez un passage plus court pour le traduire. » */
    const val MAX_CHARACTERS = 1_000

    private val whitespace = Regex("\\s+")

    /** Espaces ramenées à une seule, aux bouts comprises. */
    fun clean(selection: String): String = selection.replace(whitespace, " ").trim()

    /** Forme envoyée au dictionnaire : ponctuation et guillemets ôtés aux deux bouts (« acknowledged, » → « acknowledged »). */
    fun lookupForm(selection: String): String = clean(selection).trim { !it.isLetterOrDigit() }

    /** Mot ou expression de [MAX_LOOKUP_WORDS] mots au plus. */
    fun isShort(selection: String): Boolean {
        val words = lookupForm(selection)
        return words.isNotEmpty() && words.split(' ').size <= MAX_LOOKUP_WORDS
    }

    /** Sens du dictionnaire sans doublon (casse ignorée) ni vide, dans l’ordre reçu, au plus 1 + [MAX_ALTERNATIVES]. */
    fun senses(lookup: List<String>): List<String> =
        lookup.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }.take(1 + MAX_ALTERNATIVES)

    /**
     * Traduit [selection] : dictionnaire pour un mot ou une expression courte (sans la ponctuation des bouts), traduction
     * si le dictionnaire ne renvoie rien ou si le passage est plus long. Aucun appel au-delà de [MAX_CHARACTERS].
     */
    suspend fun translateSelection(selection: String, translator: Translator): TranslationResult {
        val cleaned = clean(selection)
        if (lookupForm(cleaned).isEmpty()) return TranslationResult.Unavailable
        if (cleaned.length > MAX_CHARACTERS) return TranslationResult.TooLong
        val short = isShort(cleaned)
        val sent = if (short) lookupForm(cleaned) else cleaned
        return try {
            val senses = if (short) senses(translator.lookup(sent)) else emptyList()
            if (senses.isNotEmpty()) {
                TranslationResult.Word(senses.first(), senses.drop(1))
            } else {
                val text = translator.translate(sent).trim()
                when {
                    text.isEmpty() -> TranslationResult.Unavailable
                    short -> TranslationResult.Word(text, emptyList())
                    else -> TranslationResult.Passage(text)
                }
            }
        } catch (_: TranslationFailure.Offline) {
            TranslationResult.Offline
        } catch (_: TranslationFailure.Unavailable) {
            TranslationResult.Unavailable
        }
    }
}

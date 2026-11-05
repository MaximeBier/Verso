package com.maximebier.verso.core.translation

/**
 * Service de traduction, de l’anglais vers le français (V3). L’interface ne connaît pas le service : DeepL dans l’app,
 * un faux traducteur dans les tests. Les échecs passent par [TranslationFailure].
 */
interface Translator {
    /**
     * Une seule traduction de [text]. [context] (la phrase autour de la sélection) oriente vers le sens du passage ; il
     * n’est pas traduit.
     */
    suspend fun translate(text: String, context: String): String
}

/** Échec d’un appel : [Offline] sans réseau, [Unavailable] pour tout le reste (quota épuisé, réponse invalide…). */
sealed class TranslationFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Offline(cause: Throwable? = null) : TranslationFailure("Pas de connexion", cause)
    class Unavailable(message: String, cause: Throwable? = null) : TranslationFailure(message, cause)
}

/** Ce que montre la feuille de traduction une fois la réponse arrivée. */
sealed interface TranslationResult {
    /** Mot ou expression courte (écran 3.08). */
    data class Word(val text: String) : TranslationResult
    /** Passage plus long (écran 3.09). */
    data class Passage(val text: String) : TranslationResult
    data object Offline : TranslationResult
    data object Unavailable : TranslationResult
    data object TooLong : TranslationResult
}

/** Règles de la traduction de la sélection (V3). */
object Translation {
    /** Au plus ce nombre de mots : présentation d’un mot (3.08) ; au-delà, d’un passage (3.09). */
    const val MAX_SHORT_WORDS = 3

    /** Au-delà, rien n’est envoyé : « Sélectionnez un passage plus court pour le traduire. » */
    const val MAX_CHARACTERS = 1_000

    /** Texte pris de chaque côté de la sélection pour le contexte ; Readium 3.4.0 en fournit 200 au plus. */
    const val CONTEXT_SIDE_CHARACTERS = 200

    private val whitespace = Regex("\\s+")

    /**
     * Fin de phrase (ponctuation finale, guillemets ou parenthèses fermants compris, puis une espace ou une majuscule
     * collée : paragraphes sans saut de ligne dans un EPUB compact) ou de paragraphe.
     */
    private val sentenceEnd = Regex("[.!?…][”\"’»)\\]]*(?=\\s|$|\\p{Lu})|\\n")

    /** Sélection qui finit sa phrase : rien de la phrase suivante dans le contexte. */
    private val endsSentence = Regex("[.!?…][”\"’»)\\]]*\\s*$")

    /** Espaces ramenées à une seule, aux bouts comprises. */
    fun clean(selection: String): String = selection.replace(whitespace, " ").trim()

    /** Forme envoyée pour un mot ou une expression courte : ponctuation et guillemets ôtés aux deux bouts. */
    fun shortForm(selection: String): String = clean(selection).trim { !it.isLetterOrDigit() }

    /** Mot ou expression de [MAX_SHORT_WORDS] mots au plus. */
    fun isShort(selection: String): Boolean {
        val words = shortForm(selection)
        return words.isNotEmpty() && words.split(' ').size <= MAX_SHORT_WORDS
    }

    /**
     * La phrase où se trouve la sélection : de [before], ce qui suit la dernière fin de phrase ou de paragraphe ; de
     * [after], ce qui précède la première, ponctuation comprise. Sans fin de phrase, tout le texte fourni, borné à
     * [CONTEXT_SIDE_CHARACTERS] de chaque côté. Une abréviation (« Mr. ») passe pour une fin de phrase.
     */
    fun context(before: String?, selection: String, after: String?): String {
        val head = before.orEmpty().takeLast(CONTEXT_SIDE_CHARACTERS).let { text ->
            sentenceEnd.findAll(text).lastOrNull()?.let { text.substring(it.range.last + 1) } ?: text
        }
        val tail = if (endsSentence.containsMatchIn(selection)) {
            ""
        } else {
            after.orEmpty().take(CONTEXT_SIDE_CHARACTERS).let { text ->
                sentenceEnd.find(text)?.let { text.substring(0, it.range.last + 1) } ?: text
            }
        }
        return clean(head + selection + tail)
    }

    /**
     * Traduit [selection] avec son [context] : sans la ponctuation des bouts pour un mot ou une expression courte, telle
     * quelle sinon. Aucun appel au-delà de [MAX_CHARACTERS].
     */
    suspend fun translateSelection(selection: String, context: String, translator: Translator): TranslationResult {
        val cleaned = clean(selection)
        if (shortForm(cleaned).isEmpty()) return TranslationResult.Unavailable
        if (cleaned.length > MAX_CHARACTERS) return TranslationResult.TooLong
        val short = isShort(cleaned)
        return try {
            val text = translator.translate(if (short) shortForm(cleaned) else cleaned, context).trim()
            when {
                text.isEmpty() -> TranslationResult.Unavailable
                short -> TranslationResult.Word(text)
                else -> TranslationResult.Passage(text)
            }
        } catch (_: TranslationFailure.Offline) {
            TranslationResult.Offline
        } catch (_: TranslationFailure.Unavailable) {
            TranslationResult.Unavailable
        }
    }
}

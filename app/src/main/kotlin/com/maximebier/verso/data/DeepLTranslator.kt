package com.maximebier.verso.data

import com.maximebier.verso.core.translation.TranslationFailure
import com.maximebier.verso.core.translation.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * DeepL API (offre Free), seul accès réseau de Verso : le passage sélectionné et la phrase qui l’entoure partent, rien
 * d’autre. Appel `v2/translate`, de l’anglais vers le français, 10 s au plus. Aucun cache. Le contexte n’est ni traduit
 * ni décompté du quota.
 */
class DeepLTranslator(
    private val key: String,
    private val endpoint: String = endpointFor(key),
) : Translator {

    override suspend fun translate(text: String, context: String): String = parse(post(requestBody(text, context)))

    private suspend fun post(json: String): String = withContext(Dispatchers.IO) {
        val body = json.toByteArray(Charsets.UTF_8)
        val connection = try {
            URL("$endpoint/v2/translate").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw TranslationFailure.Unavailable("Adresse du service invalide", e)
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Authorization", "DeepL-Auth-Key $key")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            // 403 clé refusée, 429 trop de requêtes, 456 quota épuisé… : le service est indisponible.
            val status = connection.responseCode
            if (status !in 200..299) throw TranslationFailure.Unavailable("HTTP $status")
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: TranslationFailure) {
            throw e
        } catch (e: IOException) {
            throw if (e.isOffline()) TranslationFailure.Offline(e) else TranslationFailure.Unavailable("Réseau", e)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FREE_ENDPOINT = "https://api-free.deepl.com"
        const val PRO_ENDPOINT = "https://api.deepl.com"

        /** Délai d’attente de la connexion, puis de la réponse. */
        const val TIMEOUT_MS = 10_000

        private val json = Json { ignoreUnknownKeys = true }

        /** Les clés de l’offre gratuite finissent par `:fx` et n’ont accès qu’à son adresse. */
        fun endpointFor(key: String): String = if (key.endsWith(":fx")) FREE_ENDPOINT else PRO_ENDPOINT

        internal fun requestBody(text: String, context: String): String = buildJsonObject {
            putJsonArray("text") { add(text) }
            put("source_lang", "EN")
            put("target_lang", "FR")
            if (context.isNotBlank()) put("context", context)
        }.toString()

        private fun IOException.isOffline(): Boolean =
            this is UnknownHostException || this is ConnectException || this is NoRouteToHostException || this is SocketTimeoutException

        /** Texte de la première traduction ; réponse inattendue : le service est considéré comme indisponible. */
        internal fun parse(body: String): String = try {
            json.parseToJsonElement(body).jsonObject.getValue("translations").jsonArray.first()
                .jsonObject.getValue("text").jsonPrimitive.content
        } catch (e: SerializationException) {
            throw TranslationFailure.Unavailable("Réponse invalide", e)
        } catch (e: IllegalArgumentException) {
            throw TranslationFailure.Unavailable("Réponse invalide", e)
        } catch (e: NoSuchElementException) {
            throw TranslationFailure.Unavailable("Réponse invalide", e)
        }
    }
}

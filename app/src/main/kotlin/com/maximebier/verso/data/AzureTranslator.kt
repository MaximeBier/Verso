package com.maximebier.verso.data

import com.maximebier.verso.core.translation.TranslationFailure
import com.maximebier.verso.core.translation.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Microsoft Translator (Azure AI Translator, offre F0), seul accès réseau de Verso : le passage sélectionné part, rien
 * d’autre. Appels `dictionary/lookup` et `translate`, de l’anglais vers le français, 10 s au plus. Aucun cache.
 */
class AzureTranslator(
    private val key: String,
    private val region: String,
    private val endpoint: String = ENDPOINT,
) : Translator {

    override suspend fun lookup(word: String): List<String> = parseLookup(post("dictionary/lookup", word))

    override suspend fun translate(text: String): String = parseTranslate(post("translate", text))

    private suspend fun post(path: String, text: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonArray { add(buildJsonObject { put("Text", text) }) }.toString().toByteArray(Charsets.UTF_8)
        val connection = try {
            URL("$endpoint/$path?api-version=3.0&from=en&to=fr").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw TranslationFailure.Unavailable("Adresse du service invalide", e)
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", key)
            if (region.isNotBlank()) connection.setRequestProperty("Ocp-Apim-Subscription-Region", region)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
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
        const val ENDPOINT = "https://api.cognitive.microsofttranslator.com"

        /** Délai d’attente de la connexion, puis de la réponse. */
        const val TIMEOUT_MS = 10_000

        private val json = Json { ignoreUnknownKeys = true }

        private fun IOException.isOffline(): Boolean =
            this is UnknownHostException || this is ConnectException || this is NoRouteToHostException || this is SocketTimeoutException

        /** Traductions du premier terme, par confiance décroissante (ordre reçu à confiance égale). */
        internal fun parseLookup(body: String): List<String> = parsing {
            val entry = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject ?: return@parsing emptyList()
            val translations = entry["translations"]?.jsonArray ?: JsonArray(emptyList())
            translations
                .map { it.jsonObject }
                .sortedByDescending { it.confidence() }
                .mapNotNull { it["displayTarget"]?.jsonPrimitive?.content }
        }

        internal fun parseTranslate(body: String): String = parsing {
            val entry = json.parseToJsonElement(body).jsonArray.first().jsonObject
            entry.getValue("translations").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
        }

        private fun JsonObject.confidence(): Double = this["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0

        /** Réponse inattendue : le service est considéré comme indisponible. */
        private inline fun <T> parsing(block: () -> T): T = try {
            block()
        } catch (e: SerializationException) {
            throw TranslationFailure.Unavailable("Réponse invalide", e)
        } catch (e: IllegalArgumentException) {
            throw TranslationFailure.Unavailable("Réponse invalide", e)
        } catch (e: NoSuchElementException) {
            throw TranslationFailure.Unavailable("Réponse invalide", e)
        }
    }
}

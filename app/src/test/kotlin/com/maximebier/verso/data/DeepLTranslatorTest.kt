package com.maximebier.verso.data

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.translation.TranslationFailure
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Test

class DeepLTranslatorTest {
    @Test fun freeKeysGoToTheFreeEndpoint() {
        assertThat(DeepLTranslator.endpointFor("4dd862de-0000:fx")).isEqualTo("https://api-free.deepl.com")
        assertThat(DeepLTranslator.endpointFor("4dd862de-0000")).isEqualTo("https://api.deepl.com")
    }

    @Test fun requestSendsTheTextTheLanguagesAndTheContext() {
        val body = Json.parseToJsonElement(DeepLTranslator.requestBody("bank", "They sat on the bank of the river.")).jsonObject
        assertThat(body.getValue("text").jsonArray.map { it.jsonPrimitive.content }).containsExactly("bank")
        assertThat(body.getValue("source_lang").jsonPrimitive.content).isEqualTo("EN")
        assertThat(body.getValue("target_lang").jsonPrimitive.content).isEqualTo("FR")
        assertThat(body.getValue("context").jsonPrimitive.content).isEqualTo("They sat on the bank of the river.")
    }

    @Test fun blankContextIsNotSent() {
        val body = Json.parseToJsonElement(DeepLTranslator.requestBody("bank", " ")).jsonObject
        assertThat(body).doesNotContainKey("context")
    }

    @Test fun answerReadsTheFirstText() {
        val body = """{"translations":[{"detected_source_language":"EN","text":"berge"}]}"""
        assertThat(DeepLTranslator.parse(body)).isEqualTo("berge")
    }

    @Test fun malformedAnswersMeanUnavailable() {
        assertThrows(TranslationFailure.Unavailable::class.java) { DeepLTranslator.parse("""{"message":"Quota Exceeded"}""") }
        assertThrows(TranslationFailure.Unavailable::class.java) { DeepLTranslator.parse("""{"translations":[]}""") }
        assertThrows(TranslationFailure.Unavailable::class.java) { DeepLTranslator.parse("pas du json") }
    }

    @Test fun unreachableHostMeansOffline() = runTest {
        // Adresse réservée (RFC 2606) : la résolution échoue sans rien envoyer.
        val translator = DeepLTranslator(key = "clé:fx", endpoint = "https://verso.invalid")
        val failure = runCatching { translator.translate("word", "a word") }.exceptionOrNull()
        assertThat(failure).isInstanceOf(TranslationFailure.Offline::class.java)
    }
}

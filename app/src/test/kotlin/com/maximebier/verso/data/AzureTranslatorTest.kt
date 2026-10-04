package com.maximebier.verso.data

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.translation.TranslationFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class AzureTranslatorTest {
    @Test fun lookupKeepsTheTranslationsByDecreasingConfidence() {
        val body = """
            [{"normalizedSource":"acknowledged","displaySource":"acknowledged","translations":[
              {"normalizedTarget":"admis","displayTarget":"admis","posTag":"VERB","confidence":0.21},
              {"normalizedTarget":"reconnu","displayTarget":"reconnu","posTag":"VERB","confidence":0.48},
              {"normalizedTarget":"avoué","displayTarget":"avoué","posTag":"ADJ","confidence":0.09}
            ]}]
        """.trimIndent()
        assertThat(AzureTranslator.parseLookup(body)).containsExactly("reconnu", "admis", "avoué").inOrder()
    }

    @Test fun lookupWithoutTranslationsIsEmpty() {
        assertThat(AzureTranslator.parseLookup("""[{"normalizedSource":"netherfield","translations":[]}]""")).isEmpty()
        assertThat(AzureTranslator.parseLookup("[]")).isEmpty()
    }

    @Test fun translateReadsTheFirstText() {
        val body = """[{"translations":[{"text":"C’est une vérité.","to":"fr"}]}]"""
        assertThat(AzureTranslator.parseTranslate(body)).isEqualTo("C’est une vérité.")
    }

    @Test fun malformedAnswersMeanUnavailable() {
        assertThrows(TranslationFailure.Unavailable::class.java) { AzureTranslator.parseTranslate("""{"error":{"code":401000}}""") }
        assertThrows(TranslationFailure.Unavailable::class.java) { AzureTranslator.parseTranslate("[]") }
        assertThrows(TranslationFailure.Unavailable::class.java) { AzureTranslator.parseLookup("pas du json") }
    }

    @Test fun unreachableHostMeansOffline() = runTest {
        // Adresse réservée (RFC 2606) : la résolution échoue sans rien envoyer.
        val translator = AzureTranslator(key = "clé", region = "francecentral", endpoint = "https://verso.invalid")
        val failure = runCatching { translator.translate("word") }.exceptionOrNull()
        assertThat(failure).isInstanceOf(TranslationFailure.Offline::class.java)
    }
}

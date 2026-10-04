package com.maximebier.verso.core.translation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TranslationTest {
    private class FakeTranslator(
        private val senses: List<String> = emptyList(),
        private val passage: String = "",
        private val failure: TranslationFailure? = null,
    ) : Translator {
        val lookups = mutableListOf<String>()
        val translations = mutableListOf<String>()

        override suspend fun lookup(word: String): List<String> {
            lookups += word
            failure?.let { throw it }
            return senses
        }

        override suspend fun translate(text: String): String {
            translations += text
            failure?.let { throw it }
            return passage
        }
    }

    @Test fun shortMeansThreeWordsAtMostWithoutEdgePunctuation() {
        assertThat(Translation.isShort("acknowledged,")).isTrue()
        assertThat(Translation.isShort("  in want  of ")).isTrue()
        assertThat(Translation.isShort("in want of a")).isFalse()
        assertThat(Translation.isShort("well-known")).isTrue()
        assertThat(Translation.isShort("« … »")).isFalse()
    }

    @Test fun lookupFormDropsEdgePunctuationAndQuotes() {
        assertThat(Translation.lookupForm("“acknowledged,”")).isEqualTo("acknowledged")
        assertThat(Translation.lookupForm("Mr. Bennet")).isEqualTo("Mr. Bennet")
    }

    @Test fun sensesAreDistinctIgnoringCaseAndCappedAtFour() {
        assertThat(Translation.senses(listOf("reconnu", "Reconnu", " ", "admis", "avoué", "accepté", "connu")))
            .containsExactly("reconnu", "admis", "avoué", "accepté").inOrder()
    }

    @Test fun wordUsesTheDictionary() = runTest {
        val translator = FakeTranslator(senses = listOf("reconnu", "admis", "avoué", "accepté", "connu"))
        val result = Translation.translateSelection("acknowledged,", translator)
        assertThat(result).isEqualTo(TranslationResult.Word("reconnu", listOf("admis", "avoué", "accepté")))
        assertThat(translator.lookups).containsExactly("acknowledged")
        assertThat(translator.translations).isEmpty()
    }

    @Test fun emptyDictionaryFallsBackToTranslate() = runTest {
        val translator = FakeTranslator(passage = "Netherfield")
        val result = Translation.translateSelection("Netherfield", translator)
        assertThat(result).isEqualTo(TranslationResult.Word("Netherfield", emptyList()))
        assertThat(translator.translations).containsExactly("Netherfield")
    }

    @Test fun longPassageIsTranslatedOnceWithItsPunctuation() = runTest {
        val translator = FakeTranslator(passage = "C’est une vérité universellement reconnue.")
        val result = Translation.translateSelection("It is a truth\nuniversally acknowledged.", translator)
        assertThat(result).isEqualTo(TranslationResult.Passage("C’est une vérité universellement reconnue."))
        assertThat(translator.lookups).isEmpty()
        assertThat(translator.translations).containsExactly("It is a truth universally acknowledged.")
    }

    @Test fun overTheLimitNothingIsSent() = runTest {
        val translator = FakeTranslator(passage = "x")
        val atLimit = "a ".repeat(Translation.MAX_CHARACTERS / 2).trim() + "b"
        assertThat(atLimit.length).isEqualTo(Translation.MAX_CHARACTERS)
        assertThat(Translation.translateSelection(atLimit, translator)).isInstanceOf(TranslationResult.Passage::class.java)
        assertThat(Translation.translateSelection(atLimit + "c", translator)).isEqualTo(TranslationResult.TooLong)
        assertThat(translator.translations).hasSize(1)
    }

    @Test fun failuresBecomeStates() = runTest {
        assertThat(Translation.translateSelection("word", FakeTranslator(failure = TranslationFailure.Offline())))
            .isEqualTo(TranslationResult.Offline)
        assertThat(Translation.translateSelection("a long passage here", FakeTranslator(failure = TranslationFailure.Unavailable("429"))))
            .isEqualTo(TranslationResult.Unavailable)
        assertThat(Translation.translateSelection("a long passage here", FakeTranslator(passage = " ")))
            .isEqualTo(TranslationResult.Unavailable)
    }
}

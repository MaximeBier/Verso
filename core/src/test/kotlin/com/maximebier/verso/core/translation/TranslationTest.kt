package com.maximebier.verso.core.translation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TranslationTest {
    private class FakeTranslator(
        private val answer: String = "",
        private val failure: TranslationFailure? = null,
    ) : Translator {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun translate(text: String, context: String): String {
            calls += text to context
            failure?.let { throw it }
            return answer
        }
    }

    @Test fun shortMeansThreeWordsAtMostWithoutEdgePunctuation() {
        assertThat(Translation.isShort("acknowledged,")).isTrue()
        assertThat(Translation.isShort("  in want  of ")).isTrue()
        assertThat(Translation.isShort("in want of a")).isFalse()
        assertThat(Translation.isShort("well-known")).isTrue()
        assertThat(Translation.isShort("« … »")).isFalse()
    }

    @Test fun shortFormDropsEdgePunctuationAndQuotes() {
        assertThat(Translation.shortForm("“acknowledged,”")).isEqualTo("acknowledged")
        assertThat(Translation.shortForm("Mr. Bennet")).isEqualTo("Mr. Bennet")
    }

    @Test fun contextIsTheSentenceAroundTheSelection() {
        val context = Translation.context(
            before = "be in want of a wife.\n\nThey sat on the ",
            selection = "bank",
            after = " of the river and watched the boats. However little known the feelings",
        )
        assertThat(context).isEqualTo("They sat on the bank of the river and watched the boats.")
    }

    @Test fun contextStopsAtAParagraphBreak() {
        val context = Translation.context(before = "a heading\nIt is a truth universally ", selection = "acknowledged", after = ", that a single man\nChapter 2")
        assertThat(context).isEqualTo("It is a truth universally acknowledged, that a single man")
    }

    @Test fun contextStopsBetweenParagraphsGluedTogether() {
        // EPUB compact : `</p><p>` sans espace, Readium colle les paragraphes.
        assertThat(Translation.context("in want of a wife.They sat on the ", "bank", " of the river.However little"))
            .isEqualTo("They sat on the bank of the river.")
    }

    @Test fun contextKeepsClosingQuotesAndHandlesOtherSentenceEnds() {
        assertThat(Translation.context("“Is he married?” She ", "smiled", ". “Oh! single”"))
            .isEqualTo("She smiled.")
        assertThat(Translation.context("He waited… And ", "waited", " again… Then left."))
            .isEqualTo("And waited again…")
    }

    @Test fun selectionAtTheEdgesOfASentence() {
        // La sélection commence la phrase : rien de la phrase d'avant.
        assertThat(Translation.context("must be in want of a wife. ", "However", " little known the feelings."))
            .isEqualTo("However little known the feelings.")
        // La sélection finit la phrase : rien de la phrase d'après.
        assertThat(Translation.context("must be in want of a ", "wife.", " However little known."))
            .isEqualTo("must be in want of a wife.")
    }

    @Test fun abbreviationsCutTheSentence() {
        // Coupe acceptée : « Mr. » passe pour une fin de phrase, le contexte reste utile.
        assertThat(Translation.context("“My dear Mr. ", "Bennet", ",” said his lady to him one day."))
            .isEqualTo("Bennet,” said his lady to him one day.")
    }

    @Test fun withoutSurroundingTextTheContextIsTheSelection() {
        assertThat(Translation.context(null, " bank ", null)).isEqualTo("bank")
        assertThat(Translation.context("", "bank", "")).isEqualTo("bank")
    }

    @Test fun contextTakesAtMostTheSideLimitOnEachSide() {
        val side = Translation.CONTEXT_SIDE_CHARACTERS
        val context = Translation.context("x".repeat(side + 50), "bank", "y".repeat(side + 50))
        assertThat(context).isEqualTo("x".repeat(side) + "bank" + "y".repeat(side))
    }

    @Test fun shortSelectionIsSentWithoutEdgePunctuationAlongWithItsContext() = runTest {
        val translator = FakeTranslator(answer = "berge")
        val result = Translation.translateSelection("bank,", "They sat on the bank, by the river.", translator)
        assertThat(result).isEqualTo(TranslationResult.Word("berge"))
        assertThat(translator.calls).containsExactly("bank" to "They sat on the bank, by the river.")
    }

    @Test fun longPassageIsTranslatedOnceWithItsPunctuation() = runTest {
        val translator = FakeTranslator(answer = "C’est une vérité universellement reconnue.")
        val result = Translation.translateSelection("It is a truth\nuniversally acknowledged.", "It is a truth universally acknowledged.", translator)
        assertThat(result).isEqualTo(TranslationResult.Passage("C’est une vérité universellement reconnue."))
        assertThat(translator.calls.single().first).isEqualTo("It is a truth universally acknowledged.")
    }

    @Test fun overTheLimitNothingIsSent() = runTest {
        val translator = FakeTranslator(answer = "x")
        val atLimit = "a ".repeat(Translation.MAX_CHARACTERS / 2).trim() + "b"
        assertThat(atLimit.length).isEqualTo(Translation.MAX_CHARACTERS)
        assertThat(Translation.translateSelection(atLimit, atLimit, translator)).isInstanceOf(TranslationResult.Passage::class.java)
        assertThat(Translation.translateSelection(atLimit + "c", atLimit + "c", translator)).isEqualTo(TranslationResult.TooLong)
        assertThat(translator.calls).hasSize(1)
    }

    @Test fun failuresBecomeStates() = runTest {
        assertThat(Translation.translateSelection("word", "", FakeTranslator(failure = TranslationFailure.Offline())))
            .isEqualTo(TranslationResult.Offline)
        assertThat(Translation.translateSelection("a long passage here", "", FakeTranslator(failure = TranslationFailure.Unavailable("456"))))
            .isEqualTo(TranslationResult.Unavailable)
        assertThat(Translation.translateSelection("a long passage here", "", FakeTranslator(answer = " ")))
            .isEqualTo(TranslationResult.Unavailable)
        assertThat(Translation.translateSelection("« … »", "", FakeTranslator(answer = "x")))
            .isEqualTo(TranslationResult.Unavailable)
    }
}

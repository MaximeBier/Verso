package com.maximebier.verso.ui.reader

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.translation.TranslationResult
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSheetTest {
    @get:Rule val rule = createComposeRule()

    private var retries = 0
    private var dismissals = 0

    private fun show(state: TranslationSheetState) = rule.setContent {
        VersoTheme(theme = AppTheme.LIGHT) {
            TranslationSheet(state = state, onRetry = { retries++ }, onDismiss = { dismissals++ })
        }
    }

    @Test fun wordShowsTheMainTranslationAndTheOtherSenses() {
        show(TranslationSheetState("acknowledged,", short = true, TranslationResult.Word("reconnu", listOf("admis", "avoué", "accepté"))))
        rule.onNodeWithText("acknowledged").assertExists()
        rule.onNodeWithText("reconnu").assertExists()
        rule.onNodeWithText("admis · avoué · accepté").assertExists()
        rule.onNodeWithText("Anglais → Français").assertExists()
        rule.onNodeWithContentDescription("Fermer").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(dismissals).isEqualTo(1)
    }

    @Test fun passageShowsOneTranslationUnderTraduction() {
        show(TranslationSheetState("It is a truth universally acknowledged.", short = false, TranslationResult.Passage("C’est une vérité universellement reconnue.")))
        rule.onNodeWithText("Traduction").assertExists()
        rule.onNodeWithText("C’est une vérité universellement reconnue.").assertExists()
    }

    @Test fun loadingShowsThePassageAndAnIndicator() {
        show(TranslationSheetState("acknowledged", short = true, result = null))
        rule.onNodeWithText("acknowledged").assertExists()
        rule.onNodeWithContentDescription("Traduction en cours").assertExists()
    }

    @Test fun offlineOffersRetryWithoutTheLanguages() {
        show(TranslationSheetState("acknowledged", short = true, TranslationResult.Offline))
        rule.onNodeWithText("Pas de connexion").assertExists()
        rule.onNodeWithText("La traduction a besoin d’Internet. Le reste de Verso fonctionne sans.").assertExists()
        rule.onNodeWithText("Anglais → Français").assertDoesNotExist()
        rule.onNodeWithText("Réessayer").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(retries).isEqualTo(1)
    }

    @Test fun unavailableAndTooLong() {
        show(TranslationSheetState("word", short = true, TranslationResult.Unavailable))
        rule.onNodeWithText("La traduction n’est pas disponible pour le moment.").assertExists()
        rule.onNodeWithText("Réessayer").assertExists()
    }

    @Test fun tooLongHasNoRetry() {
        show(TranslationSheetState("long", short = false, TranslationResult.TooLong))
        rule.onNodeWithText("Sélectionnez un passage plus court pour le traduire.").assertExists()
        rule.onNodeWithText("Réessayer").assertDoesNotExist()
    }

    @Test fun swipeDownCloses() {
        show(TranslationSheetState("acknowledged", short = true, TranslationResult.Word("reconnu", emptyList())))
        rule.onNodeWithText("reconnu").performTouchInput { swipeDown(startY = centerY, endY = centerY + 600f) }
        rule.waitForIdle()
        assertThat(dismissals).isEqualTo(1)
    }
}

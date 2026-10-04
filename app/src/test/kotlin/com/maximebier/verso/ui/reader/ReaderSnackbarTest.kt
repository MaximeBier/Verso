package com.maximebier.verso.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.components.VersoSnackbarHost
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSnackbarTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun undoOnTopOfTheReaderBarsIsReachable() {
        var result: SnackbarResult? = null
        var settings = 0
        val host = SnackbarHostState()
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    ReaderBars(
                        visible = true,
                        state = ReaderBarsState("Titre", emptyList(), 5, 0.05f, 60),
                        onBack = {}, onTocClick = {}, onSearchClick = {},
                        onSettingsClick = { settings++ },
                    )
                    VersoSnackbarHost(
                        hostState = host,
                        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
                    )
                }
            }
            LaunchedEffect(Unit) { result = host.showSnackbar("Note supprimée", actionLabel = "Annuler", duration = SnackbarDuration.Long) }
        }
        rule.onNodeWithText("Annuler").performClick()
        rule.waitForIdle()
        assertThat(result).isEqualTo(SnackbarResult.ActionPerformed)
        assertThat(settings).isEqualTo(0)
    }
}

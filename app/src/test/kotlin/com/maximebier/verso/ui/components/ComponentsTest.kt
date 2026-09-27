package com.maximebier.verso.ui.components

import com.maximebier.verso.data.AppTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun segmentedButtonExposesSelectionAndReportsClicks() {
        var selected = 0
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                VersoSegmentedButton(
                    options = listOf("Récents", "Titre", "Auteur"),
                    selectedIndex = 0,
                    onSelect = { selected = it },
                    groupLabel = "Trier par",
                )
            }
        }
        compose.onNodeWithText("Récents").assertIsSelected()
        compose.onNodeWithText("Titre").assertIsNotSelected()
        compose.onNodeWithText("Auteur").performClick()
        assertThat(selected).isEqualTo(2)
    }

    @Test
    fun progressBarExposesItsFraction() {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) { VersoProgressBar(fraction = 0.31f, current = true) }
        }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.31f, 0f..1f))).assertExists()
    }

    @Test
    fun iconButtonHasItsTalkBackLabel() {
        var clicks = 0
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) {
                VersoIconButton(icon = VersoIcons.Settings, contentDescription = "Paramètres", onClick = { clicks++ })
            }
        }
        compose.onNodeWithContentDescription("Paramètres").performClick()
        assertThat(clicks).isEqualTo(1)
    }
}

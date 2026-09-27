package com.maximebier.verso.ui.details

import com.maximebier.verso.data.AppTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Revue REV-080 : TalkBack annonce le libellé du champ, même vide (« Auteur, zone d’édition »). */
@RunWith(AndroidJUnit4::class)
class DetailsFieldsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyFieldIsAnnouncedWithItsLabel() {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT) { DetailsTextField(value = "", onValueChange = {}, label = "Auteur") }
        }

        compose.onNode(hasSetTextAction() and hasContentDescription("Auteur")).assertExists()
    }
}

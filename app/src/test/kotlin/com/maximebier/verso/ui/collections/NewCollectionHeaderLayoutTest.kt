package com.maximebier.verso.ui.collections

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** En-tête de « Nouvelle collection », mesuré avec le vrai rendu du texte sur l’écran du téléphone (390 dp). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class NewCollectionHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun titleLines(): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.new_collection_title)).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.single().lineCount
    }

    private fun createRightEdgeGap() = with(compose.density) {
        val create = compose.onNodeWithText(context.getString(R.string.new_collection_create)).fetchSemanticsNode().boundsInRoot
        (compose.onRoot().fetchSemanticsNode().boundsInRoot.right - create.right).toDp()
    }

    @Test
    fun titleFitsOnOneLineWithoutName() {
        compose.setContent { VersoTheme { NewCollectionContent(NewCollectionUiState(loading = false), NewCollectionActions()) } }
        assertThat(titleLines()).isEqualTo(1)
    }

    @Test
    fun titleFitsOnOneLineWithANameAndCreateSitsEightDpFromTheEdge() {
        compose.setContent {
            VersoTheme { NewCollectionContent(NewCollectionUiState(loading = false, name = "Les Mousquetaires"), NewCollectionActions()) }
        }
        assertThat(titleLines()).isEqualTo(1)
        assertThat(createRightEdgeGap().value).isWithin(1f).of(8.dp.value)
    }
}

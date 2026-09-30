package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.reader.PageInfo
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageFooterTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun show(chapter: String?, pageInfo: PageInfo?, onTurn: ((Boolean) -> Unit)? = null, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                    Box(Modifier.width(390.dp)) { PageFooter(chapter = chapter, pageInfo = pageInfo, onTurn = onTurn) }
                }
            }
        }
    }

    /** À 200 %, le chapitre passe sur toute la largeur, sans coupure, et la page en dessous. */
    @Test
    fun atTwoHundredPercentTheChapterIsNotCut() {
        show(chapter = "Deuxième partie, chapitre I", pageInfo = PageInfo(12, 240), fontScale = 2f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Deuxième partie, chapitre I", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts.single().multiParagraph.didExceedMaxLines).isFalse()
        compose.onNodeWithText("Page 12 sur 240", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun withoutTextTheFooterHasNoTalkBackActions() {
        show(chapter = null, pageInfo = null, onTurn = {})
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).assertCountEquals(0)
    }

    @Test
    fun showsChapterAndPageOfChapter() {
        show(chapter = "Deuxième partie, chapitre I", pageInfo = PageInfo(2, 9))
        compose.onNodeWithText("Deuxième partie, chapitre I").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reader_page_of, 2, 9)).assertIsDisplayed()
        compose.onNodeWithText("Page 2 sur 9").assertIsDisplayed()
    }

    @Test
    fun withoutPageCountOnlyTheChapterIsShown() {
        show(chapter = "Préface", pageInfo = null)
        compose.onNodeWithText("Préface").assertIsDisplayed()
        compose.onNodeWithText("Page", substring = true).assertDoesNotExist()
    }

    @Test
    fun talkBackReadsOneFocusableFooterThatTurnsPages() {
        val turns = mutableListOf<Boolean>()
        show(chapter = "Deuxième partie, chapitre I", pageInfo = PageInfo(1, 1), onTurn = { turns += it })

        // Un seul nœud fusionné : TalkBack s’y arrête et lit le chapitre puis « Page 1 sur 1 ».
        val node = compose.onNodeWithText("Page 1 sur 1").fetchSemanticsNode()
        assertThat(node.config.isMergingSemanticsOfDescendants).isTrue()
        assertThat(node.config[SemanticsProperties.Text].map { it.text })
            .containsExactly("Deuxième partie, chapitre I", "Page 1 sur 1").inOrder()

        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertThat(actions.map { it.label }).containsExactly(
            context.getString(R.string.reader_page_next),
            context.getString(R.string.reader_page_previous),
        ).inOrder()
        compose.runOnIdle {
            actions[0].action()
            actions[1].action()
        }
        assertThat(turns).containsExactly(true, false).inOrder()
    }
}

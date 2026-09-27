package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

@RunWith(AndroidJUnit4::class)
class PageFooterTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun show(chapter: String?, pageInfo: PageInfo?, onTurn: ((Boolean) -> Unit)? = null) {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                PageFooter(chapter = chapter, pageInfo = pageInfo, onTurn = onTurn)
            }
        }
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

package com.maximebier.verso.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.ui.details.SizeParts
import com.maximebier.verso.ui.theme.VersoTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class BackupScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private var state by mutableStateOf(BackupUiState())
    private val calls = mutableListOf<String>()
    private val actions = BackupActions(
        onBack = { calls += "retour" },
        onCreate = { calls += "créer" },
        onRestore = { calls += "restaurer" },
        onRestoreConfirm = { calls += "remplacer" },
        onRestoreDismiss = { calls += "annuler" },
    )
    private val at = LocalDateTime.of(2026, 9, 20, 21, 14).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun show() = composeRule.setContent { VersoTheme { BackupScreen(state = state, actions = actions) } }

    @Test
    fun cardShowsDateSizeAndNameOfTheLastBackup() {
        state = BackupUiState(lastBackup = LastBackup(at, 48_000_000L, "verso-sauvegarde-2026-09-20.zip"))
        show()

        composeRule.onNodeWithText(ctx.getString(R.string.backup_intro)).assertExists()
        composeRule.onNodeWithText("Dernière sauvegarde").assertExists()
        composeRule.onNodeWithText("20 septembre 2026, 21 h 14").assertExists()
        composeRule.onNodeWithText("48 Mo · " + breakableFileName("verso-sauvegarde-2026-09-20.zip")).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restore_warning)).assertExists()
    }

    @Test
    fun withoutBackupTheCardSaysSo() {
        show()
        composeRule.onNodeWithText("Aucune sauvegarde pour l’instant").assertExists()
    }

    @Test
    fun buttonsCallTheirActionsAndAreAtLeast48dp() {
        show()
        composeRule.onNodeWithText("Créer une sauvegarde").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Restaurer une sauvegarde").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(calls).containsExactly("créer", "restaurer").inOrder()
    }

    @Test
    fun whileBusyButtonsAreDisabledAndProgressIsShown() {
        state = BackupUiState(busy = BackupBusy.RESTORING)
        show()

        composeRule.onNodeWithText("Créer une sauvegarde").assertIsNotEnabled()
        composeRule.onNodeWithText("Restaurer une sauvegarde").assertIsNotEnabled()
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restoring)).assertExists()
    }

    @Test
    fun confirmationGivesBothCountsAndReplacesOnlyOnConfirm() {
        state = BackupUiState(confirmation = RestoreConfirmation(currentBooks = 1, backupBooks = 3))
        show()

        val current = ctx.resources.getQuantityString(R.plurals.library_book_count, 1, 1)
        val backup = ctx.resources.getQuantityString(R.plurals.library_book_count, 3, 3)
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restore_dialog_title)).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restore_dialog_body, current, backup)).assertExists()
        composeRule.onNodeWithText("Annuler").performClick()
        composeRule.onNodeWithText("Remplacer").performClick()
        assertThat(calls).containsExactly("annuler", "remplacer").inOrder()
    }

    @Test
    fun fileNameCanWrapOnlyAfterHyphensAndBeforeTheDot() {
        assertThat(breakableFileName("verso-sauvegarde-2026-09-20.zip"))
            .isEqualTo("verso-​sauvegarde-​2026-​09-​20​.zip")
    }

    @Test
    fun sizeIsWholeFromTenMegabytes() {
        assertThat(backupSizeParts(48_000_000L)).isEqualTo(SizeParts(megabytes = true, value = "48"))
        assertThat(backupSizeParts(47_600_000L)).isEqualTo(SizeParts(megabytes = true, value = "48"))
        assertThat(backupSizeParts(1_200_000L)).isEqualTo(SizeParts(megabytes = true, value = "1,2"))
        assertThat(backupSizeParts(850_000L)).isEqualTo(SizeParts(megabytes = false, value = "850"))
    }
}

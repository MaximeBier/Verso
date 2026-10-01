package com.maximebier.verso.screenshots

import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.screenshots.samples.BackupBusySample
import com.maximebier.verso.screenshots.samples.BackupConfirmationSample
import com.maximebier.verso.screenshots.samples.BackupEmptySample
import com.maximebier.verso.screenshots.samples.BackupSample
import com.maximebier.verso.screenshots.samples.NoteSheetSample
import com.maximebier.verso.screenshots.samples.NotesEmptySample
import com.maximebier.verso.screenshots.samples.NotesSample
import com.maximebier.verso.screenshots.samples.SelectionBarSample

/** Un écran V3 et les palettes où il est capturé (toutes par défaut). */
data class V3ScreenFixture(val screen: ScreenFixture, val themes: List<AppTheme> = AppTheme.entries) {
    override fun toString(): String = screen.id
}

/** Écrans V3 dessinables par Compose (le rendu des surlignages dans Readium est vérifié sur le téléphone). */
object V3ScreenCatalog {
    val fixtures: List<V3ScreenFixture> = listOf(
        V3ScreenFixture(ScreenFixture("3.04-texte-selectionne") { SelectionBarSample() }),
        V3ScreenFixture(ScreenFixture("3.05-ajouter-une-note") { NoteSheetSample() }),
        V3ScreenFixture(ScreenFixture("3.06-notes-et-surlignages") { NotesSample() }),
        V3ScreenFixture(ScreenFixture("3.06-vide") { NotesEmptySample() }),
        V3ScreenFixture(ScreenFixture("3.07-sauvegarde") { BackupSample() }),
        V3ScreenFixture(ScreenFixture("3.07-sauvegarde-aucune") { BackupEmptySample() }),
        V3ScreenFixture(ScreenFixture("3.07-sauvegarde-en-cours") { BackupBusySample() }),
        V3ScreenFixture(ScreenFixture("3.07-restauration-confirmation") { BackupConfirmationSample() }),
    )
}

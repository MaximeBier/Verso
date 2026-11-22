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
import com.maximebier.verso.screenshots.samples.TranslationLoadingSample
import com.maximebier.verso.screenshots.samples.TranslationOfflineSample
import com.maximebier.verso.screenshots.samples.TranslationPassageSample
import com.maximebier.verso.screenshots.samples.TranslationWordSample

/** Un écran V3 et les palettes où il est capturé (toutes par défaut). */
data class V3ScreenFixture(val screen: ScreenFixture, val themes: List<AppTheme> = AppTheme.entries) {
    override fun toString(): String = screen.id
}

/** Écrans V3 dessinables par Compose (le rendu des notes dans Readium est vérifié sur le téléphone). */
object V3ScreenCatalog {
    val fixtures: List<V3ScreenFixture> = listOf(
        V3ScreenFixture(ScreenFixture("Lecture-Selection") { SelectionBarSample() }),
        V3ScreenFixture(ScreenFixture("Lecture-Note") { NoteSheetSample() }),
        V3ScreenFixture(ScreenFixture("Lecture-Notes") { NotesSample() }),
        V3ScreenFixture(ScreenFixture("Lecture-Notes-Vide") { NotesEmptySample() }),
        V3ScreenFixture(ScreenFixture("Parametres-Sauvegarde") { BackupSample() }),
        V3ScreenFixture(ScreenFixture("Parametres-Sauvegarde-Aucune") { BackupEmptySample() }),
        V3ScreenFixture(ScreenFixture("Parametres-Sauvegarde-EnCours") { BackupBusySample() }),
        V3ScreenFixture(ScreenFixture("Parametres-Sauvegarde-Confirmation") { BackupConfirmationSample() }),
        V3ScreenFixture(ScreenFixture("Traduction-Mot") { TranslationWordSample() }),
        V3ScreenFixture(ScreenFixture("Traduction-Chargement") { TranslationLoadingSample() }),
        V3ScreenFixture(ScreenFixture("Traduction-Phrase") { TranslationPassageSample() }),
        V3ScreenFixture(ScreenFixture("Traduction-HorsLigne") { TranslationOfflineSample() }),
    )
}

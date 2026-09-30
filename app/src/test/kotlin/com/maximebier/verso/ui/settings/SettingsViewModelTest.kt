package com.maximebier.verso.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ReadingSettingsLimits
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: VersoDatabase
    private lateinit var books: BookRepository
    private lateinit var sessions: SessionRepository
    private lateinit var settings: SettingsRepository
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, VersoDatabase::class.java).allowMainThreadQueries().build()
        books = BookRepository(db.bookDao(), tmp.newFolder("books"), tmp.newFolder("covers"))
        sessions = SessionRepository(db.sessionDao())
        // DataStore doit tourner sur le même dispatcher de test que le reste (immédiat, sans
        // vrai thread d'IO) : sinon ses lectures/écritures s'exécutent en temps réel hors du
        // contrôle de `runTest`, et le round-trip édition -> réémission peut, rarement, dépasser
        // le délai fixe de Turbine (3s) sous charge — un flake purement lié au test, pas à
        // SettingsViewModel ni à SettingsRepository.
        dataStoreScope = CoroutineScope(testDispatcher + SupervisorJob())
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(tmp.root, "settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    // Fabriques de TestBooks.kt (tâche 2.3).
    private fun sampleBook() = testBook(sha256 = "abc", title = "Madame Bovary", author = "Gustave Flaubert", lastOpenedAt = 0L)

    private fun sampleSession(bookId: Long) = testSession(bookId = bookId, startedAt = 0L)

    @Test
    fun reopenLastBookIsOnByDefaultAndCanBeTurnedOff() = runTest {
        val vm = SettingsViewModel(settings, sessions)

        vm.state.test {
            assertThat(awaitItem().reopenLastBook).isTrue()
            vm.onReopenLastBookChange(false)
            assertThat(awaitItem().reopenLastBook).isFalse()
        }
        assertThat(settings.reopenLastBook.first()).isFalse()
    }

    @Test
    fun clearJournalAsksConfirmationThenErasesSessionsButKeepsPositions() = runTest {
        val bookId = books.insert(sampleBook())
        books.saveReadingPosition(bookId, """{"href":"c2.xhtml"}""", 0.31)
        sessions.upsert(sampleSession(bookId))
        val vm = SettingsViewModel(settings, sessions)

        vm.state.test {
            assertThat(awaitItem().confirmingClearJournal).isFalse()
            vm.onClearJournalClick()
            assertThat(awaitItem().confirmingClearJournal).isTrue()
            vm.onClearJournalConfirm()
            assertThat(awaitItem().confirmingClearJournal).isFalse()
        }

        assertThat(sessions.observeSessions(bookId).first { it.isEmpty() }).isEmpty()
        val book = books.book(bookId)!!
        assertThat(book.readingLocatorJson).isEqualTo("""{"href":"c2.xhtml"}""")
        assertThat(book.progression).isEqualTo(0.31)
    }

    @Test
    fun dismissingConfirmationKeepsTheJournal() = runTest {
        val bookId = books.insert(sampleBook())
        sessions.upsert(sampleSession(bookId))
        val vm = SettingsViewModel(settings, sessions)

        vm.state.test {
            awaitItem()
            vm.onClearJournalClick()
            assertThat(awaitItem().confirmingClearJournal).isTrue()
            vm.onClearJournalDismiss()
            assertThat(awaitItem().confirmingClearJournal).isFalse()
        }

        assertThat(sessions.observeSessions(bookId).first()).hasSize(1)
    }

    @Test
    fun readingSettingsAndStatisticsSwitchAreWritten() = runTest {
        val vm = SettingsViewModel(settings, sessions)
        vm.state.test {
            val initial = awaitItem()
            assertThat(initial.readingSettings).isEqualTo(ReadingSettings())
            assertThat(initial.showStatistics).isTrue()

            vm.onFontChange(ReadingFont.ATKINSON)
            assertThat(awaitItem().readingSettings.font).isEqualTo(ReadingFont.ATKINSON)
            vm.onFontSizeChange(ReadingSettingsLimits.MAX_FONT_SIZE_SP + 5)
            assertThat(awaitItem().readingSettings.fontSizeSp).isEqualTo(ReadingSettingsLimits.MAX_FONT_SIZE_SP)
            vm.onDefaultScrollModeChange(ScrollMode.PAGES)
            assertThat(awaitItem().readingSettings.defaultScrollMode).isEqualTo(ScrollMode.PAGES)
            vm.onShowStatisticsChange(false)
            assertThat(awaitItem().showStatistics).isFalse()
        }
        assertThat(settings.readingSettings.first().font).isEqualTo(ReadingFont.ATKINSON)
        assertThat(settings.showStatistics.first()).isFalse()
    }
}

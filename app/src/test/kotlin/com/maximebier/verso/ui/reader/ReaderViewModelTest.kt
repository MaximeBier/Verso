package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.GestureSignal
import com.maximebier.verso.reader.testLocator
import com.maximebier.verso.readium.Locators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelTest {

    private lateinit var db: VersoDatabase
    private lateinit var books: BookRepository

    /** ViewModels créés par [factory] : leurs dernières écritures de session sont attendues avant de fermer la base. */
    private val viewModels = mutableListOf<ReaderViewModel>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).allowMainThreadQueries().build()
        books = BookRepository(db.bookDao(), context.filesDir.resolve("books"), context.filesDir.resolve("covers"))
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { viewModels.forEach { it.awaitSessionWrites() } } }
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun restoresSavedLocatorAfterRecreation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.3000)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))

        // Première vie de l’écran : on lit un peu plus loin, la position est sauvegardée.
        val firstStore = ViewModelStore()
        val first = ViewModelProvider.create(firstStore, factory(id))[ReaderViewModel::class]
        val loaded = first.uiState.first { !it.loading }
        assertThat(loaded.initialLocator?.locations?.progression).isEqualTo(0.40)

        val fake = FakeReaderController(start)
        first.onReaderReady(fake)
        runCurrent()
        // Lecture lente (deux secondes pour un petit pas) : reste un mouvement de lecture pour la machine à états de 5.3.
        advanceTimeBy(2_000)
        val moved = testLocator(chapter = 2, progression = 0.41, total = 0.3001)
        fake.displayed.value = moved
        runCurrent()
        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false))
        advanceTimeBy(1_000)
        runCurrent()
        books.observeBook(id).first { entity ->
            entity?.readingLocatorJson?.let(Locators::fromJson)?.locations?.progression == 0.41
        }
        firstStore.clear() // fin du ViewModel : équivalent d’une mort du processus pour l’écran

        // Seconde vie : le ViewModel repart de la base et rouvre au dernier locator sauvegardé.
        val secondStore = ViewModelStore()
        val second = ViewModelProvider.create(secondStore, factory(id))[ReaderViewModel::class]
        val restored = second.uiState.first { !it.loading }

        assertThat(restored.failed).isFalse()
        assertThat(restored.initialLocator?.href?.toString()).isEqualTo("chapitre-2.xhtml")
        assertThat(restored.initialLocator?.locations?.progression).isEqualTo(0.41)
        assertThat(restored.readingPercent).isEqualTo(30)
        secondStore.clear()
    }

    @Test
    fun missingBookFails() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(bookId = 999))[ReaderViewModel::class]

        assertThat(viewModel.uiState.first { !it.loading }.failed).isTrue()
        store.clear()
    }

    @Test
    fun tocAndBarsToggle() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }

        viewModel.toggleBars()
        assertThat(viewModel.uiState.value.barsVisible).isTrue()
        viewModel.showToc()
        assertThat(viewModel.uiState.value.tocVisible).isTrue()
        assertThat(viewModel.uiState.value.toc.map { it.title }).containsExactly("Chapitre I", "Chapitre II").inOrder()

        val fake = FakeReaderController(testLocator(chapter = 1, progression = 0.0, total = 0.0))
        viewModel.onReaderReady(fake)
        viewModel.jumpToTocEntry(1)
        runCurrent()

        assertThat(fake.goCalls.last().href.toString()).isEqualTo("chapitre-2.xhtml")
        assertThat(viewModel.uiState.value.tocVisible).isFalse()
        assertThat(viewModel.uiState.value.barsVisible).isFalse()

        // Saut explicite vers un locator précis (« Reprendre ici » du journal, tâche 6.4).
        viewModel.jumpTo(testLocator(chapter = 1, progression = 0.25, total = 0.1))
        runCurrent()

        assertThat(fake.goCalls.last().href.toString()).isEqualTo("chapitre-1.xhtml")
        assertThat(fake.goCalls.last().locations.progression).isEqualTo(0.25)
        store.clear()
    }

    @Test
    fun tocJumpShowsReturnCardAndGoBackReturnsToReading() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val effects = mutableListOf<TrackerEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.readingEffects.collect { effects += it } }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()

        viewModel.jumpToTocEntry(0) // « Chapitre I » : début du livre
        runCurrent()

        assertThat(fake.goCalls.last().href.toString()).isEqualTo("chapitre-1.xhtml")
        val withCard = viewModel.uiState.first { it.returnCard != null }
        assertThat(withCard.returnCard?.percent).isEqualTo(30)
        assertThat(withCard.readingPercent).isEqualTo(30)

        viewModel.goBack()
        runCurrent()

        assertThat(fake.goCalls.last().locations.totalProgression!!).isWithin(1e-9).of(0.30)
        assertThat(viewModel.uiState.value.returnCard).isNull()
        // Un saut et un retour sont de la navigation : aucun SaveReading ni ReadingMoved sur readingEffects.
        assertThat(effects).isEmpty()
        store.clear() // arrête le Tick du coordinateur avant la fin de runTest
    }

    @Test
    fun journalShowsTheCurrentSessionAndResumeHereIsAJump() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        viewModel.onReaderReady(FakeReaderController(start))
        runCurrent()

        viewModel.toggleBars()   // un toucher : la session en cours existe désormais
        viewModel.showJournal()
        val journal = viewModel.journal.first { state -> state != null && !state.isEmpty }!!
        assertThat(journal.days.single().sessions.single().inProgress).isTrue()

        val earlier = testLocator(chapter = 1, progression = 0.1, total = 0.05)
        viewModel.resumeFromJournal(BookPosition(Locators.toJson(earlier), 0.05))
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.journalVisible).isFalse()
        assertThat(state.returnCard).isNotNull()                         // saut explicite loin de la lecture
        assertThat(state.readingProgression).isWithin(1e-9).of(0.30)     // la lecture n'a pas bougé
        store.clear()
    }

    @Test
    fun configurationChangeKeepsTheSessionInProgress() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        // Clear en finally : un échec ne laisse pas tourner le Tick du coordinateur (runTest ne finirait pas).
        try {
            viewModel.uiState.first { !it.loading }
            viewModel.onReaderReady(FakeReaderController(start))
            runCurrent()
            viewModel.toggleBars()   // un toucher : la session en cours existe désormais

            // Rotation ou thème du système : ON_STOP pendant le changement de configuration, puis ON_START.
            viewModel.onStop(changingConfigurations = true)
            viewModel.onReaderGone()
            viewModel.onStart()
            viewModel.onReaderReady(FakeReaderController(start))
            runCurrent()
            viewModel.toggleBars()

            viewModel.showJournal()
            val journal = viewModel.journal.first { state -> state != null && !state.isEmpty }!!
            val session = journal.days.single().sessions.single()
            assertThat(session.inProgress).isTrue()
        } finally {
            store.clear()
        }
        viewModel.awaitSessionWrites()
        assertThat(db.sessionDao().observeForBook(id).first()).hasSize(1)
    }

    @Test
    fun internalLinkIsAnExplicitJump() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()

        // Lien interne suivi par le moteur (qui se déplace lui-même) : annoncé comme un saut, sans go().
        viewModel.onInternalLinkFollowed(Url("chapitre-1.xhtml#note-3")!!)
        runCurrent()

        assertThat(fake.goCalls).isEmpty()
        assertThat(viewModel.uiState.value.returnCard?.percent).isEqualTo(30)
        assertThat(viewModel.uiState.value.readingPercent).isEqualTo(30)
        store.clear()
    }

    @Test
    fun jumpRequestedWithoutReaderIsAppliedToTheNextOne() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }

        // Surface retirée (activité recréée) : le sommaire reste ouvert, le saut attend la nouvelle surface.
        val old = FakeReaderController(testLocator(chapter = 1, progression = 0.0, total = 0.0))
        viewModel.onReaderReady(old)
        viewModel.toggleBars()
        viewModel.showToc()
        viewModel.onReaderGone()
        viewModel.jumpToTocEntry(1)
        runCurrent()

        assertThat(old.goCalls).isEmpty()
        assertThat(viewModel.uiState.value.tocVisible).isTrue()

        val next = FakeReaderController(testLocator(chapter = 1, progression = 0.0, total = 0.0))
        viewModel.onReaderReady(next)
        runCurrent()

        assertThat(next.goCalls.map { it.href.toString() }).containsExactly("chapitre-2.xhtml")
        assertThat(viewModel.uiState.value.tocVisible).isFalse()
        assertThat(viewModel.uiState.value.barsVisible).isFalse()
        assertThat(old.goCalls).isEmpty()
        store.clear()
    }

    private fun TestScope.factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ReaderViewModel(
                bookId = bookId,
                books = books,
                sessions = SessionRepository(db.sessionDao()),
                openPublication = { Result.success(testPublication()) },
                clock = { testScheduler.currentTime },
            ).also { viewModels += it }
        }
    }

    private fun testBook(readingLocatorJson: String?, progression: Double) = BookEntity(
        title = "Madame Bovary",
        author = "Gustave Flaubert",
        filePath = "/nonexistent/madame-bovary.epub",
        sha256 = "sha-${readingLocatorJson.hashCode()}-$progression",
        coverPath = null,
        sizeBytes = 1_234,
        originalFileName = "madame-bovary.epub",
        importedAt = 0,
        lastOpenedAt = null,
        readingLocatorJson = readingLocatorJson,
        progression = progression,
        totalWords = 100_000,
    )
}

/** Publication minimale en mémoire : deux chapitres, un sommaire plat. */
fun testPublication(): Publication {
    val chapter1 = Link(href = Url("chapitre-1.xhtml")!!, mediaType = MediaType.XHTML)
    val chapter2 = Link(href = Url("chapitre-2.xhtml")!!, mediaType = MediaType.XHTML)
    return Publication(
        manifest = Manifest(
            metadata = Metadata(localizedTitle = LocalizedString("Madame Bovary")),
            readingOrder = listOf(chapter1, chapter2),
            tableOfContents = listOf(
                chapter1.copy(title = "Chapitre I"),
                chapter2.copy(title = "Chapitre II"),
            ),
        ),
    )
}

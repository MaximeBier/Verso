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
import com.maximebier.verso.R
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.core.text.TocProgress
import com.maximebier.verso.core.text.preorder
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.importer.EpubFixtures
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.GestureSignal
import com.maximebier.verso.reader.ReaderStyle
import com.maximebier.verso.reader.testLocator
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadiumOpener
import com.maximebier.verso.ui.common.locationTexts
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Locator
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

    // Main = StandardTestDispatcher, jamais Unconfined : un dispatcher « unconfined » reprend load() sur le fil qui a
    // terminé l’attente (Room, Readium), et la fin du chargement (startSessions…) courait alors en parallèle du test,
    // comme jamais sur le vrai fil principal (flake configurationChangeKeepsTheSessionInProgress, aucune session en base).

    /** ViewModels créés par [factory] : leurs dernières écritures de session sont attendues avant de fermer la base. */
    private val viewModels = mutableListOf<ReaderViewModel>()

    /** Titres signalés par `reportOpenFailure` (message de la bibliothèque). */
    private val openFailures = mutableListOf<String>()

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
    fun settingsAndThemeAreSubmittedWithoutMovingTheReadingPosition() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.3000)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val settings = MutableStateFlow(ReadingSettings())
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id, readingSettings = settings))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()
        assertThat(fake.submitted.last()).isEqualTo(ReaderStyle(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS))

        settings.value = ReadingSettings(fontSizeSp = 24)
        runCurrent()
        viewModel.onThemeChanged(AppTheme.SEPIA)
        runCurrent()

        assertThat(fake.submitted.last())
            .isEqualTo(ReaderStyle(ReadingSettings(fontSizeSp = 24), AppTheme.SEPIA, ScrollMode.CONTINUOUS))
        assertThat(viewModel.uiState.value.readingSettings.fontSizeSp).isEqualTo(24)
        assertThat(viewModel.uiState.value.returnCard).isNull()
        assertThat(books.book(id)!!.progression).isEqualTo(0.30)
        store.clear()
    }

    @Test
    fun restoresSavedLocatorAfterRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
    fun anchoredChaptersOfOneFileAreToldApartByTheReadingProgression() = runTest {
        // Défaut C : plusieurs chapitres ancrés dans un fichier, la barre affichait le premier du fichier.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = EpubFixtures.anchoredEpub(context.filesDir.resolve("ancres.epub"))
        val reading = Locator(
            href = Url("OEBPS/p1.xhtml")!!,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(progression = 0.5, totalProgression = 0.3),
        )
        val id = books.insert(testBook(Locators.toJson(reading), 0.3).copy(filePath = file.path))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id, open = ReadiumOpener(context)::open))[ReaderViewModel::class]

        val state = viewModel.uiState.first { !it.loading }

        assertThat(state.failed).isFalse()
        assertThat(state.chapterPath).containsExactly("Première partie", "II").inOrder()
        assertThat(state.shortLocation).isEqualTo("Partie I, chap. II")
        assertThat(state.currentProgression).isEqualTo(0.5)
        store.clear()
    }

    @Test
    fun savedReadingPositionCarriesTheLongChapterLabelOfTheReadingBar() = runTest {
        // Revue finale I1 : la carte « Reprendre » lit le titre du locator écrit en base.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val (viewModel, fake, id) = openAnchoredBook(store, startProgression = 0.50)
        advanceTimeBy(2_000)
        fake.displayed.value = anchoredLocator(0.501)
        runCurrent()
        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false))
        advanceTimeBy(1_000)
        runCurrent()
        viewModel.onStop()
        runCurrent()

        // Attente en temps réel (Room écrit sur son propre fil) : le battement de 1 s ne laisse jamais le planificateur au repos.
        val stored = withContext(Dispatchers.Default) {
            withTimeoutOrNull(10_000) {
                books.observeBook(id).first { entity -> entity?.readingLocatorJson?.let(Locators::fromJson)?.title != null }
            }
        }
        store.clear() // avant l’assertion : sinon le battement tourne sans fin en temps virtuel si elle échoue
        assertThat(stored?.readingLocatorJson?.let(Locators::fromJson)?.title).isEqualTo("Première partie, II")
    }

    /** Ouvre l'EPUB ancré (position : début de `p1.xhtml`) avec une surface factice ; préordre 3 = « III ». */
    private suspend fun TestScope.openAnchoredBook(store: ViewModelStore, startProgression: Double = 0.0): Triple<ReaderViewModel, FakeReaderController, Long> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = EpubFixtures.anchoredEpub(context.filesDir.resolve("ancres.epub"))
        val start = anchoredLocator(startProgression)
        val id = books.insert(testBook(Locators.toJson(start), 0.0).copy(filePath = file.path))
        val viewModel = ViewModelProvider.create(store, factory(id, open = ReadiumOpener(context)::open))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()
        return Triple(viewModel, fake, id)
    }

    private fun anchoredLocator(progression: Double) = Locator(
        href = Url("OEBPS/p1.xhtml")!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(progression = progression, totalProgression = progression * 0.6),
    )

    private fun ReaderViewModel.tocEntry(index: Int) = preorder(uiState.value.toc) { it.children }[index]

    @Test
    fun tocJumpToAnAnchorCalibratesItWithTheSettledEngineProgression() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val (viewModel, fake) = openAnchoredBook(store)
        try {
            val estimate = viewModel.tocEntry(3).progression!!

            viewModel.jumpToTocEntry(3)
            runCurrent()
            // Le moteur arrive sur le titre de « III » un peu avant l'estimation, puis ne bouge plus.
            fake.displayed.value = anchoredLocator(0.61)
            advanceTimeBy(TocProgress.CALIBRATION_SETTLE_MS + 100)
            runCurrent()

            assertThat(estimate).isWithin(0.02).of(2.0 / 3)
            assertThat(viewModel.tocEntry(3).progression).isEqualTo(0.61)
        } finally {
            store.clear() // même en échec : sinon le Tick du coordinateur ne s’arrête jamais
        }
    }

    @Test
    fun gestureBeforeTheEngineSettlesCancelsTheCalibration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val (viewModel, fake) = openAnchoredBook(store)
        try {
            val estimate = viewModel.tocEntry(3).progression

            viewModel.jumpToTocEntry(3)
            runCurrent()
            fake.displayed.value = anchoredLocator(0.61)
            advanceTimeBy(100)
            // L'utilisateur fait défiler : la position affichée n'est plus celle de l'ancre.
            fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false))
            fake.displayed.value = anchoredLocator(0.70)
            advanceTimeBy(TocProgress.CALIBRATION_WINDOW_MS)
            runCurrent()

            assertThat(viewModel.tocEntry(3).progression).isEqualTo(estimate)
        } finally {
            store.clear()
        }
    }

    @Test
    fun missingBookFails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(bookId = 999))[ReaderViewModel::class]

        assertThat(viewModel.uiState.first { !it.loading }.failed).isTrue()
        store.clear()
    }

    @Test
    fun unreadableFileFailsAndReportsTheBook() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            factory(id, open = { Result.failure(IllegalStateException("illisible")) }),
        )[ReaderViewModel::class]

        assertThat(viewModel.uiState.first { !it.loading }.failed).isTrue()
        assertThat(openFailures).containsExactly("Madame Bovary")
        store.clear()
    }

    @Test
    fun fixedLayoutBookFailsWithoutBecomingTheLastOpenedBook() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val fixed = testPublication().let { p ->
            Publication(manifest = p.manifest.copy(metadata = p.metadata.copy(layout = Layout.FIXED)))
        }
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id, open = { Result.success(fixed) }))[ReaderViewModel::class]

        assertThat(viewModel.uiState.first { !it.loading }.failed).isTrue()
        assertThat(openFailures).containsExactly("Madame Bovary")
        // Sinon il serait rouvert (et raté) à chaque lancement pendant 24 h, à la place du vrai dernier livre.
        assertThat(books.book(id)!!.lastOpenedAt).isNull()
        store.clear()
    }

    @Test
    fun engineRefusingTheBookFailsAndReportsIt() = runTest {
        // Revue finale I3 : EPUB à mise en page fixe, rendition impossible : retour à la bibliothèque avec un message.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        assertThat(viewModel.uiState.first { !it.loading }.failed).isFalse()

        viewModel.onEngineFailed()

        assertThat(viewModel.uiState.value.failed).isTrue()
        assertThat(openFailures).containsExactly("Madame Bovary")
        store.clear()
    }

    @Test
    fun tocAndBarsToggle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        // Horloge figée : pendant les attentes réelles (Room), runTest avance le temps virtuel au rythme du Tick du lecteur
        // (plusieurs minutes observées), et le Tick réel du SessionCoordinator (Dispatchers.Default) y lirait une inactivité
        // de plus de 5 min, qui ferme la session. Ce test porte sur le changement de configuration, pas sur le temps.
        val viewModel = ViewModelProvider.create(store, factory(id, clock = { SESSION_TEST_TIME_MS }))[ReaderViewModel::class]
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
        // Des touchers sans lecture : la session n'est pas gardée au journal.
        assertThat(db.sessionDao().observeForBook(id).first()).isEmpty()
    }

    @Test
    fun internalLinkIsAnExplicitJump() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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

    private fun TestScope.factory(
        bookId: Long,
        open: suspend (File) -> Result<Publication> = { Result.success(testPublication()) },
        clock: () -> Long = { testScheduler.currentTime },
        readingSettings: Flow<ReadingSettings> = flowOf(ReadingSettings()),
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ReaderViewModel(
                bookId = bookId,
                books = books,
                sessions = SessionRepository(db.sessionDao()),
                openPublication = open,
                clock = clock,
                locationTexts = ApplicationProvider.getApplicationContext<Context>().resources.locationTexts(),
                reportOpenFailure = { title -> openFailures += title },
                readingSettings = readingSettings,
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

    private companion object {
        const val SESSION_TEST_TIME_MS = 1_000_000L
    }
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

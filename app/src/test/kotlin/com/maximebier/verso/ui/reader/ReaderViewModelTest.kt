package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.core.text.TocProgress
import com.maximebier.verso.core.text.preorder
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.testSession
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.importer.EpubFixtures
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.GestureSignal
import com.maximebier.verso.reader.PageInfo
import com.maximebier.verso.reader.ReaderGestures
import com.maximebier.verso.reader.ReaderStyle
import com.maximebier.verso.reader.testLocator
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadiumOpener
import com.maximebier.verso.ui.common.locationTexts
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    @get:Rule val tmp = TemporaryFolder()

    /** Un seul DataStore par test : deux instances sur le même fichier lèveraient une exception. */
    private var settingsRepository: SettingsRepository? = null

    private fun TestScope.testSettings(): SettingsRepository =
        settingsRepository ?: SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "reader.preferences_pb") }),
        ).also { settingsRepository = it }

    @Before
    fun setUp() {
        settingsRepository = null
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
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()
        assertThat(fake.submitted.last()).isEqualTo(ReaderStyle(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS))

        testSettings().updateReadingSettings { it.withFontSize(24) }
        testSettings().readingSettings.first { it.fontSizeSp == 24 }
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

    @Test
    fun readingSettingsChangedFromTheReaderAreSavedAndSubmittedWithoutMovingReading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)

        viewModel.onThemeChanged(AppTheme.SEPIA)
        viewModel.onReaderReady(fake)
        runCurrent()
        assertThat(fake.submitted.last()).isEqualTo(ReaderStyle(ReadingSettings(), AppTheme.SEPIA, ScrollMode.CONTINUOUS))

        viewModel.updateReadingSettings { it.larger().copy(lineSpacing = LineSpacing.AIRY) }
        runCurrent()
        testSettings().readingSettings.first { it.fontSizeSp == 21 }
        runCurrent()

        val last = fake.submitted.last()
        assertThat(last.settings.fontSizeSp).isEqualTo(21)
        assertThat(last.settings.lineSpacing).isEqualTo(LineSpacing.AIRY)
        assertThat(viewModel.uiState.value.readingPercent).isEqualTo(30)
        assertThat(viewModel.uiState.value.returnCard).isNull()
        assertThat(fake.goCalls).isEmpty()
        store.clear()
    }

    @Test
    fun readingSettingsSheetOpensWithoutBarsAndCloses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        viewModel.toggleBars()

        viewModel.showReadingSettings()
        assertThat(viewModel.uiState.value.settingsVisible).isTrue()
        assertThat(viewModel.uiState.value.barsVisible).isFalse()

        viewModel.hideReadingSettings()
        assertThat(viewModel.uiState.value.settingsVisible).isFalse()
        store.clear()
    }

    @Test
    fun themeChosenInTheSheetIsSaved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }

        viewModel.setThemeMode(ThemeMode.NIGHT)
        runCurrent()

        // Écriture DataStore sur un autre fil : attente en temps réel, pas en temps virtuel.
        val saved = withContext(Dispatchers.Default) {
            withTimeout(5_000) { testSettings().themeMode.first { it == ThemeMode.NIGHT } }
        }
        assertThat(saved).isEqualTo(ThemeMode.NIGHT)
        assertThat(viewModel.themeMode.first { it == ThemeMode.NIGHT }).isEqualTo(ThemeMode.NIGHT)
        store.clear()
    }

    @Test
    fun bookScrollModeIsSubmittedOtherwiseTheDefaultOne() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        testSettings().updateReadingSettings { it.copy(defaultScrollMode = ScrollMode.PAGES) }
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        assertThat(viewModel.uiState.first { !it.loading }.scrollMode).isEqualTo(ScrollMode.PAGES)
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()

        assertThat(fake.submitted.last().scrollMode).isEqualTo(ScrollMode.PAGES)
        store.clear()
    }

    @Test
    fun bookOwnScrollModeWinsOverTheDefaultOne() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        assertThat(openedScrollMode(defaultMode = ScrollMode.CONTINUOUS, bookMode = "PAGES")).isEqualTo(ScrollMode.PAGES)
    }

    @Test
    fun bookContinuousScrollModeWinsOverPagesByDefault() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        assertThat(openedScrollMode(defaultMode = ScrollMode.PAGES, bookMode = "CONTINUOUS")).isEqualTo(ScrollMode.CONTINUOUS)
    }

    @Test
    fun unknownBookScrollModeFallsBackToTheDefaultOne() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        assertThat(openedScrollMode(defaultMode = ScrollMode.PAGES, bookMode = "SIDEWAYS")).isEqualTo(ScrollMode.PAGES)
    }

    @Test
    fun surfaceIsCreatedWithTheSavedReadingSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        testSettings().updateReadingSettings { it.withFontSize(26) }
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]

        // Premier état qui porte la publication (ReaderSurface se crée alors) : les réglages enregistrés y sont déjà.
        val first = viewModel.uiState.first { it.publication != null }
        assertThat(first.readingSettings.fontSizeSp).isEqualTo(26)
        store.clear()
    }

    @Test
    fun switchingToPagesIsSavedForThisBookWithoutMovingTheReadingPosition() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()

        viewModel.setScrollMode(ScrollMode.PAGES)
        // Le mode affiché change au moment même où il est enregistré pour ce livre.
        assertThat(viewModel.uiState.value.scrollMode).isEqualTo(ScrollMode.PAGES)
        // Seul chemin vers le moteur : submit. La remise en page et le retour au texte sont ceux du contrôleur.
        assertThat(fake.submitted.last().scrollMode).isEqualTo(ScrollMode.PAGES)
        advanceTimeBy(ReaderGestures.MODE_SWITCH_SETTLE_MS + 1)
        runCurrent()

        assertThat(fake.goCalls).isEmpty()
        assertThat(viewModel.uiState.value.returnCard).isNull()
        assertThat(viewModel.uiState.value.readingPercent).isEqualTo(30)
        books.observeBook(id).first { it?.scrollMode == "PAGES" }
        assertThat(books.book(id)!!.progression).isEqualTo(0.30)

        // Même mode : rien n’est renvoyé au moteur.
        val submissions = fake.submitted.size
        viewModel.setScrollMode(ScrollMode.PAGES)
        assertThat(fake.submitted).hasSize(submissions)

        viewModel.setScrollMode(ScrollMode.CONTINUOUS)
        assertThat(viewModel.uiState.value.scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
        assertThat(fake.submitted.last().scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
        books.observeBook(id).first { it?.scrollMode == "CONTINUOUS" }
        store.clear()
    }

    @Test
    fun pageInfoAndDisplayedChapterReachTheUiState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30).copy(scrollMode = "PAGES"))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()

        fake.pageInfo.value = PageInfo(page = 2, pageCount = 9)
        runCurrent()

        assertThat(viewModel.uiState.value.pageInfo).isEqualTo(PageInfo(2, 9))
        assertThat(viewModel.uiState.value.displayedChapterPath).containsExactly("Chapitre II")

        // Le pied suit la position AFFICHÉE ; la barre garde celle de la lecture.
        fake.displayed.value = testLocator(chapter = 1, progression = 0.5, total = 0.2)
        runCurrent()
        assertThat(viewModel.uiState.value.displayedChapterPath).containsExactly("Chapitre I")
        store.clear()
    }

    @Test
    fun footerChapterFollowsTheAnchorMeasuredInThePage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // Une partie dans un seul fichier, deux chapitres ancrés (Madame Bovary de Gutenberg).
        val part = Link(href = Url("chapitre-2.xhtml")!!, mediaType = MediaType.XHTML)
        val publication = Publication(
            manifest = Manifest(
                metadata = Metadata(localizedTitle = LocalizedString("Madame Bovary")),
                readingOrder = listOf(part),
                tableOfContents = listOf(
                    Link(href = Url("chapitre-2.xhtml#c9")!!, mediaType = MediaType.XHTML, title = "IX"),
                    Link(href = Url("chapitre-2.xhtml#c10")!!, mediaType = MediaType.XHTML, title = "X"),
                ),
            ),
        )
        val start = testLocator(chapter = 2, progression = 0.10, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30).copy(scrollMode = "PAGES"))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id, open = { Result.success(publication) }))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()

        // Ancre mesurée dans la page : chapitre X, quelle que soit l’estimation par la progression.
        fake.pageInfo.value = PageInfo(page = 1, pageCount = 19, chapterAnchor = "c10")
        runCurrent()
        assertThat(viewModel.uiState.value.displayedChapterPath).containsExactly("X")

        fake.pageInfo.value = PageInfo(page = 21, pageCount = 21, chapterAnchor = "c9")
        runCurrent()
        assertThat(viewModel.uiState.value.displayedChapterPath).containsExactly("IX")
        store.clear()
    }

    @Test
    fun turnPageAsksTheReader() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30).copy(scrollMode = "PAGES"))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)

        viewModel.turnPage(forward = true)
        viewModel.turnPage(forward = false)

        assertThat(fake.turns).containsExactly(true, false).inOrder()
        store.clear()
    }

    @Test
    fun bookOpenedInPagesUsesThePagesThresholds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (viewModel, fake, store) = openAt(bookMode = "PAGES")
        assertThat(twoSlowPageTurnsConfirmAfterAJump(viewModel, fake)).isTrue()
        assertThat(viewModel.uiState.value.readingPercent).isEqualTo(60)
        store.clear()
    }

    @Test
    fun bookOpenedInContinuousKeepsTheContinuousThresholds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (viewModel, fake, store) = openAt(bookMode = "CONTINUOUS")
        // Une minute entre deux gestes dépasse la pause admise en continu : la carte reste.
        assertThat(twoSlowPageTurnsConfirmAfterAJump(viewModel, fake)).isFalse()
        assertThat(viewModel.uiState.value.readingPercent).isEqualTo(30)
        store.clear()
    }

    @Test
    fun switchingToPagesAppliesThePagesThresholds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (viewModel, fake, store) = openAt(bookMode = "CONTINUOUS")
        viewModel.setScrollMode(ScrollMode.PAGES)
        runCurrent()
        assertThat(twoSlowPageTurnsConfirmAfterAJump(viewModel, fake)).isTrue()
        store.clear()
    }

    @Test
    fun switchingBackToContinuousRestoresTheContinuousThresholds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (viewModel, fake, store) = openAt(bookMode = "PAGES")
        viewModel.setScrollMode(ScrollMode.CONTINUOUS)
        runCurrent()
        assertThat(twoSlowPageTurnsConfirmAfterAJump(viewModel, fake)).isFalse()
        store.clear()
    }

    private data class OpenedReader(val viewModel: ReaderViewModel, val fake: FakeReaderController, val store: ViewModelStore)

    /** Livre lu à 30 % (chapitre II, 40 %), de défilement [bookMode], surface factice branchée. */
    private suspend fun TestScope.openAt(bookMode: String): OpenedReader {
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30).copy(scrollMode = bookMode))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()
        return OpenedReader(viewModel, fake, store)
    }

    /**
     * Saut loin de la lecture (60 %), puis deux tours de page lents (une minute chacun, un écran environ : le livre de
     * test compte à peu près 670 écrans). Vrai si la nouvelle position est confirmée (carte « Revenir » partie).
     */
    private suspend fun TestScope.twoSlowPageTurnsConfirmAfterAJump(viewModel: ReaderViewModel, fake: FakeReaderController): Boolean {
        viewModel.jumpTo(testLocator(chapter = 2, progression = 0.80, total = 0.60))
        advanceTimeBy(2_000)
        runCurrent()
        assertThat(viewModel.uiState.value.returnCard).isNotNull()
        listOf(0.6015, 0.6030).forEachIndexed { index, total ->
            advanceTimeBy(60_000)
            fake.displayed.value = testLocator(chapter = 2, progression = 0.81 + 0.01 * index, total = total)
            runCurrent()
            advanceTimeBy(100)
            fake.gestures.emit(GestureSignal(testScheduler.currentTime, isFling = false))
            runCurrent()
        }
        advanceTimeBy(1_000)
        runCurrent()
        return viewModel.uiState.value.returnCard == null
    }

    @Test
    fun remainingTimeUsesTheMeasuredSpeed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        SessionRepository(db.sessionDao()).upsert(testSession(id, startedAt = 0).copy(activeMs = 10 * 60_000, wordsRead = 1_000))

        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading && it.wordsPerMinute == 100 }
        viewModel.onReaderReady(FakeReaderController(start))
        runCurrent()
        val expected = remainingMinutes(totalWords = 100_000, progression = 0.30, wordsPerMinute = 100)
        assertThat(viewModel.uiState.first { it.remainingMinutes == expected }.remainingMinutes).isEqualTo(expected)
        store.clear()
    }

    @Test
    fun openingFromTheDetailsJournalShowsTheJournal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id, openJournal = true))[ReaderViewModel::class]
        assertThat(viewModel.uiState.first { !it.loading && it.journalVisible }.journalVisible).isTrue()
        store.clear()
    }

    /** Ouvre un livre de défilement [bookMode] (`books.scrollMode`) avec [defaultMode] dans les Paramètres ; mode soumis au lecteur. */
    private suspend fun TestScope.openedScrollMode(defaultMode: ScrollMode, bookMode: String): ScrollMode {
        testSettings().updateReadingSettings { it.copy(defaultScrollMode = defaultMode) }
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30).copy(scrollMode = bookMode))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        val state = viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()
        assertThat(fake.submitted.last().scrollMode).isEqualTo(state.scrollMode)
        store.clear()
        return state.scrollMode
    }

    private fun TestScope.factory(
        bookId: Long,
        open: suspend (File) -> Result<Publication> = { Result.success(testPublication()) },
        clock: () -> Long = { testScheduler.currentTime },
        openJournal: Boolean = false,
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
                settings = testSettings(),
                openJournalOnLoad = openJournal,
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

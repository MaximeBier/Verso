package com.maximebier.verso.ui.library

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.importer.ImportResult
import com.maximebier.verso.importer.PendingImport
import com.maximebier.verso.importer.RejectReason
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryViewModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: VersoDatabase
    private lateinit var books: BookRepository
    private lateinit var settings: SettingsRepository
    private val fake = FakeImports()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        books = BookRepository(db.bookDao(), tmp.newFolder("books"), tmp.newFolder("covers"))
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

    private fun viewModel(pending: MutableStateFlow<List<Uri>> = MutableStateFlow(emptyList())) = LibraryViewModel(
        books = books,
        settings = settings,
        importActions = fake.actions(),
        incomingPending = pending,
        takeIncoming = { pending.getAndUpdate { it.drop(1) }.firstOrNull() },
    )

    private suspend fun ReceiveTurbine<LibraryUiState>.awaitUntil(predicate: (LibraryUiState) -> Boolean): LibraryUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private suspend fun insert(
        title: String,
        author: String = "Auteur",
        lastOpenedAt: Long? = null,
        locatorJson: String? = null,
        progression: Double = 0.0,
        totalWords: Long = 60_000,
    ): Long = books.insert(
        BookEntity(
            title = title,
            author = author,
            filePath = File(tmp.root, "$title.epub").absolutePath,
            sha256 = "sha-$title",
            coverPath = null,
            sizeBytes = 1_000,
            originalFileName = "$title.epub",
            importedAt = 1_000,
            lastOpenedAt = lastOpenedAt,
            readingLocatorJson = locatorJson,
            progression = progression,
            totalWords = totalWords,
        ),
    )

    @Test
    fun emptyLibraryIsLoadedWithoutResume() = runTest(dispatcher) {
        viewModel().state.test {
            val state = awaitUntil { !it.loading }
            assertThat(state.books).isEmpty()
            assertThat(state.resume).isNull()
            assertThat(state.sort).isEqualTo(LibrarySort.RECENT)
            assertThat(state.viewMode).isEqualTo(LibraryViewMode.LIST)
        }
    }

    @Test
    fun booksAreMappedWithStatusAndPercent() = runTest(dispatcher) {
        insert("Germinal")
        insert("Bel-Ami", locatorJson = LOCATOR_JSON, progression = 0.473)
        insert("Nana", locatorJson = LOCATOR_JSON, progression = 0.995)
        viewModel().state.test {
            val byTitle = awaitUntil { it.books.size == 3 }.books.associateBy { it.title }
            assertThat(byTitle.getValue("Germinal").status).isEqualTo(BookStatus.NEW)
            assertThat(byTitle.getValue("Bel-Ami").status).isEqualTo(BookStatus.IN_PROGRESS)
            assertThat(byTitle.getValue("Bel-Ami").percent).isEqualTo(47)
            assertThat(byTitle.getValue("Nana").status).isEqualTo(BookStatus.FINISHED)
            assertThat(byTitle.getValue("Germinal").colorSeed).isEqualTo("sha-Germinal")
        }
    }

    @Test
    fun sortChangeIsPersistedAndReorders() = runTest(dispatcher) {
        insert("Germinal")
        insert("Bel-Ami")
        insert("Candide")
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.books.size == 3 }
            vm.onSortChange(LibrarySort.TITLE)
            val sorted = awaitUntil { it.sort == LibrarySort.TITLE && it.books.size == 3 }
            assertThat(sorted.books.map { it.title }).containsExactly("Bel-Ami", "Candide", "Germinal").inOrder()
        }
        assertThat(settings.librarySort.first()).isEqualTo(LibrarySort.TITLE)
    }

    @Test
    fun viewModeIsPersisted() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.loading }
            vm.onViewModeChange(LibraryViewMode.GRID)
            awaitUntil { it.viewMode == LibraryViewMode.GRID }
        }
        assertThat(settings.libraryViewMode.first()).isEqualTo(LibraryViewMode.GRID)
    }

    @Test
    fun resumeCardUsesLastOpenedBookAndLocator() = runTest(dispatcher) {
        insert("Germinal", lastOpenedAt = 10)
        val id = insert("Madame Bovary", lastOpenedAt = 20, locatorJson = LOCATOR_JSON, progression = 0.31, totalWords = 150_000)
        viewModel().state.test {
            val resume = requireNotNull(awaitUntil { it.resume != null && it.books.size == 2 }.resume)
            assertThat(resume.book.id).isEqualTo(id)
            assertThat(resume.chapter).isEqualTo("Deuxième partie, chapitre I")
            assertThat(resume.excerpt).isEqualTo("Yonville-l’Abbaye (ainsi nommé à cause d’une ancienne abbaye de Capucins)")
            assertThat(resume.remainingMinutes).isEqualTo(remainingMinutes(150_000, 0.31))
        }
    }

    @Test
    fun excerptIsCleanedAndTruncatedOnWordBoundary() {
        assertThat(excerptOf("  Il était\n  une   fois ")).isEqualTo("Il était une fois")
        val excerpt = excerptOf("mot ".repeat(100))
        assertThat(excerpt.length).isAtMost(EXCERPT_MAX_CHARS + 1)
        assertThat(excerpt).endsWith("mot…")
    }

    @Test
    fun addedImportShowsSnackbarOnce() = runTest(dispatcher) {
        fake.next = ImportResult.Added(bookId = 5, title = "Candide")
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.loading }
            vm.onImport(Uri.parse("content://fichiers/candide.epub"))
            val shown = awaitUntil { it.snackbar != null }
            assertThat(shown.snackbar).isEqualTo(ImportSnackbar(5, "Candide"))
            assertThat(shown.importing).isFalse()
            vm.onSnackbarShown()
            awaitUntil { it.snackbar == null }
        }
        assertThat(fake.imported).containsExactly(Uri.parse("content://fichiers/candide.epub"))
    }

    @Test
    fun duplicateShowsDialogThenIgnoreOrReplace() = runTest(dispatcher) {
        val id = insert("Germinal")
        val duplicate = ImportResult.Duplicate(
            existing = requireNotNull(books.book(id)),
            pending = PendingImport(File(tmp.root, "t.tmp"), "sha-Germinal", "germinal.epub", 1_000),
        )
        fake.next = duplicate
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.loading }
            vm.onImport(Uri.parse("content://fichiers/germinal.epub"))
            assertThat(awaitUntil { it.dialog != null }.dialog).isEqualTo(ImportDialog.Duplicate(duplicate))
            vm.onDuplicateIgnore()
            awaitUntil { it.dialog == null }
            assertThat(fake.ignored).isEqualTo(duplicate)

            vm.onImport(Uri.parse("content://fichiers/germinal.epub"))
            awaitUntil { it.dialog != null }
            vm.onDuplicateReplace()
            awaitUntil { it.dialog == null }
            assertThat(fake.replaced).isEqualTo(duplicate)
        }
    }

    @Test
    fun rejectedShowsDialogWithFileName() = runTest(dispatcher) {
        fake.next = ImportResult.Rejected(RejectReason.DRM)
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.loading }
            vm.onImport(Uri.parse("content://fichiers/protege.epub"))
            assertThat(awaitUntil { it.dialog != null }.dialog)
                .isEqualTo(ImportDialog.Rejected(RejectReason.DRM, "protege.epub"))
            vm.onRejectedDismiss()
            awaitUntil { it.dialog == null }
        }
    }

    @Test
    fun importerCrashBecomesUnreadable() = runTest(dispatcher) {
        fake.failure = IllegalStateException("panne")
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.loading }
            vm.onImport(Uri.parse("content://fichiers/x.epub"))
            assertThat(awaitUntil { it.dialog != null }.dialog)
                .isEqualTo(ImportDialog.Rejected(RejectReason.UNREADABLE, "x.epub"))
        }
    }

    @Test
    fun incomingUrisAreImported() = runTest(dispatcher) {
        fake.next = ImportResult.Added(bookId = 9, title = "Germinal")
        val pending = MutableStateFlow(listOf(Uri.parse("content://drive/germinal.epub")))
        val vm = viewModel(pending)
        vm.state.test {
            assertThat(awaitUntil { it.snackbar != null }.snackbar).isEqualTo(ImportSnackbar(9, "Germinal"))
        }
        assertThat(fake.imported).containsExactly(Uri.parse("content://drive/germinal.epub"))
        assertThat(pending.value).isEmpty()
    }

    @Test
    fun deleteRequestConfirmRemovesBook() = runTest(dispatcher) {
        val id = insert("Germinal")
        val vm = viewModel()
        vm.state.test {
            val book = awaitUntil { it.books.size == 1 }.books.single()
            vm.onDeleteRequest(book)
            assertThat(awaitUntil { it.pendingDelete != null }.pendingDelete).isEqualTo(book)
            vm.onDeleteConfirm()
            awaitUntil { it.pendingDelete == null && it.books.isEmpty() }
        }
        assertThat(books.book(id)).isNull()
    }

    @Test
    fun factoryBuildsViewModelFromContainer() {
        val extras = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, context as Application)
        }
        val vm = LibraryViewModel.Factory.create(LibraryViewModel::class.java, extras)
        assertThat(vm).isInstanceOf(LibraryViewModel::class.java)
    }

    private class FakeImports {
        var next: ImportResult = ImportResult.Rejected(RejectReason.NOT_EPUB)
        var failure: Exception? = null
        val imported = mutableListOf<Uri>()
        var replaced: ImportResult.Duplicate? = null
        var ignored: ImportResult.Duplicate? = null

        fun actions() = ImportActions(
            importBook = { uri ->
                imported += uri
                failure?.let { throw it }
                next
            },
            replace = { duplicate ->
                replaced = duplicate
                ImportResult.Added(duplicate.existing.id, duplicate.existing.title)
            },
            ignore = { duplicate -> ignored = duplicate },
            displayName = { uri -> uri.lastPathSegment.orEmpty() },
        )
    }

    private companion object {
        const val LOCATOR_JSON = """{"href":"OEBPS/partie2-chap1.xhtml","type":"application/xhtml+xml","title":"Deuxième partie, chapitre I","locations":{"progression":0.1,"totalProgression":0.31},"text":{"highlight":"Yonville-l’Abbaye (ainsi nommé à cause d’une ancienne abbaye de Capucins)"}}"""
    }
}

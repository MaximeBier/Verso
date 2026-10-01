package com.maximebier.verso.ui.details

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.stats.ReadingStats
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.VersoDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class DetailsViewModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: VersoDatabase
    private lateinit var books: BookRepository
    private lateinit var booksDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        booksDir = tmp.newFolder("books")
        books = BookRepository(db.bookDao(), booksDir, tmp.newFolder("covers"))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insertBovary(): Long {
        val file = File(booksDir, "sha-bovary.epub").apply { writeText("epub") }
        return books.insert(
            BookEntity(
                title = "Madame Bovary",
                author = "Gustave Flaubert",
                filePath = file.absolutePath,
                sha256 = "sha-bovary",
                coverPath = null,
                sizeBytes = 1_234_567,
                originalFileName = "madame-bovary.epub",
                importedAt = IMPORTED_AT,
                lastOpenedAt = null,
                readingLocatorJson = null,
                progression = 0.0,
                totalWords = 150_000,
            ),
        )
    }

    private fun TestScope.viewModel(id: Long) = DetailsViewModel(bookId = id, books = books, saveScope = this)

    private suspend fun titleBecomes(id: Long, title: String) = books.observeBook(id).first { it?.title == title }

    @Test
    fun loadsBook() = runTest(dispatcher) {
        val id = insertBovary()
        val state = viewModel(id).state.first { it.loaded }
        assertThat(state.titleField).isEqualTo("Madame Bovary")
        assertThat(state.authorField).isEqualTo("Gustave Flaubert")
        assertThat(state.hasStarted).isFalse()
        assertThat(state.percent).isEqualTo(0)
        assertThat(state.remainingMinutes).isEqualTo(remainingMinutes(150_000, 0.0))
        assertThat(state.sizeBytes).isEqualTo(1_234_567)
        assertThat(state.originalFileName).isEqualTo("madame-bovary.epub")
        assertThat(state.importedAt).isEqualTo(IMPORTED_AT)
        assertThat(state.colorSeed).isEqualTo("sha-bovary")
    }

    @Test
    fun titleIsSavedTrimmedAfterDebounce() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        vm.state.first { it.loaded }
        vm.onTitleChange("  Madame Bovary, mœurs de province  ")
        advanceTimeBy(DetailsViewModel.AUTOSAVE_DEBOUNCE_MS - 1)
        runCurrent()
        assertThat(requireNotNull(books.book(id)).title).isEqualTo("Madame Bovary")
        advanceTimeBy(2)
        runCurrent()
        titleBecomes(id, "Madame Bovary, mœurs de province")
    }

    @Test
    fun typingRestartsTheDebounce() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        vm.state.first { it.loaded }
        vm.onTitleChange("Madame B")
        advanceTimeBy(400)
        vm.onTitleChange("Madame Bo")
        advanceTimeBy(400)
        runCurrent()
        assertThat(requireNotNull(books.book(id)).title).isEqualTo("Madame Bovary")
        advanceTimeBy(200)
        runCurrent()
        titleBecomes(id, "Madame Bo")
    }

    @Test
    fun blankTitleKeepsPrevious() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        vm.state.first { it.loaded }
        vm.onTitleChange("   ")
        advanceTimeBy(DetailsViewModel.AUTOSAVE_DEBOUNCE_MS * 2)
        runCurrent()
        assertThat(requireNotNull(books.book(id)).title).isEqualTo("Madame Bovary")
        vm.flush()
        advanceUntilIdle()
        assertThat(vm.state.value.titleField).isEqualTo("Madame Bovary")
        assertThat(requireNotNull(books.book(id)).title).isEqualTo("Madame Bovary")
    }

    @Test
    fun authorCanBeCleared() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        vm.state.first { it.loaded }
        vm.onAuthorChange("")
        advanceTimeBy(DetailsViewModel.AUTOSAVE_DEBOUNCE_MS + 1)
        runCurrent()
        books.observeBook(id).first { it?.author == "" }
    }

    @Test
    fun flushSavesImmediately() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        vm.state.first { it.loaded }
        vm.onTitleChange("Bovary")
        vm.flush()
        runCurrent()
        titleBecomes(id, "Bovary")
    }

    @Test
    fun pendingEditIsSavedWhenLeaving() = runTest(dispatcher) {
        val id = insertBovary()
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { DetailsViewModel(bookId = id, books = books, saveScope = this@runTest) } }
        val vm = ViewModelProvider.create(store, factory)[DetailsViewModel::class]
        vm.state.first { it.loaded }
        vm.onTitleChange("Titre en cours de frappe")
        store.clear()
        advanceUntilIdle()
        titleBecomes(id, "Titre en cours de frappe")
    }

    @Test
    fun deleteAsksConfirmationThenRemovesBookAndLeaves() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        vm.state.first { it.loaded }
        vm.onDeleteClick()
        assertThat(vm.state.value.showDeleteDialog).isTrue()
        vm.onDeleteDismiss()
        assertThat(vm.state.value.showDeleteDialog).isFalse()
        assertThat(books.book(id)).isNotNull()

        vm.onDeleteClick()
        vm.onDeleteConfirm()
        advanceUntilIdle()
        val state = vm.state.first { it.deleted }
        assertThat(state.showDeleteDialog).isFalse()
        assertThat(books.book(id)).isNull()
        assertThat(File(booksDir, "sha-bovary.epub").exists()).isFalse()
    }

    @Test
    fun statusChoiceIsSavedAndWinsOverProgression() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        advanceUntilIdle()
        assertThat(vm.state.value.status).isEqualTo(BookStatus.TO_READ)

        vm.onStatusChange(BookStatus.FINISHED)
        advanceUntilIdle()
        assertThat(books.book(id)!!.stateOverride).isEqualTo("FINISHED")
        assertThat(vm.state.value.status).isEqualTo(BookStatus.FINISHED)

        books.saveReadingPosition(id, """{"href":"ch1.xhtml","type":"application/xhtml+xml"}""", 0.4)
        advanceUntilIdle()
        assertThat(vm.state.value.status).isEqualTo(BookStatus.FINISHED)
    }

    @Test
    fun statsAreShownWithMeasuredRemainingTimeAndHiddenWhenSwitchedOff() = runTest(dispatcher) {
        val id = insertBovary()
        val stats = ReadingStats(totalActiveMs = 148 * 60_000L, sessionCount = 5, wordsPerMinute = 240)
        val shown = MutableStateFlow(true)
        val vm = DetailsViewModel(bookId = id, books = books, saveScope = this, statsOf = { flowOf(stats) }, showStatistics = shown)
        advanceUntilIdle()
        assertThat(vm.state.value.stats).isEqualTo(stats)
        assertThat(vm.state.value.remainingMinutes).isEqualTo(remainingMinutes(150_000, 0.0, 240))

        shown.value = false
        advanceUntilIdle()
        assertThat(vm.state.value.stats).isNull()
        assertThat(vm.state.value.remainingMinutes).isEqualTo(remainingMinutes(150_000, 0.0, 240))
    }

    @Test
    fun collectionNamesFollowTheBook() = runTest(dispatcher) {
        val id = insertBovary()
        val names = MutableStateFlow(emptyList<String>())
        val vm = DetailsViewModel(bookId = id, books = books, saveScope = this, collectionNamesOf = { names })
        advanceUntilIdle()
        assertThat(vm.state.value.collectionNames).isEmpty()
        names.value = listOf("Classiques", "Flaubert")
        advanceUntilIdle()
        assertThat(vm.state.value.collectionNames).containsExactly("Classiques", "Flaubert").inOrder()
    }

    @Test
    fun missingBookLeaves() = runTest(dispatcher) {
        assertThat(viewModel(999).state.first { it.loaded }.missing).isTrue()
    }

    @Test
    fun factoryBuildsViewModelFromContainer() {
        val extras = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, context as Application)
        }
        val vm = DetailsViewModel.factory(42).create(DetailsViewModel::class.java, extras)
        assertThat(vm).isInstanceOf(DetailsViewModel::class.java)
    }

    private companion object {
        const val IMPORTED_AT = 1_789_000_000_000L
    }
}

package com.maximebier.verso.ui.collections

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.CollectionRepository
import com.maximebier.verso.data.db.VersoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: CollectionTestEnvironment
    private var a = 0L
    private var b = 0L
    private var c = 0L
    private var id = 0L

    @Before
    fun setUp() = runTest {
        env = CollectionTestEnvironment(tmp.root)
        a = env.books.insert(testBook("a", title = "La Fortune des Rougon", author = "Émile Zola"))
        b = env.books.insert(testBook("b", title = "La Curée", author = "Émile Zola"))
        c = env.books.insert(testBook("c", title = "L’Assommoir", author = "Émile Zola"))
        id = env.collections.create("Les Rougon-Macquart", listOf(a, b, c))
    }

    @After
    fun tearDown() = env.close()

    private fun viewModel() = CollectionViewModel(id, env.collections, env.books, env.sessions.observeAllStats())

    private suspend fun CollectionViewModel.loaded() = state.first { !it.loading }

    private suspend fun CollectionViewModel.order(): List<Long> = state.first { !it.loading }.detail!!.books.map { it.id }

    @Test
    fun detailListsBooksInOrderWithTheSummary() = runTest {
        val state = viewModel().loaded()
        assertThat(state.detail!!.name).isEqualTo("Les Rougon-Macquart")
        assertThat(state.detail!!.books.map { it.id }).containsExactly(a, b, c).inOrder()
        assertThat(state.detail!!.summary.author).isEqualTo("Émile Zola")
        assertThat(state.detail!!.summary.toRead).isEqualTo(3)
    }

    @Test
    fun moveIsSavedAndSurvivesANewViewModel() = runTest {
        val vm = viewModel()
        vm.loaded()
        vm.onMove(0, 2)
        assertThat(vm.state.first { it.detail?.books?.first()?.id == b }.detail!!.books.map { it.id })
            .containsExactly(b, c, a).inOrder()
        assertThat(viewModel().order()).containsExactly(b, c, a).inOrder()
    }

    @Test
    fun quickSuccessiveMovesAreAllKept() = runBlocking {
        // Base aux exécuteurs de Room, comme sur le téléphone : l’affichage suit les écritures avec un temps de retard.
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java).build()
        try {
            val books = BookRepository(db.bookDao(), tmp.newFolder("livres"), tmp.newFolder("couvertures"))
            val collections = CollectionRepository(db.collectionDao()) { 1L }
            val ids = listOf("x", "y", "z").map { books.insert(testBook(it)) }
            val id = collections.create("Série", ids)
            val vm = CollectionViewModel(id, collections, books, flowOf(emptyMap()))
            vm.state.first { !it.loading }
            // « Descendre » deux fois de suite sur le premier livre, sans attendre l’affichage : il finit dernier.
            vm.onMove(0, 1)
            vm.onMove(1, 2)
            val expected = listOf(ids[1], ids[2], ids[0])
            val order = withTimeoutOrNull(5_000) {
                collections.observeMemberships().first { list -> list.sortedBy { it.position }.map { it.bookId } == expected }
            }
            assertThat(order).isNotNull()
        } finally {
            db.close()
        }
    }

    @Test
    fun moveOutOfRangeChangesNothing() = runTest {
        val vm = viewModel()
        vm.loaded()
        vm.onMove(2, 2)
        vm.onMove(7, 0)
        assertThat(vm.order()).containsExactly(a, b, c).inOrder()
    }

    @Test
    fun removeKeepsTheBookInTheLibrary() = runTest {
        val vm = viewModel()
        vm.loaded()
        vm.onRemove(b)
        assertThat(vm.state.first { it.detail?.books?.size == 2 }.detail!!.books.map { it.id }).containsExactly(a, c).inOrder()
        assertThat(env.books.book(b)).isNotNull()
    }

    @Test
    fun blankRenameIsIgnoredAndClosesTheDialog() = runTest {
        val vm = viewModel()
        vm.loaded()
        vm.onRenameRequest()
        assertThat(vm.state.first().dialog).isEqualTo(CollectionDialog.Rename)
        vm.onRename("   ")
        assertThat(vm.state.first().dialog).isNull()
        vm.onRename("Zola")
        assertThat(vm.state.first { it.detail?.name == "Zola" }.detail!!.name).isEqualTo("Zola")
    }

    @Test
    fun deleteLeavesTheScreenAndKeepsTheBooks() = runTest {
        val vm = viewModel()
        vm.loaded()
        vm.onDeleteRequest()
        assertThat(vm.state.first().dialog).isEqualTo(CollectionDialog.Delete)
        vm.onDeleteConfirm()
        val state = vm.state.first { it.deleted }
        assertThat(state.missing).isFalse()
        assertThat(env.collections.observeCollections().first()).isEmpty()
        assertThat(env.books.book(a)).isNotNull()
    }

    @Test
    fun collectionDeletedElsewhereIsMissing() = runTest {
        val vm = viewModel()
        vm.loaded()
        env.collections.delete(id)
        assertThat(vm.state.first { it.missing }.deleted).isFalse()
    }

    @Test
    fun reorderModeToggles() = runTest {
        val vm = viewModel()
        vm.loaded()
        vm.onReorderStart()
        assertThat(vm.state.first().reordering).isTrue()
        vm.onReorderEnd()
        assertThat(vm.state.first().reordering).isFalse()
    }
}

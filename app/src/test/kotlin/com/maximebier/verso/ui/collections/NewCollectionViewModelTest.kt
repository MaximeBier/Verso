package com.maximebier.verso.ui.collections

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.testBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewCollectionViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: CollectionTestEnvironment
    private var trois = 0L
    private var vingt = 0L
    private var vicomte = 0L
    private var germinal = 0L

    @Before
    fun setUp() = runTest {
        env = CollectionTestEnvironment(tmp.root)
        env.settings.setLibrarySort(LibrarySort.TITLE)
        vingt = env.books.insert(testBook("va", title = "Vingt Ans après", author = "Alexandre Dumas"))
        trois = env.books.insert(testBook("tm", title = "Les Trois Mousquetaires", author = "Alexandre Dumas"))
        germinal = env.books.insert(testBook("g", title = "Germinal", author = "Émile Zola"))
        vicomte = env.books.insert(testBook("vb", title = "Le Vicomte de Bragelonne", author = "Alexandre Dumas"))
    }

    @After
    fun tearDown() = env.close()

    private fun viewModel(preselected: Long? = null) =
        NewCollectionViewModel(preselected, env.books, env.settings, env.collections)

    private suspend fun NewCollectionViewModel.loaded() = state.first { !it.loading }

    @Test
    fun preselectedBookIsChecked() = runTest {
        val state = viewModel(preselected = germinal).loaded()
        assertThat(state.selectedCount).isEqualTo(1)
        assertThat(state.books.single { it.book.id == germinal }.selected).isTrue()
    }

    @Test
    fun filterKeepsHiddenSelections() = runTest {
        val vm = viewModel(preselected = germinal)
        vm.loaded()
        vm.onQueryChange("dumas")
        val state = vm.state.first { it.query == "dumas" }
        assertThat(state.books.map { it.book.title })
            .containsExactly("Les Trois Mousquetaires", "Le Vicomte de Bragelonne", "Vingt Ans après").inOrder()
        assertThat(state.selectedCount).isEqualTo(1)
    }

    @Test
    fun createNeedsANonBlankName() = runTest {
        val vm = viewModel()
        assertThat(vm.loaded().canCreate).isFalse()
        vm.onNameChange("   ")
        assertThat(vm.state.first { it.name == "   " }.canCreate).isFalse()
        vm.onNameChange("Les Mousquetaires")
        assertThat(vm.state.first { it.name == "Les Mousquetaires" }.canCreate).isTrue()
    }

    @Test
    fun booksEnterInLibraryOrderNotClickOrder() = runTest {
        val vm = viewModel(preselected = germinal)
        vm.loaded()
        vm.onQueryChange("dumas")
        vm.onToggle(vingt)
        vm.onToggle(trois)
        vm.onToggle(vicomte)
        vm.onNameChange("Les Mousquetaires")
        vm.onCreate()
        vm.onCreate()
        val id = vm.state.first { it.created != null }.created!!
        assertThat(env.collections.observeCollections().first()).hasSize(1)
        val order = env.collections.observeMemberships().first().filter { it.collectionId == id }.map { it.bookId }
        // Tri « Titre » de la bibliothèque (Collator, espaces ignorés) : Germinal, Les Trois…, Le Vicomte…, Vingt Ans après.
        // Germinal, masqué par le filtre, entre aussi.
        assertThat(order).containsExactly(germinal, trois, vicomte, vingt).inOrder()
        assertThat(env.collections.observeCollections().first().single().name).isEqualTo("Les Mousquetaires")
    }

    @Test
    fun toggleTwiceUnchecks() = runTest {
        val vm = viewModel()
        vm.loaded()
        vm.onToggle(germinal)
        vm.onToggle(germinal)
        assertThat(vm.state.first().selectedCount).isEqualTo(0)
    }
}

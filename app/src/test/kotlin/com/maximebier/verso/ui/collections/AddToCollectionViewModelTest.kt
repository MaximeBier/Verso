package com.maximebier.verso.ui.collections

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
class AddToCollectionViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: CollectionTestEnvironment
    private var germinal = 0L
    private var other = 0L
    private var zola = 0L
    private var dumas = 0L

    @Before
    fun setUp() = runTest {
        env = CollectionTestEnvironment(tmp.root)
        germinal = env.books.insert(testBook("g", title = "Germinal"))
        other = env.books.insert(testBook("o"))
        zola = env.collections.create("Les Rougon-Macquart", listOf(other, germinal))
        dumas = env.collections.create("Les Mousquetaires", listOf(other))
    }

    @After
    fun tearDown() = env.close()

    private fun viewModel() = AddToCollectionViewModel(germinal, env.collections, env.books)

    private suspend fun order(collectionId: Long) =
        env.collections.observeMemberships().first().filter { it.collectionId == collectionId }.map { it.bookId }

    @Test
    fun choicesReflectMembership() = runTest {
        val state = viewModel().state.first { !it.loading }
        assertThat(state.bookTitle).isEqualTo("Germinal")
        assertThat(state.choices.map { it.name }).containsExactly("Les Mousquetaires", "Les Rougon-Macquart").inOrder()
        assertThat(state.choices.map { it.checked }).containsExactly(false, true).inOrder()
        assertThat(state.choices.map { it.bookCount }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun toggleAddsLastThenRemoves() = runTest {
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.onToggle(dumas)
        assertThat(vm.state.first { s -> s.choices.first { it.id == dumas }.checked }.choices.first { it.id == dumas }.bookCount)
            .isEqualTo(2)
        assertThat(order(dumas)).containsExactly(other, germinal).inOrder()
        vm.onToggle(dumas)
        vm.state.first { s -> !s.choices.first { it.id == dumas }.checked }
        assertThat(order(dumas)).containsExactly(other)
    }

    @Test
    fun twoQuickTogglesAddThenRemove() = runTest {
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.onToggle(dumas)
        vm.onToggle(dumas)
        vm.state.first { s -> !s.choices.first { it.id == dumas }.checked }
        assertThat(order(dumas)).containsExactly(other)
    }
}

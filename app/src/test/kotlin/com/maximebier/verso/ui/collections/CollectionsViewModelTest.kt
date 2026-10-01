package com.maximebier.verso.ui.collections

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: CollectionTestEnvironment

    @Before
    fun setUp() {
        env = CollectionTestEnvironment(tmp.root)
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun newestCollectionFirstAndUpdatesFollowTheDatabase() = runTest {
        val a = env.books.insert(testBook("a"))
        val b = env.books.insert(testBook("b"))
        val zola = env.collections.create("Les Rougon-Macquart", listOf(a))
        env.collections.create("Les Mousquetaires", emptyList())
        val viewModel = CollectionsViewModel(env.collections, env.books, env.sessions.observeAllStats())

        viewModel.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.cards.map { it.name }).containsExactly("Les Mousquetaires", "Les Rougon-Macquart").inOrder()
            assertThat(state.cards.last().bookCount).isEqualTo(1)

            env.collections.add(zola, b)
            state = awaitItem()
            while (state.cards.last().bookCount != 2) state = awaitItem()
            assertThat(state.cards.last().covers).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

package com.maximebier.verso.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionDaoTest {
    private lateinit var db: VersoDatabase
    private lateinit var dao: CollectionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.collectionDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun books(vararg sha: String): List<Long> = sha.map { db.bookDao().insert(testBook(it)) }

    private suspend fun order(collectionId: Long): List<Long> =
        dao.observeMemberships().first().filter { it.collectionId == collectionId }.map { it.bookId }

    @Test
    fun createKeepsTheGivenOrderAndNewestCollectionComesFirst() = runTest {
        val (a, b, c) = books("a", "b", "c")
        val first = dao.create("Les Rougon-Macquart", createdAt = 1, bookIds = listOf(c, a, b))
        val second = dao.create("Les Mousquetaires", createdAt = 2, bookIds = emptyList())
        assertThat(order(first)).containsExactly(c, a, b).inOrder()
        assertThat(dao.observeAll().first().map { it.id }).containsExactly(second, first).inOrder()
    }

    @Test
    fun addGoesLastAndIsIdempotent() = runTest {
        val (a, b) = books("a", "b")
        val id = dao.create("Série", 1, listOf(a))
        dao.add(id, b)
        dao.add(id, b)
        dao.add(id, a)
        assertThat(order(id)).containsExactly(a, b).inOrder()
    }

    @Test
    fun removeLeavesTheOthersInOrderAndAddStillGoesLast() = runTest {
        val (a, b, c, d) = books("a", "b", "c", "d")
        val id = dao.create("Série", 1, listOf(a, b, c))
        dao.remove(id, b)
        dao.add(id, d)
        assertThat(order(id)).containsExactly(a, c, d).inOrder()
    }

    @Test
    fun reorderRewritesPositions() = runTest {
        val (a, b, c) = books("a", "b", "c")
        val id = dao.create("Série", 1, listOf(a, b, c))
        dao.reorder(id, listOf(c, a, b))
        assertThat(order(id)).containsExactly(c, a, b).inOrder()
        assertThat(dao.observeMemberships().first().map { it.position }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun aBookCanBelongToSeveralCollections() = runTest {
        val (a) = books("a")
        val x = dao.create("X", 1, listOf(a))
        val y = dao.create("Y", 2, listOf(a))
        assertThat(dao.observeCollectionIdsOf(a).first()).containsExactly(x, y)
    }

    @Test
    fun deletingABookRemovesItFromItsCollectionsOnly() = runTest {
        val (a, b) = books("a", "b")
        val id = dao.create("Série", 1, listOf(a, b))
        db.bookDao().deleteById(a)
        assertThat(order(id)).containsExactly(b)
        assertThat(dao.observeById(id).first()).isNotNull()
    }

    @Test
    fun deletingACollectionKeepsItsBooks() = runTest {
        val (a) = books("a")
        val id = dao.create("Série", 1, listOf(a))
        dao.delete(id)
        assertThat(dao.observeById(id).first()).isNull()
        assertThat(dao.observeMemberships().first()).isEmpty()
        assertThat(db.bookDao().byId(a)).isNotNull()
    }

    @Test
    fun rename() = runTest {
        val id = dao.create("Serie", 1, emptyList())
        dao.rename(id, "Série")
        assertThat(dao.observeById(id).first()!!.name).isEqualTo("Série")
    }
}

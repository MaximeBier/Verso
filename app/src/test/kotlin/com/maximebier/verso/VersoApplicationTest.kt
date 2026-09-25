package com.maximebier.verso

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VersoApplicationTest {

    @Test
    fun manifestDeclaresTheApplicationAndItBuildsTheContainer() = runTest {
        val app = ApplicationProvider.getApplicationContext<VersoApplication>()
        val container = app.container
        assertThat(container.database.bookDao()).isNotNull()
        assertThat(container.books.lastOpened()).isNull()
        assertThat(container.settings.reopenLastBook.first()).isTrue()
        assertThat(File(app.filesDir, "books").isDirectory).isTrue()
        assertThat(File(app.filesDir, "covers").isDirectory).isTrue()
        assertThat(container.clock()).isGreaterThan(0L)
    }
}

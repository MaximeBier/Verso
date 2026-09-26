package com.maximebier.verso.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [deleteRetrying] ne touche jamais réellement le disque ni n'attend ici : `delete`/`exists`/
 * `sleep` sont des doublures pour isoler la logique de reprise du vrai `File.delete()`.
 */
@RunWith(AndroidJUnit4::class)
class DeleteRetryingTest {

    private val anyFile = File("peu-importe.tmp")

    @Test
    fun succeedsOnFirstAttempt() {
        var deleteCalls = 0
        var sleepCalls = 0
        val result = deleteRetrying(
            file = anyFile,
            attempts = 5,
            delayMs = 0,
            sleep = { sleepCalls++ },
            delete = { deleteCalls++; true },
            exists = { true },
        )

        assertThat(result).isTrue()
        assertThat(deleteCalls).isEqualTo(1)
        assertThat(sleepCalls).isEqualTo(0)
    }

    @Test
    fun succeedsOnThirdAttempt() {
        var deleteCalls = 0
        var sleepCalls = 0
        val result = deleteRetrying(
            file = anyFile,
            attempts = 5,
            delayMs = 0,
            sleep = { sleepCalls++ },
            delete = { deleteCalls++; deleteCalls >= 3 },
            exists = { true },
        )

        assertThat(result).isTrue()
        assertThat(deleteCalls).isEqualTo(3)
        assertThat(sleepCalls).isEqualTo(2)
    }

    @Test
    fun treatsAnAlreadyAbsentFileAsSuccessWithoutRetrying() {
        var deleteCalls = 0
        var sleepCalls = 0
        val result = deleteRetrying(
            file = anyFile,
            attempts = 5,
            delayMs = 0,
            sleep = { sleepCalls++ },
            delete = { deleteCalls++; false },
            exists = { false },
        )

        assertThat(result).isTrue()
        assertThat(deleteCalls).isEqualTo(1)
        assertThat(sleepCalls).isEqualTo(0)
    }

    @Test
    fun failsAfterAllAttemptsWithoutThrowing() {
        var deleteCalls = 0
        var sleepCalls = 0
        val result = deleteRetrying(
            file = anyFile,
            attempts = 4,
            delayMs = 0,
            sleep = { sleepCalls++ },
            delete = { deleteCalls++; false },
            exists = { true },
        )

        assertThat(result).isFalse()
        assertThat(deleteCalls).isEqualTo(4)
        // Une pause entre chaque tentative, jamais après la dernière.
        assertThat(sleepCalls).isEqualTo(3)
    }
}

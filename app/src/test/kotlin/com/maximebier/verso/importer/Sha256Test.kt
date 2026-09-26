package com.maximebier.verso.importer

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256Test {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun knownVectors() {
        assertThat(Sha256.of(ByteArrayInputStream(ByteArray(0))))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertThat(Sha256.of(ByteArrayInputStream("abc".toByteArray())))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun streamsLargeFileLikeMessageDigest() {
        val bytes = Random(42).nextBytes(5 * 1024 * 1024 + 17)
        val file = tmp.newFile("gros.bin").apply { writeBytes(bytes) }
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        assertThat(Sha256.of(file)).isEqualTo(expected)
    }

    @Test
    fun copyAndHashCopiesSameBytes() {
        val bytes = Random(7).nextBytes(300_000)
        val out = ByteArrayOutputStream()
        val hash = Sha256.copyAndHash(ByteArrayInputStream(bytes), out)
        assertThat(out.toByteArray()).isEqualTo(bytes)
        assertThat(hash).isEqualTo(Sha256.of(ByteArrayInputStream(bytes)))
    }
}

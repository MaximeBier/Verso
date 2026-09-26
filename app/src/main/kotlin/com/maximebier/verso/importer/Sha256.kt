package com.maximebier.verso.importer

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Empreinte SHA-256 calculée en flux : le fichier n'est jamais chargé entièrement en mémoire. */
object Sha256 {

    private const val BUFFER_SIZE = 64 * 1024
    private const val ALGORITHM = "SHA-256"

    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun of(file: File): String = file.inputStream().use { of(it) }

    /** Copie `input` vers `output` et renvoie l'empreinte du contenu copié, en un seul passage. */
    fun copyAndHash(input: InputStream, output: OutputStream): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        output.flush()
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

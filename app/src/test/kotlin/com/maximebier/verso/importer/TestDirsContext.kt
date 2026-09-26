package com.maximebier.verso.importer

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/** Contexte dont filesDir et cacheDir pointent vers des dossiers de test (jamais les données de l'app). */
class TestDirsContext(base: Context, private val files: File, private val cache: File) : ContextWrapper(base) {
    override fun getFilesDir(): File = files
    override fun getCacheDir(): File = cache
    override fun getApplicationContext(): Context = this
}

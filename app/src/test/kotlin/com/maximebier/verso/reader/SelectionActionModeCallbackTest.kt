package com.maximebier.verso.reader

import android.view.View
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionActionModeCallbackTest {
    @Test
    fun nativeMenuIsEmptiedAndSelectionReported() {
        var started = 0
        var ended = 0
        val callback = SelectionActionModeCallback(onStarted = { started++ }, onEnded = { ended++ })
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val menu = PopupMenu(context, View(context)).menu.apply { add("Copier"); add("Partager") }
        assertThat(callback.onCreateActionMode(null, menu)).isTrue()
        assertThat(menu.size()).isEqualTo(0)
        menu.add("Tout sélectionner")
        assertThat(callback.onPrepareActionMode(null, menu)).isTrue()
        assertThat(menu.size()).isEqualTo(0)
        callback.onDestroyActionMode(null)
        assertThat(started to ended).isEqualTo(1 to 1)
    }
}

package com.maximebier.verso.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

/**
 * Menu de sélection d’Android remplacé par la barre de Verso (3.04) : le menu reste vide (aucune barre flottante),
 * et le début et la fin de la sélection sont signalés. Readium garde la sélection et ses poignées.
 */
internal class SelectionActionModeCallback(
    private val onStarted: () -> Unit,
    private val onEnded: () -> Unit,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu): Boolean {
        menu.clear()
        onStarted()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu): Boolean {
        menu.clear()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode?) {
        onEnded()
    }
}

package com.maximebier.verso.core.text

/**
 * Parcours en préordre (parent puis enfants, dans l’ordre) : l’unique ordre du sommaire dans l’app. Le rang d’une
 * entrée dans ce parcours sert d’identifiant entre la feuille du sommaire, ses ancres et le ViewModel.
 */
fun <T> preorder(roots: List<T>, children: (T) -> List<T>): List<T> =
    roots.flatMap { listOf(it) + preorder(children(it), children) }

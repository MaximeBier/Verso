package com.maximebier.verso.core.model

/**
 * Position dans un livre : locator Readium sérialisé (JSON) + progression totale 0..1.
 *
 * Jamais des pixels : le locator (chapitre + progression dans le chapitre + extrait) reste exact
 * quand la taille du texte change. `totalProgression` sert aux pourcentages et aux distances.
 */
data class BookPosition(val locatorJson: String, val totalProgression: Double)

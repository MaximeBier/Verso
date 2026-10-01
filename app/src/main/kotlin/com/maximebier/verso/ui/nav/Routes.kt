package com.maximebier.verso.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object LibraryRoute

@Serializable data class DetailsRoute(val bookId: Long)

@Serializable data class ReaderRoute(val bookId: Long, val openJournal: Boolean = false, val highlightId: Long? = null)

@Serializable data class NotesRoute(val bookId: Long)

@Serializable data object SettingsRoute

@Serializable data object LicensesRoute

@Serializable data object BackupRoute

@Serializable data class CollectionRoute(val collectionId: Long)

@Serializable data class NewCollectionRoute(val preselectedBookId: Long? = null)

package com.maximebier.verso.core.backup

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class BackupManifestTest {
    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)
    private val allEntries = setOf(
        BackupFormat.DATA_ENTRY,
        BackupFormat.SETTINGS_ENTRY,
        BackupFormat.bookEntry(shaA),
        BackupFormat.coverEntry("$shaA.jpg"),
        BackupFormat.bookEntry(shaB),
    )

    private fun manifest(
        format: Int = BackupFormat.VERSION,
        books: List<BackupBookRef> = listOf(BackupBookRef(1, shaA, "$shaA.jpg"), BackupBookRef(2, shaB, null)),
        sessions: List<BackupItemRef> = listOf(BackupItemRef(10, 1), BackupItemRef(11, 2)),
        highlights: List<BackupItemRef> = listOf(BackupItemRef(20, 2)),
        entries: Set<String> = allEntries,
        collections: List<BackupCollectionRef> = listOf(BackupCollectionRef(30, listOf(2, 1)), BackupCollectionRef(31, emptyList())),
    ) = BackupManifest(format, books, sessions, highlights, entries, collections)

    @Test
    fun completeBackupIsValid() {
        assertThat(manifest().check()).isEqualTo(BackupCheck.Valid)
    }

    @Test
    fun emptyLibraryIsValid() {
        val empty = manifest(
            books = emptyList(),
            sessions = emptyList(),
            highlights = emptyList(),
            entries = setOf(BackupFormat.DATA_ENTRY, BackupFormat.SETTINGS_ENTRY),
            collections = emptyList(),
        )
        assertThat(empty.check()).isEqualTo(BackupCheck.Valid)
    }

    @Test
    fun newerFormatIsRefusedBeforeAnythingElse() {
        assertThat(manifest(format = BackupFormat.VERSION + 1, entries = emptySet()).check()).isEqualTo(BackupCheck.NewerFormat)
        assertThat(BackupFormat.checkVersion(BackupFormat.VERSION + 1)).isEqualTo(BackupCheck.NewerFormat)
        assertThat(BackupFormat.checkVersion(BackupFormat.VERSION)).isEqualTo(BackupCheck.Valid)
    }

    @Test
    fun formatZeroIsInvalid() {
        assertThat(manifest(format = 0).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun missingDataOrSettingsIsInvalid() {
        assertThat(manifest(entries = allEntries - BackupFormat.DATA_ENTRY).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(entries = allEntries - BackupFormat.SETTINGS_ENTRY).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun missingEpubIsInvalid() {
        assertThat(manifest(entries = allEntries - BackupFormat.bookEntry(shaB)).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun missingCoverIsInvalid() {
        assertThat(manifest(entries = allEntries - BackupFormat.coverEntry("$shaA.jpg")).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun coverMustBeNamedAfterItsBook() {
        val wrongCover = listOf(BackupBookRef(1, shaA, "$shaB.jpg"), BackupBookRef(2, shaB, null))
        assertThat(manifest(books = wrongCover, entries = allEntries + BackupFormat.coverEntry("$shaB.jpg")).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun sessionOrHighlightOfAnAbsentBookIsInvalid() {
        assertThat(manifest(sessions = listOf(BackupItemRef(10, 3))).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(highlights = listOf(BackupItemRef(20, 3))).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun duplicateIdsOrFingerprintsAreInvalid() {
        val sameId = listOf(BackupBookRef(1, shaA, null), BackupBookRef(1, shaB, null))
        val sameSha = listOf(BackupBookRef(1, shaA, null), BackupBookRef(2, shaA, null))
        assertThat(manifest(books = sameId).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(books = sameSha).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(sessions = listOf(BackupItemRef(10, 1), BackupItemRef(10, 2))).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(highlights = listOf(BackupItemRef(20, 1), BackupItemRef(20, 2))).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun malformedFingerprintIsInvalid() {
        val evil = listOf(BackupBookRef(1, "../../donnees", null))
        assertThat(manifest(books = evil, sessions = emptyList(), highlights = emptyList()).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun onlyBackupEntriesAreKnown() {
        assertThat(BackupFormat.isKnownEntry(BackupFormat.DATA_ENTRY)).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.SETTINGS_ENTRY)).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.bookEntry(shaA))).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.coverEntry("$shaA.jpg"))).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.coverEntry("$shaA.png"))).isTrue()
        assertThat(BackupFormat.isKnownEntry("../donnees.json")).isFalse()
        assertThat(BackupFormat.isKnownEntry("livres/../../x.epub")).isFalse()
        assertThat(BackupFormat.isKnownEntry("livres/${shaA.uppercase()}.epub")).isFalse()
        assertThat(BackupFormat.isKnownEntry("couvertures/$shaA.gif")).isFalse()
        assertThat(BackupFormat.isKnownEntry("mimetype")).isFalse()
    }

    @Test
    fun bookFingerprintIsReadFromTheEntryName() {
        assertThat(BackupFormat.bookSha256(BackupFormat.bookEntry(shaA))).isEqualTo(shaA)
        assertThat(BackupFormat.bookSha256(BackupFormat.DATA_ENTRY)).isNull()
        assertThat(BackupFormat.bookSha256("livres/abc.epub")).isNull()
    }

    @Test
    fun fileNameCarriesTheDate() {
        assertThat(BackupFormat.fileName(LocalDate.of(2026, 9, 20))).isEqualTo("verso-sauvegarde-2026-09-20.zip")
}

    @Test
    fun formatsOneAndTwoAreReadAndThreeIsNewer() {
        assertThat(BackupFormat.VERSION).isEqualTo(2)
        assertThat(manifest(format = 1, collections = emptyList()).check()).isEqualTo(BackupCheck.Valid)
        assertThat(manifest(format = 2).check()).isEqualTo(BackupCheck.Valid)
        assertThat(manifest(format = 3).check()).isEqualTo(BackupCheck.NewerFormat)
    }

    @Test
    fun collectionsMustBeConsistent() {
        assertThat(manifest(collections = listOf(BackupCollectionRef(0, emptyList()))).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(collections = listOf(BackupCollectionRef(30, emptyList()), BackupCollectionRef(30, emptyList()))).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(collections = listOf(BackupCollectionRef(30, listOf(1, 9)))).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(collections = listOf(BackupCollectionRef(30, listOf(1, 1)))).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }
}

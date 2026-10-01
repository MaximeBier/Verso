package com.maximebier.verso.backup

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupBookRef
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.core.backup.BackupItemRef
import com.maximebier.verso.data.RawSetting
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupMappingTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sha = "c".repeat(64)

    @Test
    fun bookKeepsItsDataAndGetsThePathsOfThisPhone() {
        val book = testBook(sha, title = "Madame Bovary", author = "Gustave Flaubert", filePath = "/ancien/books/$sha.epub", coverPath = "/ancien/covers/$sha.jpg")
            .copy(id = 7, lastOpenedAt = 99L, readingLocatorJson = """{"href":"c2.xhtml"}""", progression = 0.31, scrollMode = "PAGES", stateOverride = "FINISHED")
        val booksDir = tmp.newFolder("books")
        val coversDir = tmp.newFolder("covers")

        val dto = book.toDto(coverFile = "$sha.jpg")
        val restored = dto.toEntity(booksDir, coversDir)

        assertThat(dto.coverFile).isEqualTo("$sha.jpg")
        assertThat(restored).isEqualTo(
            book.copy(filePath = File(booksDir, "$sha.epub").absolutePath, coverPath = File(coversDir, "$sha.jpg").absolutePath),
        )
    }

    @Test
    fun bookWithoutCoverHasNoCoverPath() {
        val dto = testBook(sha).copy(id = 1).toDto(coverFile = null)
        assertThat(dto.toEntity(tmp.newFolder("b"), tmp.newFolder("c")).coverPath).isNull()
    }

    @Test
    fun sessionsAndHighlightsAreUnchanged() {
        val session = testSession(bookId = 7, startedAt = 1_000L, id = 42)
        val highlight = HighlightEntity(
            id = 13, bookId = 7, locatorJson = """{"href":"c1.xhtml"}""", text = "Elle s’ennuyait", note = "Le cœur du livre",
            progression = 0.12, chapterPath = "Première partie\nI", createdAt = 5L, updatedAt = 6L,
        )
        assertThat(session.toDto().toEntity()).isEqualTo(session)
        assertThat(highlight.toDto().chapterPath).containsExactly("Première partie", "I").inOrder()
        assertThat(highlight.toDto().toEntity()).isEqualTo(highlight)
    }

    @Test
    fun everySettingTypeSurvivesJson() {
        val settings = listOf(
            RawSetting("b", true), RawSetting("d", 1.5), RawSetting("f", 2.5f), RawSetting("i", 3),
            RawSetting("l", 4L), RawSetting("s", "texte"), RawSetting("set", setOf("x", "y")),
        )
        val json = BackupJson.encodeToString(SettingsFileDto.serializer(), SettingsFileDto(settings.map { it.toDto() }))
        val back = BackupJson.decodeFromString(SettingsFileDto.serializer(), json).settings.map { it.toRawSetting() }
        assertThat(back).isEqualTo(settings)
    }

    @Test
    fun unknownSettingTypeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { SettingDto("x", "date", JsonPrimitive("hier")).toRawSetting() }
        assertThrows(IllegalArgumentException::class.java) { SettingDto("x", "int", JsonPrimitive("pas un nombre")).toRawSetting() }
    }

    @Test
    fun headerIsReadWhateverTheRestOfTheFile() {
        val header = BackupJson.decodeFromString(BackupHeaderDto.serializer(), """{"format":2,"livres":{"nouveau":true}}""")
        assertThat(header.format).isEqualTo(2)
    }

    @Test
    fun dataSurvivesJsonAndDescribesItsManifest() {
        val data = BackupDataDto(
            format = BackupFormat.VERSION,
            createdAt = 1L,
            appVersion = "3.0.0",
            books = listOf(testBook(sha).copy(id = 7).toDto(coverFile = "$sha.jpg")),
            sessions = listOf(testSession(bookId = 7, startedAt = 1L, id = 42).toDto()),
            highlights = emptyList(),
        )
        val back = BackupJson.decodeFromString(BackupDataDto.serializer(), BackupJson.encodeToString(BackupDataDto.serializer(), data))
        assertThat(back).isEqualTo(data)

        val manifest = back.manifest(setOf("donnees.json"))
        assertThat(manifest.books).containsExactly(BackupBookRef(7, sha, "$sha.jpg"))
        assertThat(manifest.sessions).containsExactly(BackupItemRef(42, 7))
        assertThat(manifest.entries).containsExactly("donnees.json")
    }
}

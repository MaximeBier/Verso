package com.maximebier.verso.core.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotesMarkdownTest {
    @Test fun groupsByChapterInBookOrder() {
        val markdown = notesMarkdown(
            title = "Madame Bovary",
            meta = "Gustave Flaubert · 3 éléments · 1 octobre 2026",
            items = listOf(
                NotesExportItem("Première partie, chapitre I", "Il avait les cheveux coupés droit sur le front.", "Premier portrait de Charles."),
                NotesExportItem("Deuxième partie, chapitre I", "la campagne ainsi ressemble à un grand manteau", "Image du manteau :\nla prairie verte."),
                NotesExportItem("Deuxième partie, chapitre I", "On l’aperçoit de loin", null),
            ),
        )
        assertThat(markdown).isEqualTo(
            """
            # Madame Bovary

            Gustave Flaubert · 3 éléments · 1 octobre 2026

            ## Première partie, chapitre I

            > Il avait les cheveux coupés droit sur le front.

            Premier portrait de Charles.

            ## Deuxième partie, chapitre I

            > la campagne ainsi ressemble à un grand manteau

            Image du manteau :
            la prairie verte.

            > On l’aperçoit de loin

            """.trimIndent(),
        )
    }

    @Test fun passagesWithoutChapterComeWithoutHeading() {
        val markdown = notesMarkdown("Livre", "2 éléments · 1 octobre 2026", listOf(NotesExportItem(null, "a", null), NotesExportItem("Chapitre 1", "b", null)))
        assertThat(markdown).isEqualTo("# Livre\n\n2 éléments · 1 octobre 2026\n\n> a\n\n## Chapitre 1\n\n> b\n")
    }

    @Test fun markdownInTheTextIsKeptReadable() {
        // Un passage sur plusieurs lignes reste une seule citation ; un titre qui commence par # n’en devient pas un.
        val markdown = notesMarkdown("# Titre", "0 élément", listOf(NotesExportItem(null, "ligne 1\nligne 2", null)))
        assertThat(markdown).startsWith("# \\# Titre\n")
        assertThat(markdown).contains("> ligne 1\n> ligne 2\n")
    }
}


package com.maximebier.verso.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icône au trait 24 × 24 (fill none, bouts et jonctions arrondis), teintée par Icon(tint = …). */
private fun strokeIcon(name: String, vararg paths: String, strokeWidth: Float = 2f): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

/** Icône pleine 24 × 24 (⋮). */
private fun fillIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply { paths.forEach { d -> addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)) } }
        .build()

object VersoIcons {
    val Settings: ImageVector = strokeIcon(
        "settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6z",
    )
    val Plus: ImageVector = strokeIcon("plus", "M12 5v14M5 12h14")
    val Minus: ImageVector = strokeIcon("minus", "M5 12h14")
    val ArrowRight: ImageVector = strokeIcon("arrow-right", "M5 12h14M13 6l6 6-6 6", strokeWidth = 2.2f)
    val ArrowLeft: ImageVector = strokeIcon("arrow-left", "M19 12H5M11 18l-6-6 6-6")
    val List: ImageVector = strokeIcon("list", "M4 6h16M4 12h16M4 18h16")
    val Grid: ImageVector = strokeIcon("grid", "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z")
    val Check: ImageVector = strokeIcon("check", "M5 12.5l4.5 4.5L19 7.5", strokeWidth = 2.4f)
    val CheckSwitch: ImageVector = strokeIcon("check-switch", "M5 12.5l4.5 4.5L19 7.5", strokeWidth = 2.6f)
    val MoreVertical: ImageVector = fillIcon(
        "more-vertical",
        "M12 3.9a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z",
        "M12 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z",
        "M12 16.9a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z",
    )
    val Info: ImageVector = strokeIcon("info", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 16v-4.5M12 8h.01")
    val Trash: ImageVector = strokeIcon("trash", "M4 7h16M9 7V4.5h6V7M18 7l-.9 13H6.9L6 7M10 11v6M14 11v6")
    val AlertTriangle: ImageVector = strokeIcon("alert-triangle", "M12 4 2.5 20h19z", "M12 10v4.5M12 17.5h.01")
    private const val BOOK_LEFT = "M4 5.5A1.5 1.5 0 0 1 5.5 4H10a2 2 0 0 1 2 2v14a1.5 1.5 0 0 0-1.5-1.5h-5A1.5 1.5 0 0 1 4 17z"
    private const val BOOK_RIGHT = "M20 5.5A1.5 1.5 0 0 0 18.5 4H14a2 2 0 0 0-2 2v14a1.5 1.5 0 0 1 1.5-1.5h5a1.5 1.5 0 0 0 1.5-1.5z"
    val BookOpen: ImageVector = strokeIcon("book-open", BOOK_LEFT, BOOK_RIGHT)
    val ExternalLink: ImageVector = strokeIcon(
        "external-link",
        "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5",
    )
    val ChevronRight: ImageVector = strokeIcon("chevron-right", "m9 6 6 6-6 6")
    val ChevronDown: ImageVector = strokeIcon("chevron-down", "m6 9 6 6 6-6")
    val ChevronUp: ImageVector = strokeIcon("chevron-up", "m18 15-6-6-6 6")
    val GripVertical: ImageVector = strokeIcon(
        "grip-vertical",
        "M9 5h.01M9 12h.01M9 19h.01M15 5h.01M15 12h.01M15 19h.01",
        strokeWidth = 3f,
    )
    val ListPlus: ImageVector = strokeIcon("list-plus", "M11 12H3M16 6H3M16 18H3M18 9v6M21 12h-6")
    val SortArrows: ImageVector = strokeIcon("sort-arrows", "M7 4v16M4 7l3-3 3 3M17 20V4M14 17l3 3 3-3")
    val ListBullets: ImageVector = strokeIcon("list-bullets", "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01")
    val History: ImageVector = strokeIcon(
        "history",
        "M3.5 12a8.5 8.5 0 1 0 2.6-6.1L3.5 8.5",
        "M3.5 4v4.5H8",
        "M12 7.5V12l3 2",
    )
    val Search: ImageVector = strokeIcon("search", "M11 4a7 7 0 1 1 0 14a7 7 0 1 1 0-14z", "m20 20-3.5-3.5")
    val Close: ImageVector = strokeIcon("x", "M18 6 6 18M6 6l12 12")
    val Bookmark: ImageVector = strokeIcon("bookmark", "M7 4h10v16l-5-3.5L7 20z")
    val Undo: ImageVector = strokeIcon("undo", "M9 14 4 9l5-5", "M4 9h11a5 5 0 0 1 0 10h-3")

    // V3 : tracés des maquettes 3.04, 3.06, 3.07 et 3.10.
    val Translate: ImageVector = strokeIcon("translate", "m5 8 6 6", "m4 14 6-6 2-3", "M2 5h12", "M7 2h1", "m22 22-5-10-5 10", "M14 18h6")
    val Retry: ImageVector = strokeIcon("retry", "M3.5 12a8.5 8.5 0 1 0 2.6-6.1L3.5 8.5M3.5 4v4.5H8")
    val Note: ImageVector = strokeIcon("note", "M20 15a2 2 0 0 1-2 2H8l-4 4V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2z", "M8 9h8M8 13h5")
    val Copy: ImageVector = strokeIcon("copy", "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M4 16V6a2 2 0 0 1 2-2h10")
    val Download: ImageVector = strokeIcon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Upload: ImageVector = strokeIcon("upload", "M12 20V9M7 14l5-5 5 5M5 4h14")
}

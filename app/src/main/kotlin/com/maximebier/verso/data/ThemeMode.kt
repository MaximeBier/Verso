package com.maximebier.verso.data

/** Palette affichée (jetons de tokens.json). */
enum class AppTheme(val isDark: Boolean) { LIGHT(false), SEPIA(false), DARK(true), BLACK(true) }

/** Thème de l'application : automatique (suit le téléphone), ou une palette imposée. Ordre des Paramètres. */
enum class ThemeMode {
    AUTO,
    LIGHT,
    SEPIA,
    DARK,
    BLACK,
    ;

    /** Palette affichée, `systemDark` étant le thème du téléphone. */
    fun resolve(systemDark: Boolean): AppTheme = when (this) {
        AUTO -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
        LIGHT -> AppTheme.LIGHT
        SEPIA -> AppTheme.SEPIA
        DARK -> AppTheme.DARK
        BLACK -> AppTheme.BLACK
    }

    /** Thème sombre affiché. */
    fun isDark(systemDark: Boolean): Boolean = resolve(systemDark).isDark
}

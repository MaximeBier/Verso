package com.maximebier.verso.data

/** Palette affichée (jetons de tokens.json). Sépia et noir arrivent en V2. */
enum class AppTheme(val isDark: Boolean) { LIGHT(false), SEPIA(false), DARK(true), BLACK(true) }

/** Thème de l'application : automatique (suit le téléphone), ou une palette imposée. */
enum class ThemeMode {
    AUTO,
    LIGHT,
    DARK,
    ;

    /** Palette affichée, `systemDark` étant le thème du téléphone. */
    fun resolve(systemDark: Boolean): AppTheme = when (this) {
        AUTO -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
        LIGHT -> AppTheme.LIGHT
        DARK -> AppTheme.DARK
    }

    /** Thème sombre affiché. */
    fun isDark(systemDark: Boolean): Boolean = resolve(systemDark).isDark
}

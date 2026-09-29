package com.maximebier.verso.data

/** Palette affichée (jetons de tokens.json). */
enum class AppTheme(val isDark: Boolean) { LIGHT(false), SEPIA(false), DARK(true), NIGHT(true) }

/** Thème foncé appliqué en automatique quand le téléphone est en sombre (Paramètres › Affichage › Thème sombre). */
enum class DarkThemeVariant(val theme: AppTheme) { DARK(AppTheme.DARK), NIGHT(AppTheme.NIGHT) }

/** Thème de l'application : automatique (suit le téléphone), ou une palette imposée. Ordre des Paramètres. */
enum class ThemeMode {
    AUTO,
    LIGHT,
    SEPIA,
    DARK,
    NIGHT,
    ;

    /** Palette affichée, `systemDark` étant le thème du téléphone et [darkVariant] le thème sombre choisi. */
    fun resolve(systemDark: Boolean, darkVariant: DarkThemeVariant = DarkThemeVariant.DARK): AppTheme = when (this) {
        AUTO -> if (systemDark) darkVariant.theme else AppTheme.LIGHT
        LIGHT -> AppTheme.LIGHT
        SEPIA -> AppTheme.SEPIA
        DARK -> AppTheme.DARK
        NIGHT -> AppTheme.NIGHT
    }

    /** Thème sombre affiché. */
    fun isDark(systemDark: Boolean): Boolean = resolve(systemDark).isDark
}

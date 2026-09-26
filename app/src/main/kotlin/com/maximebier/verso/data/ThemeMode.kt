package com.maximebier.verso.data

/** Thème de l'application : automatique (suit le téléphone), ou clair ou sombre imposé. */
enum class ThemeMode {
    AUTO,
    LIGHT,
    DARK,
    ;

    /** Thème sombre affiché, `systemDark` étant celui du téléphone. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        AUTO -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

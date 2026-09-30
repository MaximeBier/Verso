package com.maximebier.verso.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.DarkThemeVariant
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** État de l'écran Paramètres (1.09 / 2.09). */
data class SettingsUiState(
    val reopenLastBook: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val darkThemeVariant: DarkThemeVariant = DarkThemeVariant.DARK,
    val readingSettings: ReadingSettings = ReadingSettings(),
    val showStatistics: Boolean = true,
    val confirmingClearJournal: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val confirming = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        combine(settings.reopenLastBook, settings.themeMode, settings.darkThemeVariant, ::Triple),
        settings.readingSettings,
        settings.showStatistics,
        confirming,
    ) { (reopen, theme, darkVariant), reading, stats, confirmingClear ->
        SettingsUiState(
            reopenLastBook = reopen,
            themeMode = theme,
            darkThemeVariant = darkVariant,
            readingSettings = reading,
            showStatistics = stats,
            confirmingClearJournal = confirmingClear,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onReopenLastBookChange(value: Boolean) {
        viewModelScope.launch { settings.setReopenLastBook(value) }
    }

    fun onThemeModeChange(value: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(value) }
    }

    fun onDarkThemeVariantChange(value: DarkThemeVariant) {
        viewModelScope.launch { settings.setDarkThemeVariant(value) }
    }

    fun onFontChange(value: ReadingFont) {
        viewModelScope.launch { settings.updateReadingSettings { it.copy(font = value) } }
    }

    fun onFontSizeChange(value: Int) {
        viewModelScope.launch { settings.updateReadingSettings { it.withFontSize(value) } }
    }

    fun onDefaultScrollModeChange(value: ScrollMode) {
        viewModelScope.launch { settings.updateReadingSettings { it.copy(defaultScrollMode = value) } }
    }

    fun onShowStatisticsChange(value: Boolean) {
        viewModelScope.launch { settings.setShowStatistics(value) }
    }

    fun onClearJournalClick() {
        confirming.value = true
    }

    fun onClearJournalDismiss() {
        confirming.value = false
    }

    /** Efface toutes les sessions ; les positions de lecture (table books) ne sont pas touchées. */
    fun onClearJournalConfirm() {
        confirming.value = false
        viewModelScope.launch { sessions.clearAll() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication
                SettingsViewModel(app.container.settings, app.container.sessions)
            }
        }
    }
}

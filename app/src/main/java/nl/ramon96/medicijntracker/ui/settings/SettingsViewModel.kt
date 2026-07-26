package nl.ramon96.medicijntracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.data.prefs.AppFont
import nl.ramon96.medicijntracker.data.prefs.AppSettings
import nl.ramon96.medicijntracker.data.prefs.ThemeMode
import nl.ramon96.medicijntracker.data.prefs.ThemeSettings
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.update.DownloadProgress
import nl.ramon96.medicijntracker.update.UpdateCheckWorker
import nl.ramon96.medicijntracker.update.UpdateError
import nl.ramon96.medicijntracker.update.UpdateInfo
import nl.ramon96.medicijntracker.update.UpdateResult

/** What the update panel is doing right now. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val fraction: Float?) : UpdateUiState
    data object ReadyToInstall : UpdateUiState
    data class Failed(val error: UpdateError) : UpdateUiState
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val defaultSnoozeMinutes: StateFlow<Int> = container.settings.defaultSnoozeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT_SNOOZE_MINUTES)

    val refillThrottleDays: StateFlow<Int> = container.settings.refillThrottleDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT_REFILL_THROTTLE_DAYS)

    val theme: StateFlow<ThemeSettings> = container.settings.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSettings())

    val barcodeLookup: StateFlow<Boolean> = container.settings.barcodeLookup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autoUpdateCheck: StateFlow<Boolean> = container.settings.autoUpdateCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _update = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    // --- reminders ---------------------------------------------------------

    fun setDefaultSnooze(minutes: Int) = viewModelScope.launch {
        container.settings.setDefaultSnoozeMinutes(minutes)
    }

    fun setRefillThrottle(days: Int) = viewModelScope.launch {
        container.settings.setRefillThrottleDays(days)
    }

    // --- appearance --------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { container.settings.setThemeMode(mode) }
    fun setPalette(id: String) = viewModelScope.launch { container.settings.setPalette(id) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { container.settings.setDynamicColor(on) }
    fun setHighContrast(on: Boolean) = viewModelScope.launch { container.settings.setHighContrast(on) }
    fun setTextScale(scale: Float) = viewModelScope.launch { container.settings.setTextScale(scale) }
    fun setFont(font: AppFont) = viewModelScope.launch { container.settings.setFont(font) }

    fun setBarcodeLookup(enabled: Boolean) =
        viewModelScope.launch { container.settings.setBarcodeLookup(enabled) }

    // --- updates -----------------------------------------------------------

    fun setAutoUpdateCheck(enabled: Boolean, context: android.content.Context) =
        viewModelScope.launch {
            container.settings.setAutoUpdateCheck(enabled)
            if (enabled) UpdateCheckWorker.schedule(context) else UpdateCheckWorker.cancel(context)
        }

    fun checkForUpdate() = viewModelScope.launch {
        _update.value = UpdateUiState.Checking
        _update.value = when (val result = container.updateChecker.check()) {
            is UpdateResult.Available -> UpdateUiState.Available(result.info)
            is UpdateResult.UpToDate -> UpdateUiState.UpToDate
            is UpdateResult.Failed -> UpdateUiState.Failed(result.error)
        }
    }

    fun downloadAndInstall(info: UpdateInfo) = viewModelScope.launch {
        container.updateDownloader.clearOldDownloads()
        container.updateDownloader.download(info).collect { progress ->
            when (progress) {
                is DownloadProgress.Running -> _update.value = UpdateUiState.Downloading(progress.fraction)
                is DownloadProgress.Failed -> _update.value = UpdateUiState.Failed(progress.error)
                is DownloadProgress.Done -> {
                    _update.value = UpdateUiState.ReadyToInstall
                    // Android takes over from here and shows its own install confirmation.
                    container.updateDownloader.install(progress.file)
                }
            }
        }
    }

    fun dismissUpdateMessage() {
        _update.value = UpdateUiState.Idle
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

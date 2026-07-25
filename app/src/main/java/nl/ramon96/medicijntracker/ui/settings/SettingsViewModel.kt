package nl.ramon96.medicijntracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.data.prefs.AppSettings
import nl.ramon96.medicijntracker.di.AppContainer

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val defaultSnoozeMinutes: StateFlow<Int> = container.settings.defaultSnoozeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT_SNOOZE_MINUTES)

    val refillThrottleDays: StateFlow<Int> = container.settings.refillThrottleDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT_REFILL_THROTTLE_DAYS)

    fun setDefaultSnooze(minutes: Int) = viewModelScope.launch {
        container.settings.setDefaultSnoozeMinutes(minutes)
    }

    fun setRefillThrottle(days: Int) = viewModelScope.launch {
        container.settings.setRefillThrottleDays(days)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

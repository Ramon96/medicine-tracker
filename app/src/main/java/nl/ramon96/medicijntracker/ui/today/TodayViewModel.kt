package nl.ramon96.medicijntracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.domain.model.IntakeStatus
import nl.ramon96.medicijntracker.domain.model.IntakeWithMedicine
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.stock.ExpiryRisk
import nl.ramon96.medicijntracker.domain.stock.ExpiryWatcher
import nl.ramon96.medicijntracker.domain.stock.RefillForecast
import nl.ramon96.medicijntracker.domain.stock.StockForecaster
import java.time.LocalDate

data class RefillWarning(val medicine: Medicine, val forecast: RefillForecast)

/** A package that will be over its date before it is finished, or already is. */
data class ExpiryWarning(val medicine: Medicine, val risk: ExpiryRisk)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val open: List<IntakeWithMedicine> = emptyList(),
    val done: List<IntakeWithMedicine> = emptyList(),
    val refillWarnings: List<RefillWarning> = emptyList(),
    val expiryWarnings: List<ExpiryWarning> = emptyList(),
    val hasMedicines: Boolean = true,
) {
    val nextDose: IntakeWithMedicine? get() = open.minByOrNull { it.intake.remindAt }
}

class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> = date
        .flatMapLatest { day ->
            combine(
                container.doseRepository.observeDay(day),
                container.medicineRepository.observeAll(),
            ) { doses, medicines ->
                val (done, open) = doses.partition {
                    it.intake.status == IntakeStatus.TAKEN ||
                        it.intake.status == IntakeStatus.SKIPPED ||
                        it.intake.status == IntakeStatus.MISSED
                }
                TodayUiState(
                    date = day,
                    open = open.sortedBy { it.intake.remindAt },
                    done = done.sortedBy { it.intake.scheduledAt },
                    refillWarnings = medicines.mapNotNull { medicine ->
                        val forecast = StockForecaster.forecast(medicine, day)
                        RefillWarning(medicine, forecast).takeIf { forecast.shouldOrderNow }
                    },
                    expiryWarnings = medicines.mapNotNull { medicine ->
                        val risk = ExpiryWatcher.risk(medicine, day) ?: return@mapNotNull null
                        ExpiryWarning(medicine, risk).takeIf { risk.worthWarningAbout }
                    },
                    hasMedicines = medicines.isNotEmpty(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    init {
        // Generates any missing doses for today and re-arms alarms, so opening the app is always
        // enough to get things back on track.
        viewModelScope.launch { container.reminderCoordinator.refreshAll() }
    }

    /** Called when the screen resumes, so the app does not stay stuck on yesterday. */
    fun refresh() {
        date.value = LocalDate.now()
        viewModelScope.launch { container.reminderCoordinator.refreshAll() }
    }

    fun markTaken(intakeId: Long) = viewModelScope.launch {
        container.doseRepository.markTaken(intakeId)
        container.reminderCoordinator.onDoseSettled(intakeId)
    }

    fun markSkipped(intakeId: Long) = viewModelScope.launch {
        container.doseRepository.markSkipped(intakeId)
        container.reminderCoordinator.onDoseSettled(intakeId)
    }

    fun undo(intakeId: Long) = viewModelScope.launch {
        container.doseRepository.undo(intakeId)
        container.reminderCoordinator.refreshAll()
    }

    fun snooze(intakeId: Long, minutes: Int) = viewModelScope.launch {
        container.reminderCoordinator.snooze(intakeId, minutes)
    }

    /** "Dit doosje is op" - the stored expiry date belonged to a box that no longer exists. */
    fun clearExpiry(medicine: Medicine) = viewModelScope.launch {
        container.medicineRepository.setExpiryDate(medicine.id, null)
    }

    /** "Nieuwe verpakking ontvangen" - puts a full package back into stock. */
    fun addPackage(medicine: Medicine) = viewModelScope.launch {
        val units = medicine.stock.unitsPerPackage.takeIf { it > 0 } ?: return@launch
        container.medicineRepository.adjustStock(medicine.id, units)
        container.reminderCoordinator.onStockChanged()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TodayViewModel(container) }
        }
    }
}

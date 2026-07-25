package nl.ramon96.medicijntracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.domain.model.IntakeStatus
import nl.ramon96.medicijntracker.domain.model.IntakeWithMedicine
import nl.ramon96.medicijntracker.domain.model.Medicine
import java.time.LocalDate
import java.time.YearMonth

/** How a single day went, used to colour the calendar cell. */
enum class DayResult { NONE, ALL_TAKEN, PARTIAL, NOTHING_TAKEN }

data class DaySummary(
    val date: LocalDate,
    val planned: Int,
    val taken: Int,
    val result: DayResult,
)

data class MedicineAdherence(
    val medicine: Medicine,
    val taken: Int,
    val settled: Int,
) {
    /** Share of settled doses that were actually taken, 0..1. Null when nothing was due yet. */
    val ratio: Float? get() = if (settled == 0) null else taken.toFloat() / settled
}

data class HistoryUiState(
    val month: YearMonth = YearMonth.now(),
    val days: List<DaySummary> = emptyList(),
    val adherence: List<MedicineAdherence> = emptyList(),
)

class HistoryViewModel(container: AppContainer) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HistoryUiState> = month
        .flatMapLatest { yearMonth ->
            container.doseRepository
                .observeRange(yearMonth.atDay(1), yearMonth.atEndOfMonth())
                .map { intakes -> buildState(yearMonth, intakes) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (month.value < YearMonth.now()) month.value = month.value.plusMonths(1)
    }

    private fun buildState(yearMonth: YearMonth, intakes: List<IntakeWithMedicine>): HistoryUiState {
        val byDate = intakes.groupBy { it.intake.scheduledAt.toLocalDate() }
        val today = LocalDate.now()

        val days = (1..yearMonth.lengthOfMonth()).map { dayOfMonth ->
            val date = yearMonth.atDay(dayOfMonth)
            val doses = byDate[date].orEmpty()
            val taken = doses.count { it.intake.status == IntakeStatus.TAKEN }
            val result = when {
                doses.isEmpty() || date.isAfter(today) -> DayResult.NONE
                taken == doses.size -> DayResult.ALL_TAKEN
                taken == 0 -> DayResult.NOTHING_TAKEN
                else -> DayResult.PARTIAL
            }
            DaySummary(date = date, planned = doses.size, taken = taken, result = result)
        }

        val adherence = intakes
            .groupBy { it.medicine.id }
            .mapNotNull { (_, doses) ->
                val medicine = doses.first().medicine
                // Only doses that have been resolved count; today's pending ones would
                // otherwise drag the percentage down all day.
                val settled = doses.count {
                    it.intake.status == IntakeStatus.TAKEN ||
                        it.intake.status == IntakeStatus.SKIPPED ||
                        it.intake.status == IntakeStatus.MISSED
                }
                MedicineAdherence(
                    medicine = medicine,
                    taken = doses.count { it.intake.status == IntakeStatus.TAKEN },
                    settled = settled,
                )
            }
            .sortedBy { it.medicine.name }

        return HistoryUiState(month = yearMonth, days = days, adherence = adherence)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container) }
        }
    }
}

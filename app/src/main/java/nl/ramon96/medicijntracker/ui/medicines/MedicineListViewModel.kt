package nl.ramon96.medicijntracker.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.stock.RefillForecast
import nl.ramon96.medicijntracker.domain.stock.StockForecaster
import java.time.LocalDate

data class MedicineRow(val medicine: Medicine, val forecast: RefillForecast)

class MedicineListViewModel(private val container: AppContainer) : ViewModel() {

    val medicines: StateFlow<List<MedicineRow>> = container.medicineRepository.observeAll()
        .map { list ->
            val today = LocalDate.now()
            list.map { MedicineRow(it, StockForecaster.forecast(it, today)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(medicine: Medicine) = viewModelScope.launch {
        container.medicineRepository.delete(medicine)
        container.reminderCoordinator.onMedicineDeleted(medicine.id)
    }

    fun addPackage(medicine: Medicine) = viewModelScope.launch {
        val units = medicine.stock.unitsPerPackage.takeIf { it > 0 } ?: return@launch
        container.medicineRepository.adjustStock(medicine.id, units)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MedicineListViewModel(container) }
        }
    }
}

package nl.ramon96.medicijntracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.domain.barcode.ScannedCode
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.stock.ExpiryWatcher
import nl.ramon96.medicijntracker.scan.LookupError
import nl.ramon96.medicijntracker.scan.LookupResult
import nl.ramon96.medicijntracker.scan.ProductSuggestion
import nl.ramon96.medicijntracker.scan.ScanError
import nl.ramon96.medicijntracker.scan.ScanOutcome
import nl.ramon96.medicijntracker.ui.today.formatAmount

/**
 * What the sheet shows after a scan.
 *
 * The states follow the three ways a barcode can be resolved, in the order they are tried: a code
 * this app has seen before, a code some public database knows, and a code nobody knows - which is
 * the normal outcome for Dutch pharmacy products and therefore has to be a comfortable path
 * rather than an error.
 */
sealed interface ScanUiState {
    data object Idle : ScanUiState

    /** The package is already linked to a medicine: offer to top up the stock. */
    data class Recognised(
        val medicine: Medicine,
        val code: ScannedCode,
        val amount: String,
    ) : ScanUiState

    data class LookingUp(val code: ScannedCode) : ScanUiState

    /** Something was found online. A suggestion to check, never something to save silently. */
    data class Suggested(
        val code: ScannedCode,
        val suggestion: ProductSuggestion,
    ) : ScanUiState

    /** Nothing known about this code, either because it is new or because we could not ask. */
    data class Unknown(
        val code: ScannedCode,
        val lookupError: LookupError? = null,
    ) : ScanUiState

    data class Failed(val error: ScanError, val detail: String? = null) : ScanUiState
}

class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Offered when an unknown code should be attached to a medicine that already exists. */
    val medicines: StateFlow<List<Medicine>> = container.medicineRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lookupJob: Job? = null

    fun onScanResult(outcome: ScanOutcome) {
        lookupJob?.cancel()
        when (outcome) {
            // Backing out of the scanner should leave no trace.
            is ScanOutcome.Cancelled -> _state.value = ScanUiState.Idle
            is ScanOutcome.Failed -> _state.value = ScanUiState.Failed(outcome.error, outcome.detail)
            is ScanOutcome.Scanned -> resolve(outcome.code)
        }
    }

    private fun resolve(code: ScannedCode) {
        val gtin = code.gtin
        // An unreadable code still gets the user to the form, with whatever was decoded.
        if (gtin == null) {
            _state.value = ScanUiState.Unknown(code)
            return
        }

        lookupJob = viewModelScope.launch {
            container.medicineRepository.findByBarcode(gtin)?.let { medicine ->
                _state.value = ScanUiState.Recognised(
                    medicine = medicine,
                    code = code,
                    amount = defaultAmountFor(medicine),
                )
                return@launch
            }

            if (!container.settings.currentBarcodeLookup()) {
                _state.value = ScanUiState.Unknown(code)
                return@launch
            }

            _state.value = ScanUiState.LookingUp(code)
            _state.value = when (val result = container.productLookup.lookup(code)) {
                is LookupResult.Found -> ScanUiState.Suggested(code, result.suggestion)
                is LookupResult.NotFound -> ScanUiState.Unknown(code)
                is LookupResult.Failed -> ScanUiState.Unknown(code, result.error)
            }
        }
    }

    /** A scan almost always means a whole package arrived, so that is what is filled in. */
    private fun defaultAmountFor(medicine: Medicine): String =
        medicine.stock.unitsPerPackage.takeIf { it > 0 }?.let { formatAmount(it) } ?: "1"

    fun setAmount(value: String) {
        val current = _state.value
        if (current is ScanUiState.Recognised) _state.value = current.copy(amount = value)
    }

    /**
     * Adds the package to the stock and remembers the expiry date, if the code carried one.
     *
     * Barcodes themselves are never written here - linking a code to a medicine goes through the
     * edit screen, whose save replaces the whole set at once.
     */
    fun confirmStock(onDone: () -> Unit = {}) {
        val current = _state.value as? ScanUiState.Recognised ?: return
        val amount = current.amount.parseAmount() ?: return

        viewModelScope.launch {
            container.medicineRepository.adjustStock(current.medicine.id, amount)

            val merged = ExpiryWatcher.merge(current.medicine.stock.expiryDate, current.code.expiry)
            if (merged != current.medicine.stock.expiryDate) {
                container.medicineRepository.setExpiryDate(current.medicine.id, merged)
            }

            container.reminderCoordinator.onStockChanged()
            _state.value = ScanUiState.Idle
            onDone()
        }
    }

    /** Moves a code to another medicine, used from "koppelen aan een bestaand medicijn". */
    fun linkTo(medicineId: Long, code: ScannedCode, onDone: (Long) -> Unit) {
        val gtin = code.gtin ?: return
        viewModelScope.launch {
            val medicine = container.medicineRepository.getById(medicineId) ?: return@launch
            container.medicineRepository.save(
                medicine.copy(
                    barcodes = (medicine.barcodes + gtin).distinct(),
                    stock = medicine.stock.copy(
                        expiryDate = ExpiryWatcher.merge(medicine.stock.expiryDate, code.expiry),
                    ),
                ),
            )
            container.reminderCoordinator.onMedicineSaved(medicineId)
            _state.value = ScanUiState.Idle
            onDone(medicineId)
        }
    }

    fun dismiss() {
        lookupJob?.cancel()
        _state.value = ScanUiState.Idle
    }

    /** Gives up on a slow lookup without losing the code that was scanned. */
    fun skipLookup() {
        val current = _state.value
        if (current is ScanUiState.LookingUp) {
            lookupJob?.cancel()
            _state.value = ScanUiState.Unknown(current.code)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScanViewModel(container) }
        }
    }
}

private fun String.parseAmount(): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }

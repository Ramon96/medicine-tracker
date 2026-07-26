package nl.ramon96.medicijntracker.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.scan.LookupError
import nl.ramon96.medicijntracker.scan.ScanError
import nl.ramon96.medicijntracker.ui.common.dateFormatter
import nl.ramon96.medicijntracker.ui.today.formatAmount

/**
 * What comes up after a scan.
 *
 * Rendered once, above the whole app, so scanning works from any tab. Nothing here writes to the
 * database without the user pressing a button first: a stray scan must never quietly change a
 * stock count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(
    state: ScanUiState,
    medicines: List<Medicine>,
    onAmountChange: (String) -> Unit,
    onConfirmStock: () -> Unit,
    onSkipLookup: () -> Unit,
    onDismiss: () -> Unit,
    onCreateFromScan: () -> Unit,
    onUseSuggestion: () -> Unit,
    onLinkToExisting: (Long) -> Unit,
) {
    if (state is ScanUiState.Idle) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is ScanUiState.Idle -> Unit

                is ScanUiState.Recognised -> RecognisedContent(
                    state = state,
                    onAmountChange = onAmountChange,
                    onConfirm = onConfirmStock,
                    onDismiss = onDismiss,
                )

                is ScanUiState.LookingUp -> LookingUpContent(onSkip = onSkipLookup)

                is ScanUiState.Suggested -> SuggestedContent(
                    state = state,
                    onUse = onUseSuggestion,
                    onFillMyself = onCreateFromScan,
                    onDismiss = onDismiss,
                )

                is ScanUiState.Unknown -> UnknownContent(
                    state = state,
                    medicines = medicines,
                    onCreate = onCreateFromScan,
                    onLink = onLinkToExisting,
                )

                is ScanUiState.Failed -> {
                    Text(
                        text = stringResource(scanErrorMessage(state.error)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.detail?.let { detail ->
                        Text(text = detail, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RecognisedContent(
    state: ScanUiState.Recognised,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stock = state.medicine.stock
    val label = listOfNotNull(
        state.medicine.name,
        state.medicine.dosage.takeIf { it.isNotBlank() },
    ).joinToString(" ")

    Text(
        text = stringResource(R.string.scan_recognised_title, label),
        style = MaterialTheme.typography.titleLarge,
    )

    OutlinedTextField(
        value = state.amount,
        onValueChange = onAmountChange,
        label = { Text(stringResource(R.string.scan_add_amount)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.scan_stock_now, formatAmount(stock.count)),
        style = MaterialTheme.typography.bodyMedium,
    )

    val added = state.amount.replace(',', '.').toDoubleOrNull()
    if (added != null && added > 0) {
        Text(
            text = stringResource(R.string.scan_stock_after, formatAmount(stock.count + added)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    state.code.expiry?.let { expiry ->
        Text(
            text = stringResource(R.string.scan_expiry_found, expiry.format(dateFormatter)),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        Button(onClick = onConfirm, enabled = added != null && added > 0) {
            Text(stringResource(R.string.action_add_to_stock))
        }
    }
}

@Composable
private fun ColumnScope.LookingUpContent(onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(
            text = stringResource(R.string.scan_looking_up),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    // A slow or hanging lookup must never trap the user in this state.
    TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) {
        Text(stringResource(R.string.action_skip_lookup))
    }
}

@Composable
private fun ColumnScope.SuggestedContent(
    state: ScanUiState.Suggested,
    onUse: () -> Unit,
    onFillMyself: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = stringResource(R.string.scan_suggestion_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(text = state.suggestion.name, style = MaterialTheme.typography.titleMedium)
    listOfNotNull(state.suggestion.brand, state.suggestion.quantity)
        .takeIf { it.isNotEmpty() }
        ?.let { Text(text = it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium) }

    // The database is community-maintained and thin on medicines, so this warning is not
    // decoration - the name really can be wrong.
    Text(
        text = stringResource(R.string.scan_suggestion_hint),
        style = MaterialTheme.typography.bodySmall,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        OutlinedButton(onClick = onFillMyself) { Text(stringResource(R.string.action_fill_myself)) }
        Button(onClick = onUse) { Text(stringResource(R.string.action_use_suggestion)) }
    }
}

@Composable
private fun ColumnScope.UnknownContent(
    state: ScanUiState.Unknown,
    medicines: List<Medicine>,
    onCreate: () -> Unit,
    onLink: (Long) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Text(
        text = stringResource(
            if (state.code.isUsable) R.string.scan_unknown_title else R.string.scan_unreadable,
        ),
        style = MaterialTheme.typography.titleLarge,
    )

    if (state.code.isUsable) {
        Text(
            text = stringResource(R.string.scan_unknown_body),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    // Why the lookup came back empty, when it was tried and failed rather than simply not knowing.
    state.lookupError?.let {
        Text(
            text = stringResource(lookupErrorMessage(it)),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_new_from_scan))
    }

    // Reaching this state means no medicine owns the code yet, so linking it needs no warning
    // about taking it away from something else.
    if (state.code.isUsable && medicines.isNotEmpty()) {
        OutlinedButton(onClick = { picking = !picking }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_link_existing))
        }
        if (picking) {
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(medicines, key = { it.id }) { medicine ->
                    TextButton(
                        onClick = { onLink(medicine.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = medicine.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Dutch wording per failure, mirroring how the updater reports its own errors. */
fun scanErrorMessage(error: ScanError): Int = when (error) {
    ScanError.PLAY_SERVICES_MISSING -> R.string.scan_error_play_services
    ScanError.MODULE_UNAVAILABLE -> R.string.scan_error_module
    ScanError.CAMERA_UNAVAILABLE -> R.string.scan_error_camera
    ScanError.UNKNOWN -> R.string.scan_error_unknown
}

fun lookupErrorMessage(error: LookupError): Int = when (error) {
    LookupError.NO_NETWORK -> R.string.lookup_error_network
    LookupError.RATE_LIMITED -> R.string.lookup_error_rate_limited
    LookupError.UNKNOWN -> R.string.lookup_error_unknown
}

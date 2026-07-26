package nl.ramon96.medicijntracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.domain.model.IntakeStatus
import nl.ramon96.medicijntracker.domain.model.IntakeWithMedicine
import nl.ramon96.medicijntracker.ui.common.PermissionCard
import nl.ramon96.medicijntracker.ui.common.cycleSummary
import nl.ramon96.medicijntracker.ui.common.dateFormatter
import nl.ramon96.medicijntracker.ui.common.rememberPermissionStatus
import nl.ramon96.medicijntracker.ui.common.timeFormatter

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onAddMedicine: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionStatus = rememberPermissionStatus()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = state.date.format(dateFormatter),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item { PermissionCard(permissionStatus) }

        items(state.expiryWarnings, key = { "expiry-${it.medicine.id}" }) { warning ->
            ExpiryCard(warning = warning, onFinished = { viewModel.clearExpiry(warning.medicine) })
        }

        items(state.refillWarnings, key = { "refill-${it.medicine.id}" }) { warning ->
            RefillCard(
                warning = warning,
                onOrdered = { viewModel.addPackage(warning.medicine) },
                onScan = onScan,
            )
        }

        if (!state.hasMedicines) {
            item { EmptyState(onAddMedicine = onAddMedicine) }
        } else if (state.open.isEmpty() && state.done.isEmpty()) {
            item { Text(stringResource(R.string.today_nothing_scheduled)) }
        }

        if (state.open.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.today_open)) }
            items(state.open, key = { it.intake.id }) { item ->
                DoseRow(
                    item = item,
                    onTaken = { viewModel.markTaken(item.intake.id) },
                    onSkipped = { viewModel.markSkipped(item.intake.id) },
                    onSnooze = {
                        viewModel.snooze(item.intake.id, item.medicine.reminders.snoozeMinutes)
                    },
                )
            }
        }

        if (state.done.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.today_done)) }
            items(state.done, key = { it.intake.id }) { item ->
                SettledDoseRow(item = item, onUndo = { viewModel.undo(item.intake.id) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DoseRow(
    item: IntakeWithMedicine,
    onTaken: () -> Unit,
    onSkipped: () -> Unit,
    onSnooze: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.intake.scheduledAt.format(timeFormatter),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.medicine.name, style = MaterialTheme.typography.titleMedium)
                    val subtitle = listOfNotNull(
                        item.medicine.dosage.takeIf { it.isNotBlank() },
                        stringResource(R.string.dose_amount, formatAmount(item.intake.amount)),
                    ).joinToString(" · ")
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }

            cycleSummary(item.medicine, item.intake.scheduledAt.toLocalDate())?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            if (item.intake.status == IntakeStatus.SNOOZED) {
                Text(
                    text = stringResource(
                        R.string.dose_snoozed_until,
                        item.intake.remindAt.format(timeFormatter),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTaken) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_taken),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                TextButton(onClick = onSnooze) { Text(stringResource(R.string.action_snooze)) }
                TextButton(onClick = onSkipped) { Text(stringResource(R.string.action_skip)) }
            }
        }
    }
}

@Composable
private fun SettledDoseRow(item: IntakeWithMedicine, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.intake.status == IntakeStatus.TAKEN) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = "${item.intake.scheduledAt.format(timeFormatter)}  ${item.medicine.name}",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.intake.status == IntakeStatus.TAKEN) TextDecoration.LineThrough else null,
            )
            Text(
                text = when (item.intake.status) {
                    IntakeStatus.TAKEN -> stringResource(R.string.status_taken)
                    IntakeStatus.SKIPPED -> stringResource(R.string.status_skipped)
                    else -> stringResource(R.string.status_missed)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onUndo) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_undo))
        }
    }
}

@Composable
private fun RefillCard(warning: RefillWarning, onOrdered: () -> Unit, onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Text(
                    text = stringResource(R.string.refill_card_title, warning.medicine.name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            val days = warning.forecast.daysOfSupply ?: 0
            Text(
                text = pluralStringResource(
                    R.plurals.refill_card_body,
                    days,
                    days,
                    warning.medicine.stock.leadTimeDays,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (warning.medicine.stock.unitsPerPackage > 0) {
                    FilledTonalButton(onClick = onOrdered) {
                        Text(stringResource(R.string.refill_received_package))
                    }
                }
                // The moment a new box arrives is exactly when scanning it is worth the trouble.
                FilledTonalButton(onClick = onScan) {
                    Text(stringResource(R.string.action_scan_package))
                }
            }
        }
    }
}

/**
 * The box goes off before it is finished - something a stock count on its own never notices.
 *
 * "Dit doosje is op" clears the date as well as the warning: the stored date belongs to the
 * oldest package, so once that is gone the date is simply wrong.
 */
@Composable
private fun ExpiryCard(warning: ExpiryWarning, onFinished: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = null)
                Text(
                    text = if (warning.risk.alreadyExpired) {
                        stringResource(
                            R.string.expiry_card_expired,
                            warning.medicine.name,
                            warning.risk.expiryDate.format(dateFormatter),
                        )
                    } else {
                        stringResource(R.string.expiry_card_title, warning.medicine.name)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            warning.risk.runOutDate?.takeIf { !warning.risk.alreadyExpired }?.let { runOut ->
                Text(
                    text = stringResource(
                        R.string.expiry_card_body,
                        warning.risk.expiryDate.format(dateFormatter),
                        runOut.format(dateFormatter),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledTonalButton(onClick = onFinished) {
                Text(stringResource(R.string.action_package_finished))
            }
        }
    }
}

@Composable
private fun EmptyState(onAddMedicine: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.today_empty_body))
            Button(onClick = onAddMedicine) { Text(stringResource(R.string.action_add_medicine)) }
        }
    }
}

/** 1.0 -> "1", 0.5 -> "0,5"; avoids showing "1.0 tablet". */
fun formatAmount(amount: Double): String =
    if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        amount.toString().replace('.', ',')
    }

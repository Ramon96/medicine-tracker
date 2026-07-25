package nl.ramon96.medicijntracker.ui.medicines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.ui.common.scheduleSummary
import nl.ramon96.medicijntracker.ui.common.shortDateFormatter
import nl.ramon96.medicijntracker.ui.today.formatAmount

@Composable
fun MedicineListScreen(
    viewModel: MedicineListViewModel,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by viewModel.medicines.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Medicine?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rows.isEmpty()) {
                item { Text(stringResource(R.string.medicines_empty)) }
            }
            items(rows, key = { it.medicine.id }) { row ->
                MedicineCard(
                    row = row,
                    onClick = { onEdit(row.medicine.id) },
                    onDelete = { pendingDelete = row.medicine },
                )
            }
        }

        FloatingActionButton(
            onClick = { onEdit(0L) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_medicine))
        }
    }

    pendingDelete?.let { medicine ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title, medicine.name)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(medicine)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MedicineCard(row: MedicineRow, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.medicine.name, style = MaterialTheme.typography.titleMedium)
                if (row.medicine.dosage.isNotBlank()) {
                    Text(row.medicine.dosage, style = MaterialTheme.typography.bodySmall)
                }
                Text(scheduleSummary(row.medicine), style = MaterialTheme.typography.bodySmall)

                if (row.medicine.stock.trackingEnabled) {
                    Text(
                        text = stringResource(
                            R.string.medicine_stock_line,
                            formatAmount(row.medicine.stock.count),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    row.forecast.orderByDate?.let { orderBy ->
                        val days = row.forecast.daysOfSupply ?: 0
                        Text(
                            text = if (row.forecast.shouldOrderNow) {
                                stringResource(R.string.medicine_order_now)
                            } else {
                                pluralStringResource(
                                    R.plurals.medicine_order_by,
                                    days,
                                    orderBy.format(shortDateFormatter),
                                    days,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.forecast.shouldOrderNow) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (!row.medicine.active) {
                    Text(
                        text = stringResource(R.string.medicine_inactive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

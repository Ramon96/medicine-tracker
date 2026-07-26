package nl.ramon96.medicijntracker.ui.medicines

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import nl.ramon96.medicijntracker.ui.common.dateFormatter
import nl.ramon96.medicijntracker.scan.rememberCodeScanner
import nl.ramon96.medicijntracker.scan.ScanOutcome
import nl.ramon96.medicijntracker.ui.common.timeFormatter
import nl.ramon96.medicijntracker.ui.today.formatAmount
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MedicineEditScreen(
    viewModel: MedicineEditViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                isError = state.nameError,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value = draft.dosage,
                onValueChange = viewModel::setDosage,
                label = { Text(stringResource(R.string.field_dosage)) },
                placeholder = { Text(stringResource(R.string.field_dosage_hint)) },
                supportingText = { Text(stringResource(R.string.field_dosage_support)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { SectionTitle(stringResource(R.string.section_schedule)) }

        item {
            // FlowRow, not Row: the four labels do not fit on one line, and a plain Row squeezes
            // the last chip off the screen edge instead of wrapping.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ScheduleType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.schedule.type == type,
                        onClick = { viewModel.setScheduleType(type) },
                        label = { Text(scheduleTypeLabel(type)) },
                    )
                }
            }
        }

        when (draft.schedule.type) {
            ScheduleType.DAILY -> Unit

            ScheduleType.INTERVAL_DAYS -> item {
                NumberField(
                    label = stringResource(R.string.field_interval_days),
                    value = draft.schedule.intervalDays.toString(),
                    onValueChange = { it.toIntOrNull()?.let(viewModel::setIntervalDays) },
                )
            }

            ScheduleType.WEEKDAYS -> item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = draft.schedule.weekdayMask and Schedule.bitFor(day) != 0,
                            onClick = { viewModel.toggleWeekday(day) },
                            label = {
                                Text(day.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("nl-NL")))
                            },
                        )
                    }
                }
            }

            ScheduleType.CYCLE -> item { CycleEditor(viewModel, draft.schedule) }
        }

        item { SectionTitle(stringResource(R.string.section_times)) }
        item {
            Text(
                text = stringResource(R.string.section_times_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        itemsIndexed(draft.doseTimes, key = { index, _ -> index }) { index, doseTime ->
            DoseTimeRow(
                time = doseTime.time,
                amount = doseTime.amount,
                onTimeChange = { viewModel.updateDoseTime(index, it) },
                onAmountChange = { viewModel.updateDoseAmount(index, it) },
                onRemove = { viewModel.removeDoseTime(index) },
            )
        }

        item {
            val context = LocalContext.current
            OutlinedButton(onClick = {
                showTimePicker(context, LocalTime.of(8, 0)) { viewModel.addDoseTime(it) }
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.action_add_time),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.noDoseTimes) {
                Text(
                    text = stringResource(R.string.error_no_times),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.section_stock)) }
        item { StockEditor(viewModel, state) }

        item { SectionTitle(stringResource(R.string.section_barcodes)) }
        item { BarcodeEditor(viewModel, state) }

        item { SectionTitle(stringResource(R.string.section_reminders)) }
        item { ReminderEditor(viewModel, state) }

        item { HorizontalDivider() }

        item { PeriodEditor(viewModel, state) }

        item {
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun CycleEditor(viewModel: MedicineEditViewModel, schedule: Schedule) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.cycle_explainer),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = stringResource(R.string.field_cycle_active),
                    value = schedule.cycleActiveDays.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { days -> viewModel.setCycleDays(days, schedule.cyclePauseDays) }
                    },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.field_cycle_pause),
                    value = schedule.cyclePauseDays.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { days -> viewModel.setCycleDays(schedule.cycleActiveDays, days) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            val anchor = schedule.cycleAnchorDate ?: LocalDate.now()
            OutlinedButton(onClick = {
                showDatePicker(context, anchor) { viewModel.setCycleAnchor(it) }
            }) {
                Text(stringResource(R.string.field_cycle_anchor, anchor.format(dateFormatter)))
            }
            SwitchRow(
                label = stringResource(R.string.field_remind_on_pause),
                description = stringResource(R.string.field_remind_on_pause_hint),
                checked = schedule.remindOnPauseDays,
                onCheckedChange = viewModel::setRemindOnPauseDays,
            )
        }
    }
}

@Composable
private fun DoseTimeRow(
    time: LocalTime,
    amount: Double,
    onTimeChange: (LocalTime) -> Unit,
    onAmountChange: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { showTimePicker(context, time, onTimeChange) }) {
            Text(time.format(timeFormatter))
        }
        NumberField(
            label = stringResource(R.string.field_amount),
            value = formatAmount(amount),
            onValueChange = { text ->
                text.replace(',', '.').toDoubleOrNull()?.let(onAmountChange)
            },
            modifier = Modifier.weight(1f),
            decimal = true,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}

@Composable
private fun StockEditor(viewModel: MedicineEditViewModel, state: MedicineEditState) {
    val stock = state.draft.stock
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(
            label = stringResource(R.string.field_track_stock),
            description = stringResource(R.string.field_track_stock_hint),
            checked = stock.trackingEnabled,
            onCheckedChange = viewModel::setStockTracking,
        )
        if (stock.trackingEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = stringResource(R.string.field_stock_count),
                    value = formatAmount(stock.count),
                    onValueChange = { text ->
                        text.replace(',', '.').toDoubleOrNull()?.let(viewModel::setStockCount)
                    },
                    modifier = Modifier.weight(1f),
                    decimal = true,
                )
                NumberField(
                    label = stringResource(R.string.field_units_per_package),
                    value = formatAmount(stock.unitsPerPackage),
                    onValueChange = { text ->
                        text.replace(',', '.').toDoubleOrNull()?.let(viewModel::setUnitsPerPackage)
                    },
                    modifier = Modifier.weight(1f),
                    decimal = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = stringResource(R.string.field_lead_time),
                    value = stock.leadTimeDays.toString(),
                    onValueChange = { it.toIntOrNull()?.let(viewModel::setLeadTimeDays) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.field_buffer_days),
                    value = stock.bufferDays.toString(),
                    onValueChange = { it.toIntOrNull()?.let(viewModel::setBufferDays) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.field_lead_time_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            ExpiryRow(viewModel, stock.expiryDate)
        }
    }
}

/**
 * The expiry date of the oldest package. Filled in by scanning a data matrix, but editable and
 * clearable by hand - once that box is finished the stored date is too pessimistic, and clearing
 * it is how the user says so.
 */
@Composable
private fun ExpiryRow(viewModel: MedicineEditViewModel, expiryDate: LocalDate?) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(
            onClick = {
                showDatePicker(context, expiryDate ?: LocalDate.now()) { viewModel.setExpiryDate(it) }
            },
        ) {
            Text(
                if (expiryDate == null) stringResource(R.string.field_expiry_none)
                else stringResource(R.string.field_expiry, expiryDate.format(dateFormatter)),
            )
        }
        if (expiryDate != null) {
            TextButton(onClick = { viewModel.setExpiryDate(null) }) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
    Text(
        text = stringResource(R.string.field_expiry_hint),
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * The packages that belong to this medicine.
 *
 * More than one is normal: pack sizes differ and the pharmacy switches generic manufacturers, and
 * every one of those is its own product number for what the user thinks of as one medicine.
 */
@Composable
private fun BarcodeEditor(viewModel: MedicineEditViewModel, state: MedicineEditState) {
    val scope = rememberCoroutineScope()
    var relink by remember { mutableStateOf<Pair<String, Medicine>?>(null) }

    val startScan = rememberCodeScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Scanned -> outcome.code.gtin?.let { gtin ->
                scope.launch {
                    // Codes are unique per medicine, so linking one that belongs elsewhere moves
                    // it. That is worth asking about rather than doing quietly.
                    val owner = viewModel.ownerOf(gtin)
                    if (owner == null) {
                        viewModel.addBarcode(gtin)
                        outcome.code.expiry?.let(viewModel::setExpiryDate)
                    } else {
                        relink = gtin to owner
                    }
                }
            }
            is ScanOutcome.Cancelled -> Unit
            is ScanOutcome.Failed -> Unit
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.field_barcodes_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.draft.barcodes.isEmpty()) {
            Text(stringResource(R.string.barcode_none), style = MaterialTheme.typography.bodyMedium)
        }
        state.draft.barcodes.forEach { code ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = code, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.removeBarcode(code) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_unlink_barcode),
                    )
                }
            }
        }
        OutlinedButton(onClick = startScan, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_link_barcode))
        }
    }

    relink?.let { (code, owner) ->
        AlertDialog(
            onDismissRequest = { relink = null },
            title = { Text(stringResource(R.string.scan_relink_title)) },
            text = {
                Text(stringResource(R.string.scan_relink_body, owner.name, state.draft.name))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addBarcode(code); relink = null }) {
                    Text(stringResource(R.string.action_move_barcode))
                }
            },
            dismissButton = {
                TextButton(onClick = { relink = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReminderEditor(viewModel: MedicineEditViewModel, state: MedicineEditState) {
    val reminders = state.draft.reminders
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(
            label = stringResource(R.string.field_reminders_enabled),
            checked = reminders.enabled,
            onCheckedChange = viewModel::setRemindersEnabled,
        )
        if (reminders.enabled) {
            NumberField(
                label = stringResource(R.string.field_snooze_minutes),
                value = reminders.snoozeMinutes.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setSnoozeMinutes) },
            )
            SwitchRow(
                label = stringResource(R.string.field_repeat_if_ignored),
                description = stringResource(R.string.field_repeat_if_ignored_hint),
                checked = reminders.repeatIfIgnored,
                onCheckedChange = viewModel::setRepeatIfIgnored,
            )
            SwitchRow(
                label = stringResource(R.string.field_vibrate),
                checked = reminders.vibrate,
                onCheckedChange = viewModel::setVibrate,
            )
            SectionTitle(stringResource(R.string.field_sound))
            SoundPickerRow(
                soundUri = reminders.soundUri,
                onSoundChange = viewModel::setSoundUri,
            )
        }
    }
}

@Composable
private fun PeriodEditor(viewModel: MedicineEditViewModel, state: MedicineEditState) {
    val context = LocalContext.current
    val draft = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            showDatePicker(context, draft.startDate) { viewModel.setStartDate(it) }
        }) {
            Text(stringResource(R.string.field_start_date, draft.startDate.format(dateFormatter)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                showDatePicker(context, draft.endDate ?: LocalDate.now()) { viewModel.setEndDate(it) }
            }) {
                Text(
                    text = draft.endDate?.let {
                        stringResource(R.string.field_end_date, it.format(dateFormatter))
                    } ?: stringResource(R.string.field_end_date_none),
                )
            }
            if (draft.endDate != null) {
                TextButton(onClick = { viewModel.setEndDate(null) }) {
                    Text(stringResource(R.string.action_clear))
                }
            }
        }
        SwitchRow(
            label = stringResource(R.string.field_active),
            checked = draft.active,
            onCheckedChange = viewModel::setActive,
        )
        OutlinedTextField(
            value = draft.notes,
            onValueChange = viewModel::setNotes,
            label = { Text(stringResource(R.string.field_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}

@Composable
private fun scheduleTypeLabel(type: ScheduleType): String = when (type) {
    ScheduleType.DAILY -> stringResource(R.string.schedule_type_daily)
    ScheduleType.INTERVAL_DAYS -> stringResource(R.string.schedule_type_interval)
    ScheduleType.WEEKDAYS -> stringResource(R.string.schedule_type_weekdays)
    ScheduleType.CYCLE -> stringResource(R.string.schedule_type_cycle)
}

/** Platform pickers keep the form free of experimental Compose dialog APIs. */
private fun showTimePicker(
    context: android.content.Context,
    initial: LocalTime,
    onPicked: (LocalTime) -> Unit,
) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        true,
    ).show()
}

private fun showDatePicker(
    context: android.content.Context,
    initial: LocalDate,
    onPicked: (LocalDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

package nl.ramon96.medicijntracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.ui.common.doseSummary
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val dutch = Locale.forLanguageTag("nl-NL")

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousMonth) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous_month))
                }
                Text(
                    text = "${state.month.month.getDisplayName(TextStyle.FULL, dutch)} ${state.month.year}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next_month))
                }
            }
        }

        item { MonthGrid(state) }

        item {
            Text(
                text = stringResource(R.string.history_adherence_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.adherence.isEmpty()) {
            item { Text(stringResource(R.string.history_empty)) }
        }

        items(state.adherence, key = { it.medicine.id }) { row ->
            AdherenceRow(row)
        }
    }
}

@Composable
private fun MonthGrid(state: HistoryUiState) {
    val days = state.days
    if (days.isEmpty()) return

    // Monday-first grid: pad the first week so day 1 lands under the right weekday.
    val leadingBlanks = days.first().date.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells: List<DaySummary?> = List(leadingBlanks) { null } + days

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, dutch),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { cell -> DayCell(cell, Modifier.weight(1f)) }
                    // Keep the last row aligned with the others.
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: DaySummary?, modifier: Modifier = Modifier) {
    val background = when (day?.result) {
        DayResult.ALL_TAKEN -> MaterialTheme.colorScheme.primaryContainer
        DayResult.PARTIAL -> MaterialTheme.colorScheme.tertiaryContainer
        DayResult.NOTHING_TAKEN -> MaterialTheme.colorScheme.errorContainer
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AdherenceRow(row: MedicineAdherence) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(row.medicine.name, modifier = Modifier.weight(1f))
            Text(
                text = row.ratio?.let { "${(it * 100).roundToInt()}%" }
                    ?: stringResource(R.string.history_no_data),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { row.ratio ?: 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            // Plural on the total, so a single dose does not read as "1 van 1 innames".
            text = pluralStringResource(
                R.plurals.history_taken_of,
                row.settled,
                row.taken,
                row.settled,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        // Spells out what one inname is, so the counts above can never be mistaken for tablets.
        doseSummary(row.medicine)?.let {
            Text(
                text = stringResource(R.string.history_dose_is, it),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

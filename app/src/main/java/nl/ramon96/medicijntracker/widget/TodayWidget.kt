package nl.ramon96.medicijntracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import nl.ramon96.medicijntracker.MainActivity
import nl.ramon96.medicijntracker.MedicijnApp
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.data.prefs.ThemeMode
import nl.ramon96.medicijntracker.domain.stock.StockForecaster
import nl.ramon96.medicijntracker.ui.theme.Palettes
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** What the widget shows; assembled before the content is rendered. */
private data class WidgetDose(val intakeId: Long, val time: String, val name: String, val dosage: String)

private data class WidgetState(
    val doses: List<WidgetDose>,
    val orderSoon: List<String>,
    val allDone: Boolean,
)

/**
 * Home-screen widget with today's remaining doses. Tapping a row ticks the dose off without
 * opening the app, which is the whole point of having it on the home screen.
 */
object TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = MedicijnApp.containerOf(context)
        val today = LocalDate.now()

        val doses = container.doseRepository.observeDay(today).first()
        val medicines = container.medicineRepository.getAll()

        val open = doses
            .filter { it.intake.isOpen }
            .sortedBy { it.intake.remindAt }
            .map {
                WidgetDose(
                    intakeId = it.intake.id,
                    time = it.intake.scheduledAt.format(timeFormat),
                    name = it.medicine.name,
                    dosage = it.medicine.dosage,
                )
            }

        val orderSoon = medicines
            .filter { StockForecaster.forecast(it, today).shouldOrderNow }
            .map { it.name }

        val state = WidgetState(
            doses = open,
            orderSoon = orderSoon,
            allDone = open.isEmpty() && doses.isNotEmpty(),
        )

        // The widget sits next to the app on the home screen, so it follows the same palette.
        val theme = container.settings.currentTheme()
        val palette = Palettes.byId(theme.paletteId)
        val lightScheme = Palettes.schemeFor(palette, dark = false, highContrast = theme.highContrast)
        val darkScheme = Palettes.schemeFor(palette, dark = true, highContrast = theme.highContrast)
        val colors = when (theme.mode) {
            // A forced mode has to win here too, otherwise the widget follows the system while
            // the app does not.
            ThemeMode.LIGHT -> ColorProviders(light = lightScheme, dark = lightScheme)
            ThemeMode.DARK -> ColorProviders(light = darkScheme, dark = darkScheme)
            ThemeMode.SYSTEM -> ColorProviders(light = lightScheme, dark = darkScheme)
        }

        provideContent {
            GlanceTheme(colors = colors) {
                WidgetContent(context, state)
            }
        }
    }

    suspend fun refresh(context: Context) {
        updateAll(context)
    }
}

@Composable
private fun WidgetContent(context: Context, state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))

        if (state.orderSoon.isNotEmpty()) {
            Text(
                text = context.getString(
                    R.string.widget_order_soon,
                    state.orderSoon.joinToString(", "),
                ),
                style = TextStyle(color = GlanceTheme.colors.error),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(4.dp))
        }

        when {
            state.allDone -> Text(
                text = context.getString(R.string.widget_all_done),
                style = TextStyle(color = GlanceTheme.colors.onSurface),
            )

            state.doses.isEmpty() -> Text(
                text = context.getString(R.string.widget_nothing_today),
                style = TextStyle(color = GlanceTheme.colors.onSurface),
            )

            else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                items(state.doses, itemId = { it.intakeId }) { dose ->
                    DoseRow(context, dose)
                }
            }
        }
    }
}

@Composable
private fun DoseRow(context: Context, dose: WidgetDose) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dose.time,
            style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = if (dose.dosage.isBlank()) dose.name else "${dose.name} · ${dose.dosage}",
            style = TextStyle(color = GlanceTheme.colors.onSurface),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Button(
            text = context.getString(R.string.widget_take),
            onClick = actionRunCallback<MarkTakenAction>(
                actionParametersOf(MarkTakenAction.intakeIdKey to dose.intakeId),
            ),
        )
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = TodayWidget
}

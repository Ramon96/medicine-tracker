package nl.ramon96.medicijntracker.domain.stock

import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.schedule.ScheduleCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/**
 * The answer to "when do I have to order this again?".
 *
 * [orderByDate] is the whole point of the app: it sits [StockInfo.leadTimeDays] plus a buffer
 * before the stock actually runs out, so the order is placed while there are still pills left.
 */
data class RefillForecast(
    val medicineId: Long,
    val avgUnitsPerDay: Double,
    /** Whole days of stock left, or null when consumption or tracking is unknown. */
    val daysOfSupply: Int?,
    val runOutDate: LocalDate?,
    val orderByDate: LocalDate?,
    val shouldOrderNow: Boolean,
) {
    val hasForecast: Boolean get() = runOutDate != null
}

object StockForecaster {

    /** Only warn this often about the same medicine, so it does not nag every single day. */
    const val DEFAULT_ALERT_THROTTLE_DAYS = 3

    fun forecast(medicine: Medicine, today: LocalDate): RefillForecast {
        val avgPerDay = ScheduleCalculator.averageUnitsPerDay(medicine)
        val stock = medicine.stock

        if (!stock.trackingEnabled || avgPerDay <= 0.0 || !medicine.active) {
            return RefillForecast(
                medicineId = medicine.id,
                avgUnitsPerDay = avgPerDay,
                daysOfSupply = null,
                runOutDate = null,
                orderByDate = null,
                shouldOrderNow = false,
            )
        }

        val daysOfSupply = floor(stock.count.coerceAtLeast(0.0) / avgPerDay).toInt()
        val runOutDate = today.plusDays(daysOfSupply.toLong())
        val orderByDate = runOutDate.minusDays(
            (stock.leadTimeDays.coerceAtLeast(0) + stock.bufferDays.coerceAtLeast(0)).toLong(),
        )

        return RefillForecast(
            medicineId = medicine.id,
            avgUnitsPerDay = avgPerDay,
            daysOfSupply = daysOfSupply,
            runOutDate = runOutDate,
            orderByDate = orderByDate,
            shouldOrderNow = !today.isBefore(orderByDate),
        )
    }

    /**
     * Whether to actually raise a notification now: the stock has to be low enough *and* the
     * previous warning has to be long enough ago.
     */
    fun shouldAlert(
        medicine: Medicine,
        today: LocalDate,
        forecast: RefillForecast = forecast(medicine, today),
        throttleDays: Int = DEFAULT_ALERT_THROTTLE_DAYS,
    ): Boolean {
        if (!forecast.shouldOrderNow) return false
        val lastAlert = medicine.stock.lastAlertDate ?: return true
        return ChronoUnit.DAYS.between(lastAlert, today) >= throttleDays
    }
}

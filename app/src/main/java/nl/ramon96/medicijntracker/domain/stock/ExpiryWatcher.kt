package nl.ramon96.medicijntracker.domain.stock

import nl.ramon96.medicijntracker.domain.model.Medicine
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Why a package's expiry date is worth mentioning.
 *
 * [expiresBeforeRunOut] is the case the app exists to catch: there are enough pills in the box to
 * last until March, but the box itself is only good until January. Counting stock alone never
 * notices that.
 */
data class ExpiryRisk(
    val expiryDate: LocalDate,
    /** Negative once the date has passed. */
    val daysLeft: Long,
    val alreadyExpired: Boolean,
    val expiresBeforeRunOut: Boolean,
    /** When the stock itself would have run out; null when consumption is unknown. */
    val runOutDate: LocalDate?,
) {
    val worthWarningAbout: Boolean get() = alreadyExpired || expiresBeforeRunOut
}

/**
 * Pairs the expiry date printed on a package with the run-out date [StockForecaster] works out,
 * and says whether the box will go off before it is finished.
 *
 * Pure Kotlin and free of Android types, like the rest of `domain/`.
 */
object ExpiryWatcher {

    /**
     * Combines the date already stored with one just read off a package.
     *
     * The earliest wins: what matters is the oldest box in the house, since that is the one being
     * eaten into first.
     */
    fun merge(current: LocalDate?, scanned: LocalDate?): LocalDate? = when {
        current == null -> scanned
        scanned == null -> current
        else -> minOf(current, scanned)
    }

    /**
     * Null when there is nothing to say: no date on record, stock tracking switched off, or the
     * medicine is not in use. Warning about an expiry date the user never asked us to track would
     * just be noise.
     */
    fun risk(
        medicine: Medicine,
        today: LocalDate,
        forecast: RefillForecast = StockForecaster.forecast(medicine, today),
    ): ExpiryRisk? {
        val expiry = medicine.stock.expiryDate ?: return null
        if (!medicine.active || !medicine.stock.trackingEnabled) return null

        val daysLeft = ChronoUnit.DAYS.between(today, expiry)
        val runOut = forecast.runOutDate

        return ExpiryRisk(
            expiryDate = expiry,
            daysLeft = daysLeft,
            alreadyExpired = daysLeft < 0,
            // Without a forecast there is no run-out date to compare against, so the only thing
            // we can honestly report is whether the date has already passed.
            expiresBeforeRunOut = runOut != null && expiry.isBefore(runOut),
            runOutDate = runOut,
        )
    }
}

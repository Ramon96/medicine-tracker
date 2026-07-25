package nl.ramon96.medicijntracker.domain.stock

import nl.ramon96.medicijntracker.domain.model.DoseTime
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import nl.ramon96.medicijntracker.domain.model.StockInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StockForecasterTest {

    private val today = LocalDate.of(2026, 3, 1)

    private fun medicine(
        stock: StockInfo,
        schedule: Schedule = Schedule(),
        active: Boolean = true,
    ) = Medicine(
        id = 7,
        name = "Test",
        startDate = LocalDate.of(2026, 1, 1),
        active = active,
        schedule = schedule,
        stock = stock,
        doseTimes = listOf(DoseTime(time = LocalTime.of(8, 0))),
    )

    @Test
    fun `daily medicine runs out after one day per pill`() {
        val forecast = StockForecaster.forecast(
            medicine(StockInfo(count = 30.0, trackingEnabled = true, leadTimeDays = 0, bufferDays = 0)),
            today,
        )
        assertEquals(30, forecast.daysOfSupply)
        assertEquals(today.plusDays(30), forecast.runOutDate)
        assertEquals(today.plusDays(30), forecast.orderByDate)
        assertFalse(forecast.shouldOrderNow)
    }

    @Test
    fun `lead time and buffer move the order moment forward`() {
        val forecast = StockForecaster.forecast(
            medicine(StockInfo(count = 30.0, trackingEnabled = true, leadTimeDays = 10, bufferDays = 3)),
            today,
        )
        assertEquals(today.plusDays(30), forecast.runOutDate)
        // Order 13 days before running out, so the batch arrives with 3 days to spare.
        assertEquals(today.plusDays(17), forecast.orderByDate)
        assertFalse(forecast.shouldOrderNow)
    }

    @Test
    fun `order now fires exactly on the order-by date`() {
        val stock = StockInfo(count = 13.0, trackingEnabled = true, leadTimeDays = 10, bufferDays = 3)
        val forecast = StockForecaster.forecast(medicine(stock), today)
        assertEquals(today, forecast.orderByDate)
        assertTrue(forecast.shouldOrderNow)
    }

    @Test
    fun `order now stays true once the deadline has passed`() {
        val stock = StockInfo(count = 4.0, trackingEnabled = true, leadTimeDays = 10, bufferDays = 3)
        assertTrue(StockForecaster.forecast(medicine(stock), today).shouldOrderNow)
    }

    @Test
    fun `a 21-7 pill lasts longer than its pill count in days`() {
        val pill = medicine(
            stock = StockInfo(count = 21.0, trackingEnabled = true, leadTimeDays = 7, bufferDays = 3),
            schedule = Schedule(
                type = ScheduleType.CYCLE,
                cycleActiveDays = 21,
                cyclePauseDays = 7,
                cycleAnchorDate = LocalDate.of(2026, 1, 1),
            ),
        )
        val forecast = StockForecaster.forecast(pill, today)
        // 21 pills at 21/28 per day = 28 calendar days of supply, not 21.
        assertEquals(28, forecast.daysOfSupply)
        assertEquals(today.plusDays(28), forecast.runOutDate)
        assertEquals(today.plusDays(18), forecast.orderByDate)
        assertFalse(forecast.shouldOrderNow)
    }

    @Test
    fun `no forecast without tracking, without stock use, or when inactive`() {
        val untracked = StockForecaster.forecast(medicine(StockInfo(count = 30.0)), today)
        assertNull(untracked.runOutDate)
        assertFalse(untracked.hasForecast)

        val noDoses = Medicine(
            id = 1,
            name = "Zonder tijden",
            startDate = today,
            stock = StockInfo(count = 30.0, trackingEnabled = true),
        )
        assertNull(StockForecaster.forecast(noDoses, today).runOutDate)

        val inactive = medicine(StockInfo(count = 30.0, trackingEnabled = true), active = false)
        assertNull(StockForecaster.forecast(inactive, today).runOutDate)
    }

    @Test
    fun `empty stock reports zero days and orders immediately`() {
        val forecast = StockForecaster.forecast(
            medicine(StockInfo(count = 0.0, trackingEnabled = true, leadTimeDays = 5)),
            today,
        )
        assertEquals(0, forecast.daysOfSupply)
        assertEquals(today, forecast.runOutDate)
        assertTrue(forecast.shouldOrderNow)
    }

    @Test
    fun `partial days round down so the warning is never late`() {
        val forecast = StockForecaster.forecast(
            medicine(
                stock = StockInfo(count = 5.0, trackingEnabled = true),
                schedule = Schedule(type = ScheduleType.INTERVAL_DAYS, intervalDays = 3),
            ),
            today,
        )
        // 5 pills at one per three days = 15 days exactly.
        assertEquals(15, forecast.daysOfSupply)
    }

    // --- throttling --------------------------------------------------------

    @Test
    fun `alert is thrown once and then held back for a few days`() {
        val stock = StockInfo(count = 2.0, trackingEnabled = true, leadTimeDays = 10)
        assertTrue(StockForecaster.shouldAlert(medicine(stock), today))

        val alertedToday = medicine(stock.copy(lastAlertDate = today))
        assertFalse(StockForecaster.shouldAlert(alertedToday, today))

        val alertedTwoDaysAgo = medicine(stock.copy(lastAlertDate = today.minusDays(2)))
        assertFalse(StockForecaster.shouldAlert(alertedTwoDaysAgo, today))

        val alertedThreeDaysAgo = medicine(stock.copy(lastAlertDate = today.minusDays(3)))
        assertTrue(StockForecaster.shouldAlert(alertedThreeDaysAgo, today))
    }

    @Test
    fun `no alert while there is still plenty of stock`() {
        val stock = StockInfo(count = 90.0, trackingEnabled = true, leadTimeDays = 10)
        assertFalse(StockForecaster.shouldAlert(medicine(stock), today))
    }
}

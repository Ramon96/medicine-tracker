package nl.ramon96.medicijntracker.domain.stock

import nl.ramon96.medicijntracker.domain.model.DoseTime
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.StockInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExpiryWatcherTest {

    private val today = LocalDate.of(2026, 3, 1)

    private fun medicine(
        stock: StockInfo,
        active: Boolean = true,
    ) = Medicine(
        id = 7,
        name = "Test",
        startDate = LocalDate.of(2026, 1, 1),
        active = active,
        stock = stock,
        doseTimes = listOf(DoseTime(time = LocalTime.of(8, 0))),
    )

    // --- merge --------------------------------------------------------------

    @Test
    fun `merge keeps whichever date exists`() {
        val date = LocalDate.of(2026, 6, 1)
        assertNull(ExpiryWatcher.merge(null, null))
        assertEquals(date, ExpiryWatcher.merge(null, date))
        assertEquals(date, ExpiryWatcher.merge(date, null))
    }

    @Test
    fun `merge keeps the earliest, because that is the box being used first`() {
        val early = LocalDate.of(2026, 6, 1)
        val late = LocalDate.of(2027, 1, 1)
        assertEquals(early, ExpiryWatcher.merge(late, early))
        assertEquals(early, ExpiryWatcher.merge(early, late))
        assertEquals(early, ExpiryWatcher.merge(early, early))
    }

    // --- risk ---------------------------------------------------------------

    @Test
    fun `no date on record means nothing to say`() {
        assertNull(ExpiryWatcher.risk(medicine(StockInfo(count = 30.0, trackingEnabled = true)), today))
    }

    @Test
    fun `a box that outlives the stock is not worth warning about`() {
        // 30 pills at one a day run out on 31 March; the box is good until June.
        val risk = ExpiryWatcher.risk(
            medicine(
                StockInfo(
                    count = 30.0,
                    trackingEnabled = true,
                    expiryDate = LocalDate.of(2026, 6, 1),
                ),
            ),
            today,
        )!!
        assertFalse(risk.expiresBeforeRunOut)
        assertFalse(risk.alreadyExpired)
        assertFalse(risk.worthWarningAbout)
        assertEquals(92, risk.daysLeft)
    }

    @Test
    fun `a box that goes off before the stock runs out is the case we care about`() {
        // 300 pills would last until December, but the box expires in April.
        val risk = ExpiryWatcher.risk(
            medicine(
                StockInfo(
                    count = 300.0,
                    trackingEnabled = true,
                    expiryDate = LocalDate.of(2026, 4, 1),
                ),
            ),
            today,
        )!!
        assertTrue(risk.expiresBeforeRunOut)
        assertFalse(risk.alreadyExpired)
        assertTrue(risk.worthWarningAbout)
    }

    @Test
    fun `a date in the past reports as expired`() {
        val risk = ExpiryWatcher.risk(
            medicine(
                StockInfo(
                    count = 30.0,
                    trackingEnabled = true,
                    expiryDate = LocalDate.of(2026, 2, 1),
                ),
            ),
            today,
        )!!
        assertTrue(risk.alreadyExpired)
        assertTrue(risk.worthWarningAbout)
        assertEquals(-28, risk.daysLeft)
    }

    @Test
    fun `expiring exactly on the run-out date is not early`() {
        // 30 pills a day apart run out on 31 March, and the box lasts that long too.
        val risk = ExpiryWatcher.risk(
            medicine(
                StockInfo(
                    count = 30.0,
                    trackingEnabled = true,
                    expiryDate = LocalDate.of(2026, 3, 31),
                ),
            ),
            today,
        )!!
        assertFalse(risk.expiresBeforeRunOut)
    }

    @Test
    fun `nothing is reported when the user is not tracking stock`() {
        assertNull(
            ExpiryWatcher.risk(
                medicine(StockInfo(count = 30.0, trackingEnabled = false, expiryDate = LocalDate.of(2026, 4, 1))),
                today,
            ),
        )
    }

    @Test
    fun `nothing is reported for a medicine that is no longer taken`() {
        assertNull(
            ExpiryWatcher.risk(
                medicine(
                    StockInfo(count = 30.0, trackingEnabled = true, expiryDate = LocalDate.of(2026, 4, 1)),
                    active = false,
                ),
                today,
            ),
        )
    }

    @Test
    fun `without a run-out date only an expired box is flagged`() {
        // No dose times means no consumption, so StockForecaster produces no run-out date.
        val noDoses = Medicine(
            id = 7,
            name = "Test",
            startDate = LocalDate.of(2026, 1, 1),
            stock = StockInfo(count = 30.0, trackingEnabled = true, expiryDate = LocalDate.of(2026, 4, 1)),
        )
        val future = ExpiryWatcher.risk(noDoses, today)!!
        assertFalse(future.expiresBeforeRunOut)
        assertFalse(future.worthWarningAbout)

        val past = ExpiryWatcher.risk(
            noDoses.copy(stock = noDoses.stock.copy(expiryDate = LocalDate.of(2026, 1, 5))),
            today,
        )!!
        assertTrue(past.alreadyExpired)
        assertTrue(past.worthWarningAbout)
    }
}

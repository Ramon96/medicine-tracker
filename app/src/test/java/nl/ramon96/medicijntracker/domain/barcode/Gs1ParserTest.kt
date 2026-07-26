package nl.ramon96.medicijntracker.domain.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Gs1ParserTest {

    /** A valid EAN-13 and the GTIN-14 the DataMatrix carries for the same pack. */
    private val ean13 = "8712345678906"
    private val gtin14 = "08712345678906"
    private val gs = '\u001D'

    // --- linear barcodes ----------------------------------------------------

    @Test
    fun `plain EAN-13 becomes a padded GTIN`() {
        assertEquals(gtin14, Gs1Parser.parse(ean13).gtin)
    }

    @Test
    fun `EAN-8 and UPC-A are padded the same way`() {
        assertEquals("00000012345670", Gs1Parser.parse("12345670").gtin)
        assertEquals("00036000291452", Gs1Parser.parse("036000291452").gtin)
    }

    @Test
    fun `a bare 14-digit code is taken as a GTIN-14`() {
        assertEquals("18712345678903", Gs1Parser.parse("18712345678903").gtin)
    }

    @Test
    fun `surrounding whitespace does not matter`() {
        assertEquals(gtin14, Gs1Parser.parse("  $ean13 ").gtin)
    }

    // --- the property the barcode memory rests on ---------------------------

    @Test
    fun `EAN-13 and GTIN-14 of the same pack produce the same key`() {
        val fromLinear = Gs1Parser.parse(ean13)
        val fromMatrix = Gs1Parser.parse("01${gtin14}17260531")
        assertEquals(fromLinear.gtin, fromMatrix.gtin)
    }

    @Test
    fun `ean13 gives back the number printed under the barcode`() {
        assertEquals(ean13, Gs1Parser.parse(ean13).ean13)
        // A genuine GTIN-14 has no 13-digit form.
        assertNull(Gs1Parser.parse("18712345678903").ean13)
    }

    // --- GS1 element strings ------------------------------------------------

    @Test
    fun `GTIN and expiry are read from a DataMatrix payload`() {
        val code = Gs1Parser.parse("01${gtin14}1726053110LOT42${gs}21SER99")
        assertEquals(gtin14, code.gtin)
        assertEquals(LocalDate.of(2026, 5, 31), code.expiry)
        assertEquals("LOT42", code.batch)
        assertEquals("SER99", code.serial)
    }

    @Test
    fun `fields may arrive in any order`() {
        val code = Gs1Parser.parse("01${gtin14}21SER99${gs}1726053110LOT42")
        assertEquals(gtin14, code.gtin)
        assertEquals(LocalDate.of(2026, 5, 31), code.expiry)
        assertEquals("SER99", code.serial)
        assertEquals("LOT42", code.batch)
    }

    @Test
    fun `a leading separator is ignored`() {
        assertEquals(gtin14, Gs1Parser.parse("${gs}01${gtin14}17260531").gtin)
    }

    @Test
    fun `a symbology identifier is not part of the data`() {
        assertEquals(gtin14, Gs1Parser.parse("]d201${gtin14}17260531").gtin)
        assertEquals(gtin14, Gs1Parser.parse("]C101${gtin14}17260531").gtin)
    }

    @Test
    fun `without separators the variable-length fields are left alone`() {
        // Some scanners drop the group separator. Guessing where the lot number ends would be
        // worse than not reading it, so only the fixed-length fields come back.
        val code = Gs1Parser.parse("01${gtin14}1726053110LOT4221SER99")
        assertEquals(gtin14, code.gtin)
        assertEquals(LocalDate.of(2026, 5, 31), code.expiry)
        assertNull(code.batch)
        assertNull(code.serial)
    }

    @Test
    fun `an unknown application identifier stops the parse without losing what came before`() {
        val code = Gs1Parser.parse("01${gtin14}1726053199SOMETHING")
        assertEquals(gtin14, code.gtin)
        assertEquals(LocalDate.of(2026, 5, 31), code.expiry)
    }

    @Test
    fun `a truncated fixed-length field is dropped rather than half-read`() {
        val code = Gs1Parser.parse("01${gtin14}172605")
        assertEquals(gtin14, code.gtin)
        assertNull(code.expiry)
    }

    // --- expiry dates -------------------------------------------------------

    @Test
    fun `day zero means the end of that month`() {
        assertEquals(LocalDate.of(2026, 5, 31), Gs1Parser.parse("01${gtin14}17260500").expiry)
        assertEquals(LocalDate.of(2026, 2, 28), Gs1Parser.parse("01${gtin14}17260200").expiry)
        // A leap year still lands on the last day.
        assertEquals(LocalDate.of(2028, 2, 29), Gs1Parser.parse("01${gtin14}17280200").expiry)
    }

    @Test
    fun `an impossible date is dropped but the product number survives`() {
        val code = Gs1Parser.parse("01${gtin14}17260231")
        assertEquals(gtin14, code.gtin)
        assertNull(code.expiry)
        assertNull(Gs1Parser.parse("01${gtin14}17261301").expiry)
    }

    // --- rejecting what should be rejected ----------------------------------

    @Test
    fun `a wrong check digit is not accepted as a product number`() {
        // Same code as ean13 with the check digit changed.
        val code = Gs1Parser.parse("8712345678905")
        assertNull(code.gtin)
        assertFalse(code.isUsable)
        assertEquals("8712345678905", code.raw)
    }

    @Test
    fun `null, empty and nonsense input do not throw`() {
        for (input in listOf(null, "", "   ", "not a barcode", "01", "]d2", "0000000000000")) {
            val code = Gs1Parser.parse(input)
            assertNull("expected no gtin for '$input'", code.gtin)
            assertFalse(code.isUsable)
        }
    }

    @Test
    fun `a usable code reports itself as usable`() {
        assertTrue(Gs1Parser.parse(ean13).isUsable)
    }

    @Test
    fun `normaliseGtin rejects codes that are too long`() {
        assertNull(Gs1Parser.normaliseGtin("123456789012345"))
        assertNull(Gs1Parser.normaliseGtin("abcdefghijklm"))
        assertNull(Gs1Parser.normaliseGtin(""))
    }
}

package nl.ramon96.medicijntracker.domain.barcode

import java.time.DateTimeException
import java.time.LocalDate

/**
 * What a scan of a medicine box yielded.
 *
 * The interesting part is [gtin]: the same box carries its product number in two shapes - a 13-digit
 * EAN-13 printed as a linear barcode, and a 14-digit GTIN inside the GS1 DataMatrix that EU
 * prescription packs have carried since 2019. Both are normalised to 14 digits here so that scanning
 * either one recognises the same medicine.
 */
data class ScannedCode(
    /** Exactly what the scanner handed back, kept even when nothing could be made of it. */
    val raw: String,
    /** Canonical product number: 14 digits, zero-padded. Null when no valid GTIN was found. */
    val gtin: String? = null,
    val expiry: LocalDate? = null,
    val batch: String? = null,
    val serial: String? = null,
) {
    val isUsable: Boolean get() = gtin != null

    /**
     * The 13-digit form, i.e. what is printed under the linear barcode. Databases that key on the
     * printed number want this rather than the padded GTIN-14.
     */
    val ean13: String? get() = gtin?.takeIf { it.length == 14 && it.startsWith("0") }?.substring(1)
}

/**
 * Reads the barcodes found on medicine packaging.
 *
 * Two shapes arrive here:
 *
 *  - a bare number (EAN-8, UPC-A, EAN-13, GTIN-14) from the linear barcode;
 *  - a GS1 element string from the DataMatrix, where the payload is a run of `AI`-prefixed fields:
 *    `01` product number, `17` expiry, `10` batch, `21` serial.
 *
 * Deliberately free of Android types so the awkward parts are covered by plain JVM tests, the same
 * arrangement as `ScheduleCalculator` and `StockForecaster`.
 */
object Gs1Parser {

    /** Separates variable-length fields. Encoded as FNC1 in the symbol itself. */
    private const val GROUP_SEPARATOR = '\u001D'

    /** Application identifiers whose value has a known, fixed width. */
    private val FIXED_LENGTH_AIS = mapOf(
        "00" to 18, // SSCC
        "01" to 14, // GTIN
        "02" to 14, // GTIN of contained trade items
        "11" to 6,  // production date
        "12" to 6,  // due date
        "13" to 6,  // packaging date
        "15" to 6,  // best before
        "16" to 6,  // sell by
        "17" to 6,  // expiry
        "20" to 2,  // variant
    )

    /** Application identifiers we read the value of; the rest are skipped or stop the parse. */
    private val VARIABLE_LENGTH_AIS = setOf("10", "21", "240", "241", "30", "710", "711", "712", "714")

    fun parse(raw: String?): ScannedCode {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) return ScannedCode(raw = input)

        val payload = stripSymbologyIdentifier(input).trim(GROUP_SEPARATOR)

        // A plain linear barcode: nothing but digits, and no application identifiers to read.
        if (payload.all { it.isDigit() } && payload.length in setOf(8, 12, 13, 14)) {
            return ScannedCode(raw = input, gtin = normaliseGtin(payload))
        }

        return parseElementString(input, payload)
    }

    /**
     * Pads a product number to 14 digits and checks its mod-10 check digit.
     *
     * Returns null when the number is not a plausible GTIN, so that a misread never gets stored as
     * if it identified a box.
     */
    fun normaliseGtin(digits: String): String? {
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        if (digits.length > 14) return null
        val padded = digits.padStart(14, '0')
        // An all-zero code is what a truncated read looks like, not a real product.
        if (padded.all { it == '0' }) return null
        return padded.takeIf { hasValidCheckDigit(it) }
    }

    /**
     * GS1 mod-10: weight the digits 3,1,3,1,... from the right of the payload, and the check digit
     * is whatever brings the total up to a multiple of ten.
     */
    private fun hasValidCheckDigit(gtin14: String): Boolean {
        val body = gtin14.dropLast(1)
        val expected = gtin14.last().digitToInt()
        var sum = 0
        for ((index, char) in body.reversed().withIndex()) {
            sum += char.digitToInt() * if (index % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10 == expected
    }

    /**
     * Some scanners prefix the payload with the symbology they read it from - `]d2` for DataMatrix,
     * `]C1` for GS1-128, `]e0` for GS1 DataBar. It is not part of the data.
     */
    private fun stripSymbologyIdentifier(input: String): String =
        if (input.length > 3 && input[0] == ']') input.substring(3) else input

    private fun parseElementString(raw: String, payload: String): ScannedCode {
        // Whether the scanner preserved the separators decides how far we can safely read: without
        // them there is no way to tell where a variable-length field ends.
        val hasSeparators = payload.contains(GROUP_SEPARATOR)

        var gtin: String? = null
        var expiry: LocalDate? = null
        var batch: String? = null
        var serial: String? = null

        var cursor = 0
        while (cursor < payload.length) {
            if (payload[cursor] == GROUP_SEPARATOR) {
                cursor++
                continue
            }
            // Every AI is at least two digits; anything else means we have lost the thread.
            if (cursor + 2 > payload.length) break
            val ai2 = payload.substring(cursor, cursor + 2)
            if (!ai2.all { it.isDigit() }) break

            val fixedWidth = FIXED_LENGTH_AIS[ai2]
            if (fixedWidth != null) {
                val start = cursor + 2
                val end = start + fixedWidth
                if (end > payload.length) break
                val value = payload.substring(start, end)
                when (ai2) {
                    "01" -> gtin = normaliseGtin(value)
                    "17" -> expiry = parseExpiry(value)
                }
                cursor = end
                continue
            }

            // Variable-length fields run until the next separator. When the scanner dropped the
            // separators, stop here rather than guess: a wrong batch number is worse than none,
            // and the GTIN and expiry we care about are fixed-length and already read.
            val ai = longestVariableAiAt(payload, cursor) ?: break
            if (!hasSeparators) break

            val start = cursor + ai.length
            val end = payload.indexOf(GROUP_SEPARATOR, start).takeIf { it >= 0 } ?: payload.length
            val value = payload.substring(start, end)
            when (ai) {
                "10" -> batch = value.takeIf { it.isNotEmpty() }
                "21" -> serial = value.takeIf { it.isNotEmpty() }
            }
            cursor = end
        }

        return ScannedCode(raw = raw, gtin = gtin, expiry = expiry, batch = batch, serial = serial)
    }

    /** Variable-length AIs are two to four digits, so try the longest match first. */
    private fun longestVariableAiAt(payload: String, cursor: Int): String? =
        (4 downTo 2)
            .mapNotNull { length ->
                payload.takeIf { cursor + length <= it.length }?.substring(cursor, cursor + length)
            }
            .firstOrNull { it in VARIABLE_LENGTH_AIS }

    /**
     * `YYMMDD`, where a day of `00` means "the end of that month" - which is what a box printed with
     * only a month and year says.
     *
     * The century is taken as 2000 + YY. GS1 defines a sliding window relative to the current date,
     * but that would make this function depend on today's date, and a medicine box expiring before
     * 2000 or after 2099 is not a case this app has to be right about.
     */
    private fun parseExpiry(value: String): LocalDate? {
        if (value.length != 6 || !value.all { it.isDigit() }) return null
        val year = 2000 + value.substring(0, 2).toInt()
        val month = value.substring(2, 4).toInt()
        val day = value.substring(4, 6).toInt()
        if (month !in 1..12) return null
        val firstOfMonth = LocalDate.of(year, month, 1)
        return try {
            if (day == 0) firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth())
            else LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            // An impossible date on the box - keep the product number, drop the date.
            null
        }
    }
}

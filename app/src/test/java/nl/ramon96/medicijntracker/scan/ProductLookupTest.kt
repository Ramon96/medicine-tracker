package nl.ramon96.medicijntracker.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the part of the lookup that decides whether a reply actually contains a product. No
 * network: the bodies below are pasted shapes of what Open Products Facts returns.
 */
class ProductLookupTest {

    private fun found(body: String): ProductSuggestion {
        val result = ProductLookup.parse(body)
        assertTrue("expected a product, got $result", result is LookupResult.Found)
        return (result as LookupResult.Found).suggestion
    }

    @Test
    fun `a complete reply becomes a suggestion`() {
        val suggestion = found(
            """
            {"status":1,"code":"8712345678906","product":{
              "product_name":"Paracetamol 500mg",
              "brands":"Kruidvat",
              "quantity":"20 tabletten"
            }}
            """.trimIndent(),
        )
        assertEquals("Paracetamol 500mg", suggestion.name)
        assertEquals("Kruidvat", suggestion.brand)
        assertEquals("20 tabletten", suggestion.quantity)
    }

    @Test
    fun `the Dutch name wins, because the box is Dutch`() {
        val suggestion = found(
            """
            {"status":1,"product":{
              "product_name":"Paracetamol tablets",
              "product_name_nl":"Paracetamol tabletten",
              "generic_name":"Pijnstiller"
            }}
            """.trimIndent(),
        )
        assertEquals("Paracetamol tabletten", suggestion.name)
    }

    @Test
    fun `the generic name is used when there is nothing better`() {
        assertEquals("Pijnstiller", found("""{"status":1,"product":{"generic_name":"Pijnstiller"}}""").name)
    }

    @Test
    fun `a missing product comes back as 200 with status zero`() {
        assertEquals(
            LookupResult.NotFound,
            ProductLookup.parse("""{"status":0,"status_verbose":"product not found","code":"8712345678906"}"""),
        )
    }

    @Test
    fun `an entry without any name is no use as a suggestion`() {
        assertEquals(
            LookupResult.NotFound,
            ProductLookup.parse("""{"status":1,"product":{"quantity":"20 tabletten"}}"""),
        )
        assertEquals(
            LookupResult.NotFound,
            ProductLookup.parse("""{"status":1,"product":{"product_name":"   "}}"""),
        )
    }

    @Test
    fun `a status of one without a product object is still nothing`() {
        assertEquals(LookupResult.NotFound, ProductLookup.parse("""{"status":1}"""))
    }

    @Test
    fun `blank brand and quantity are dropped rather than shown empty`() {
        val suggestion = found("""{"status":1,"product":{"product_name":"Paracetamol","brands":"","quantity":"  "}}""")
        assertEquals(null, suggestion.brand)
        assertEquals(null, suggestion.quantity)
    }

    @Test
    fun `a body that is not JSON does not throw`() {
        assertEquals(
            LookupResult.Failed(LookupError.UNKNOWN),
            ProductLookup.parse("<html>502 Bad Gateway</html>"),
        )
        assertEquals(LookupResult.Failed(LookupError.UNKNOWN), ProductLookup.parse(""))
    }
}

package nl.ramon96.medicijntracker.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `tag maps to the same code the build file produces`() {
        assertEquals(10000, AppVersion.codeFromTag("v1.0.0"))
        assertEquals(10203, AppVersion.codeFromTag("v1.2.3"))
        assertEquals(20000, AppVersion.codeFromTag("v2.0.0"))
        // The leading v is optional.
        assertEquals(10203, AppVersion.codeFromTag("1.2.3"))
    }

    @Test
    fun `codes increase in release order`() {
        val ordered = listOf("v1.0.0", "v1.0.1", "v1.1.0", "v1.10.0", "v2.0.0")
            .map { AppVersion.codeFromTag(it)!! }
        assertEquals(ordered.sorted(), ordered)
    }

    @Test
    fun `pre-release suffix is ignored`() {
        assertEquals(10200, AppVersion.codeFromTag("v1.2.0-beta1"))
    }

    @Test
    fun `nonsense tags produce no version`() {
        assertNull(AppVersion.codeFromTag(null))
        assertNull(AppVersion.codeFromTag(""))
        assertNull(AppVersion.codeFromTag("latest"))
        assertNull(AppVersion.codeFromTag("v1.2"))
        assertNull(AppVersion.codeFromTag("v1.2.3.4"))
        assertNull(AppVersion.codeFromTag("vX.Y.Z"))
        // Out of the range the scheme can encode without colliding.
        assertNull(AppVersion.codeFromTag("v1.100.0"))
    }

    @Test
    fun `only a strictly higher version counts as an update`() {
        val installed = AppVersion.codeFromTag("v1.2.0")!!
        assertTrue(AppVersion.isNewerThan("v1.2.1", installed))
        assertTrue(AppVersion.isNewerThan("v1.3.0", installed))
        assertTrue(AppVersion.isNewerThan("v2.0.0", installed))

        // Same version must not offer an endless update loop.
        assertFalse(AppVersion.isNewerThan("v1.2.0", installed))
        assertFalse(AppVersion.isNewerThan("v1.1.9", installed))
        assertFalse(AppVersion.isNewerThan("v0.9.0", installed))
    }

    @Test
    fun `an unreadable tag never triggers an update`() {
        assertFalse(AppVersion.isNewerThan(null, 10000))
        assertFalse(AppVersion.isNewerThan("nightly", 10000))
    }
}

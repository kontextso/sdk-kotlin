package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreenOrientationTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `fromString parses canonical wire values`() {
        assertEquals(ScreenOrientation.PORTRAIT, ScreenOrientation.fromString("portrait"))
        assertEquals(ScreenOrientation.LANDSCAPE, ScreenOrientation.fromString("landscape"))
    }

    @Test
    fun `fromString is case-insensitive`() {
        assertEquals(ScreenOrientation.PORTRAIT, ScreenOrientation.fromString("PORTRAIT"))
        assertEquals(ScreenOrientation.LANDSCAPE, ScreenOrientation.fromString("Landscape"))
    }

    @Test
    fun `fromString returns null for unknown, empty, or null input`() {
        assertNull(ScreenOrientation.fromString("diagonal"))
        assertNull(ScreenOrientation.fromString(""))
        assertNull(ScreenOrientation.fromString(null))
    }

    @Test
    fun `serializes to lowercase wire value`() {
        assertEquals("\"portrait\"", json.encodeToString(ScreenOrientation.PORTRAIT))
        assertEquals("\"landscape\"", json.encodeToString(ScreenOrientation.LANDSCAPE))
    }
}

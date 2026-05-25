package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes all six fields with the orientation wire form`() {
        val s = json.encodeToString(
            ScreenDto(
                width = 1080,
                height = 2400,
                dpr = 2.75,
                darkMode = true,
                orientation = ScreenOrientation.LANDSCAPE,
                brightness = 50.0,
            ),
        )
        assertTrue(s.contains("\"width\":1080"))
        assertTrue(s.contains("\"height\":2400"))
        assertTrue(s.contains("\"dpr\":2.75"))
        assertTrue(s.contains("\"darkMode\":true"))
        assertTrue(s.contains("\"orientation\":\"landscape\""))
        assertTrue(s.contains("\"brightness\":50.0"))
    }

    @Test
    fun `round-trips all fields`() {
        val original = ScreenDto(720, 1280, 2.0, false, ScreenOrientation.PORTRAIT, 0.0)
        assertEquals(original, json.decodeFromString<ScreenDto>(json.encodeToString(original)))
    }
}

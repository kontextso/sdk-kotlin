package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OsDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes name, version, locale (BCP-47) and timezone (IANA)`() {
        val s = json.encodeToString(OsDto(name = "android", version = "14", locale = "en-US", timezone = "America/New_York"))
        assertTrue(s.contains("\"name\":\"android\""))
        assertTrue(s.contains("\"version\":\"14\""))
        assertTrue(s.contains("\"locale\":\"en-US\""))
        assertTrue(s.contains("\"timezone\":\"America/New_York\""))
    }

    @Test
    fun `round-trips all fields`() {
        val original = OsDto(name = "android", version = "13", locale = "cs-CZ", timezone = "UTC")
        assertEquals(original, json.decodeFromString<OsDto>(json.encodeToString(original)))
    }
}

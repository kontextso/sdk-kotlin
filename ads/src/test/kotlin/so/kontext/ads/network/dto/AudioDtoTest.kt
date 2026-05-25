package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes a populated outputType list to lowercase enum wire values`() {
        val s = json.encodeToString(
            AudioDto(
                volume = 80,
                muted = true,
                outputPluggedIn = true,
                outputType = listOf(AudioOutputType.BLUETOOTH, AudioOutputType.USB),
            ),
        )
        assertTrue(s.contains("\"volume\":80"))
        assertTrue(s.contains("\"muted\":true"))
        assertTrue(s.contains("\"outputPluggedIn\":true"))
        assertTrue(s.contains("\"outputType\":[\"bluetooth\",\"usb\"]"))
    }

    @Test
    fun `decodes a populated outputType list back to typed enums`() {
        val dto = json.decodeFromString<AudioDto>(
            """{"volume":80,"muted":true,"outputPluggedIn":true,"outputType":["bluetooth","usb"]}""",
        )
        assertEquals(listOf(AudioOutputType.BLUETOOTH, AudioOutputType.USB), dto.outputType)
    }

    @Test
    fun `encodes an empty outputType list`() {
        val s = json.encodeToString(
            AudioDto(volume = 0, muted = false, outputPluggedIn = false, outputType = emptyList()),
        )
        assertTrue(s.contains("\"outputType\":[]"))
    }
}

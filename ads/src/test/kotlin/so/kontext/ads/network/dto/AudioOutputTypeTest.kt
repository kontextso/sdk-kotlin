package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AudioOutputTypeTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `fromString parses canonical wire values`() {
        assertEquals(AudioOutputType.WIRED, AudioOutputType.fromString("wired"))
        assertEquals(AudioOutputType.HDMI, AudioOutputType.fromString("hdmi"))
        assertEquals(AudioOutputType.BLUETOOTH, AudioOutputType.fromString("bluetooth"))
        assertEquals(AudioOutputType.USB, AudioOutputType.fromString("usb"))
        assertEquals(AudioOutputType.OTHER, AudioOutputType.fromString("other"))
    }

    @Test
    fun `fromString is case-insensitive`() {
        assertEquals(AudioOutputType.WIRED, AudioOutputType.fromString("WIRED"))
        assertEquals(AudioOutputType.BLUETOOTH, AudioOutputType.fromString("BlueTooth"))
    }

    @Test
    fun `fromString returns null for unknown, empty, or null input`() {
        // Unlike NetworkType / HardwareType / BatteryState (which fall back
        // to OTHER), this returns null so DeviceCollector's mapNotNull
        // drops unrecognised entries from the list rather than emitting
        // a misleading "other" placeholder.
        assertNull(AudioOutputType.fromString("speaker"))
        assertNull(AudioOutputType.fromString(""))
        assertNull(AudioOutputType.fromString(null))
    }

    @Test
    fun `serializes to lowercase wire value`() {
        assertEquals("\"wired\"", json.encodeToString(AudioOutputType.WIRED))
        assertEquals("\"hdmi\"", json.encodeToString(AudioOutputType.HDMI))
        assertEquals("\"bluetooth\"", json.encodeToString(AudioOutputType.BLUETOOTH))
        assertEquals("\"usb\"", json.encodeToString(AudioOutputType.USB))
        assertEquals("\"other\"", json.encodeToString(AudioOutputType.OTHER))
    }
}

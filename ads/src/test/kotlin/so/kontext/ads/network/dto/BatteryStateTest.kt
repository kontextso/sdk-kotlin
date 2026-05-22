package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryStateTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `fromString parses canonical wire values`() {
        assertEquals(BatteryState.CHARGING, BatteryState.fromString("charging"))
        assertEquals(BatteryState.FULL, BatteryState.fromString("full"))
        assertEquals(BatteryState.UNPLUGGED, BatteryState.fromString("unplugged"))
        assertEquals(BatteryState.UNKNOWN, BatteryState.fromString("unknown"))
    }

    @Test
    fun `fromString is case-insensitive`() {
        assertEquals(BatteryState.CHARGING, BatteryState.fromString("CHARGING"))
        assertEquals(BatteryState.UNPLUGGED, BatteryState.fromString("Unplugged"))
    }

    @Test
    fun `fromString returns null for unknown, empty, or null input`() {
        // Mirrors Swift's `BatteryState(rawValue:)` shape — fallback to
        // UNKNOWN is the call-site's job (DeviceCollector does
        // `?: BatteryState.UNKNOWN`), not the helper's.
        assertNull(BatteryState.fromString("discharging"))
        assertNull(BatteryState.fromString(""))
        assertNull(BatteryState.fromString(null))
    }

    @Test
    fun `serializes to lowercase wire value`() {
        assertEquals("\"charging\"", json.encodeToString(BatteryState.CHARGING))
        assertEquals("\"full\"", json.encodeToString(BatteryState.FULL))
        assertEquals("\"unplugged\"", json.encodeToString(BatteryState.UNPLUGGED))
        assertEquals("\"unknown\"", json.encodeToString(BatteryState.UNKNOWN))
    }
}

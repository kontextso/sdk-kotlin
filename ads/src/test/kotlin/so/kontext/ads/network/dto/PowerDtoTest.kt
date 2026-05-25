package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PowerDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes batteryLevel and the typed batteryState wire form`() {
        val s = json.encodeToString(
            PowerDto(lowPowerMode = true, batteryState = BatteryState.CHARGING, batteryLevel = 87.5),
        )
        assertTrue(s.contains("\"lowPowerMode\":true"))
        assertTrue(s.contains("\"batteryState\":\"charging\""))
        assertTrue(s.contains("\"batteryLevel\":87.5"))
    }

    @Test
    fun `omits a null batteryLevel with encodeDefaults false`() {
        val s = json.encodeToString(PowerDto(lowPowerMode = false, batteryState = BatteryState.UNKNOWN))
        assertTrue(s.contains("\"batteryState\":\"unknown\""))
        assertFalse(s.contains("batteryLevel"))
    }

    @Test
    fun `decodes with batteryLevel defaulting to null`() {
        val dto = json.decodeFromString<PowerDto>("""{"lowPowerMode":false,"batteryState":"unknown"}""")
        assertEquals(BatteryState.UNKNOWN, dto.batteryState)
        assertNull(dto.batteryLevel)
    }
}

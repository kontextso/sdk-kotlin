package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `serializes nested structure with typed enums`() {
        val device = DeviceDto(
            hardware = HardwareDto(
                type = HardwareType.HANDSET,
                brand = "Google",
                model = "Pixel 8",
                bootTime = 1_700_000_000_000L,
                sdCardAvailable = false,
            ),
            os = OsDto(name = "android", version = "14", locale = "en-US", timezone = "America/New_York"),
            screen = ScreenDto(
                width = 1080,
                height = 2400,
                dpr = 2.75,
                darkMode = false,
                orientation = ScreenOrientation.PORTRAIT,
                brightness = 50.0,
            ),
            power = PowerDto(lowPowerMode = false, batteryState = BatteryState.UNKNOWN),
            audio = AudioDto(volume = 50, muted = false, outputPluggedIn = false, outputType = emptyList()),
        )

        val serialized = json.encodeToString(device)
        assertTrue(serialized.contains("\"name\":\"android\""))
        assertTrue(serialized.contains("\"brand\":\"Google\""))
        assertTrue(serialized.contains("\"model\":\"Pixel 8\""))
        assertTrue(serialized.contains("\"bootTime\":1700000000000"))
        assertTrue(serialized.contains("\"sdCardAvailable\":false"))
        assertTrue(serialized.contains("\"width\":1080"))
        // Typed enums encode via @SerialName — wire form matches the
        // server's lowercase string enums.
        assertTrue(serialized.contains("\"type\":\"handset\""))
        assertTrue(serialized.contains("\"orientation\":\"portrait\""))
    }
}

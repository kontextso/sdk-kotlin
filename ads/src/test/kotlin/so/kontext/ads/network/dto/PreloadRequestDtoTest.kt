package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.model.Role

class PreloadRequestDtoTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private val testDevice = DeviceDto(
        hardware = HardwareDto(type = HardwareType.HANDSET, brand = "Google", model = "Pixel 8", bootTime = 1_700_000_000_000L, sdCardAvailable = false),
        os = OsDto(name = "android", version = "14", locale = "en-US", timezone = "UTC"),
        screen = ScreenDto(width = 1080, height = 2400, dpr = 2.75, darkMode = false, orientation = ScreenOrientation.PORTRAIT, brightness = 50.0),
        power = PowerDto(lowPowerMode = false, batteryState = BatteryState.UNKNOWN),
        audio = AudioDto(volume = 50, muted = false, outputPluggedIn = false, outputType = emptyList()),
    )
    private val testApp = AppDto(bundleId = "test.bundle", version = "1.0.0")

    @Test
    fun `serializes correctly`() {
        val dto = PreloadRequestDto(
            publisherToken = "tok",
            userId = "user",
            conversationId = "conv",
            enabledPlacementCodes = listOf("inlineAd"),
            messages = listOf(
                MessageDto(id = "m1", role = Role.USER, content = "Hello", createdAt = "2024-01-01T00:00:00.000Z"),
            ),
            sdk = SdkDto(name = "sdk-kotlin", version = "4.0.0", platform = "android"),
            device = testDevice,
            app = testApp,
        )

        val serialized = json.encodeToString(dto)
        assertTrue(serialized.contains("\"publisherToken\":\"tok\""))
        assertTrue(serialized.contains("\"enabledPlacementCodes\":[\"inlineAd\"]"))
        assertTrue(serialized.contains("\"role\":\"user\""))
    }

    @Test
    fun `omits null optional fields`() {
        val dto = PreloadRequestDto(
            publisherToken = "tok",
            userId = "user",
            conversationId = "conv",
            enabledPlacementCodes = listOf("inlineAd"),
            messages = emptyList(),
            sdk = SdkDto(name = "sdk-kotlin", version = "4.0.0", platform = "android"),
            device = testDevice,
            app = testApp,
        )

        val serialized = json.encodeToString(dto)
        assertFalse(serialized.contains("sessionId"))
        assertFalse(serialized.contains("character"))
        assertFalse(serialized.contains("regulatory"))
    }

    @Test
    fun `serializes UUID sessionId via UuidSerializer`() {
        val sessionUuid = java.util.UUID.fromString("33333333-3333-3333-3333-333333333333")
        val dto = PreloadRequestDto(
            publisherToken = "tok",
            userId = "u",
            conversationId = "c",
            enabledPlacementCodes = listOf("inlineAd"),
            messages = emptyList(),
            sdk = SdkDto(name = "sdk-kotlin", version = "4.0.0", platform = "android"),
            device = testDevice,
            app = testApp,
            sessionId = sessionUuid,
        )

        val serialized = json.encodeToString(dto)
        assertTrue(serialized.contains("\"sessionId\":\"33333333-3333-3333-3333-333333333333\""))
    }
}

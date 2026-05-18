package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.TEST_INSTALL_ID

class InitRequestDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `serializes the typed AppMetadata shape`() {
        val dto = InitRequestDto(
            publisherToken = "tok",
            userId = "user-1",
            installId = TEST_INSTALL_ID,
            sdk = SdkDto(name = "sdk-kotlin", platform = "android", version = "4.0.0"),
            app = InitRequestDto.AppMetadata(bundleId = "so.kontext.example", version = "1.2.3"),
        )
        val serialized = json.encodeToString(dto)
        assertTrue(serialized.contains("\"publisherToken\":\"tok\""))
        // userId is sent on every /init so the server can target
        // per-user toggles (reportErrors / reportDebug) in the response.
        assertTrue(serialized.contains("\"userId\":\"user-1\""))
        assertTrue(serialized.contains("\"app\":{"))
        assertTrue(serialized.contains("\"bundleId\":\"so.kontext.example\""))
        assertTrue(serialized.contains("\"version\":\"1.2.3\""))
        // sdk lands as a nested object
        assertTrue(serialized.contains("\"sdk\":{"))
    }

    @Test
    fun `serializes installId at top level for per-install attribution`() {
        // The server keys pacing / frequency caps / per-install diagnostics
        // on `installId`. Sent on every /init request to register the field
        // before any /preload fires. Mirrors sdk-swift `InitRequestDTO`.
        val dto = InitRequestDto(
            publisherToken = "tok",
            userId = "user-1",
            installId = TEST_INSTALL_ID,
            sdk = SdkDto(name = "sdk-kotlin", platform = "android", version = "4.0.0"),
            app = InitRequestDto.AppMetadata(bundleId = "so.kontext.example", version = "1.2.3"),
        )
        val serialized = json.encodeToString(dto)
        assertTrue(serialized.contains("\"installId\":\"$TEST_INSTALL_ID\""))
    }
}

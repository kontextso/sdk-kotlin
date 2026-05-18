package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.TEST_INSTALL_ID

class ErrorRequestDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `serializes the typed AdditionalData shape`() {
        val dto = ErrorRequestDto(
            error = "Something broke",
            stack = "at line 1",
            additionalData = ErrorRequestDto.AdditionalData(
                publisherToken = "tok",
                conversationId = "conv",
                userId = "user",
                installId = TEST_INSTALL_ID,
                bidId = "bid",
                sdk = SdkDto(name = "sdk-kotlin", platform = "android", version = "4.0.0"),
            ),
        )
        val serialized = json.encodeToString(dto)
        assertTrue(serialized.contains("\"error\":\"Something broke\""))
        assertTrue(serialized.contains("\"stack\":\"at line 1\""))
        assertTrue(serialized.contains("\"additionalData\":"))
        assertTrue(serialized.contains("\"publisherToken\":\"tok\""))
        assertTrue(serialized.contains("\"bidId\":\"bid\""))
        // sdk lands as a nested object, not a string
        assertTrue(serialized.contains("\"sdk\":{"))
        assertTrue(serialized.contains("\"name\":\"sdk-kotlin\""))
    }

    @Test
    fun `serializes installId inside additionalData for per-install attribution`() {
        // installId lives on `additionalData` (not the top-level error body)
        // so the server's error-ingestion pipeline can attribute reports to
        // a stable install identity without coupling to error-message
        // parsing. Mirrors sdk-swift `ErrorRequestDTO.AdditionalData`.
        val dto = ErrorRequestDto(
            error = "boom",
            additionalData = ErrorRequestDto.AdditionalData(
                installId = TEST_INSTALL_ID,
                sdk = SdkDto(name = "sdk-kotlin", platform = "android", version = "4.0.0"),
            ),
        )
        val serialized = json.encodeToString(dto)
        assertTrue(serialized.contains("\"installId\":\"$TEST_INSTALL_ID\""))
    }
}

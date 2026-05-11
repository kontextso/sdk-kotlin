package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}

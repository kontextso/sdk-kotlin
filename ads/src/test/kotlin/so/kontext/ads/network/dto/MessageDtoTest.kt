package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.model.Role

class MessageDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes the role wire form and createdAt verbatim`() {
        val s = json.encodeToString(
            MessageDto(id = "m1", role = Role.USER, content = "hi", createdAt = "2024-01-01T00:00:00.000Z"),
        )
        assertTrue(s.contains("\"id\":\"m1\""))
        assertTrue(s.contains("\"role\":\"user\""))
        assertTrue(s.contains("\"content\":\"hi\""))
        assertTrue(s.contains("\"createdAt\":\"2024-01-01T00:00:00.000Z\""))
    }

    @Test
    fun `decodes the assistant role and createdAt`() {
        val dto = json.decodeFromString<MessageDto>(
            """{"id":"m2","role":"assistant","content":"yo","createdAt":"2024-02-02T03:04:05.678Z"}""",
        )
        assertEquals(Role.ASSISTANT, dto.role)
        assertEquals("2024-02-02T03:04:05.678Z", dto.createdAt)
    }
}

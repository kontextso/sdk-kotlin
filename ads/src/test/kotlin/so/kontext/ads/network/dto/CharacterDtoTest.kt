package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharacterDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes all fields when fully populated`() {
        val s = json.encodeToString(
            CharacterDto(
                id = "c1",
                name = "Aria",
                avatarUrl = "https://cdn.example/a.png",
                greeting = "hi",
                persona = "friendly",
                tags = listOf("anime", "rpg"),
                isNsfw = true,
            ),
        )
        assertTrue(s.contains("\"id\":\"c1\""))
        assertTrue(s.contains("\"name\":\"Aria\""))
        assertTrue(s.contains("\"avatarUrl\":\"https://cdn.example/a.png\""))
        assertTrue(s.contains("\"greeting\":\"hi\""))
        assertTrue(s.contains("\"persona\":\"friendly\""))
        assertTrue(s.contains("\"tags\":[\"anime\",\"rpg\"]"))
        assertTrue(s.contains("\"isNsfw\":true"))
    }

    @Test
    fun `omits null optionals for a minimal character`() {
        val s = json.encodeToString(CharacterDto(id = "c1", name = "Aria", avatarUrl = "https://cdn.example/a.png"))
        assertFalse(s.contains("greeting"))
        assertFalse(s.contains("persona"))
        assertFalse(s.contains("tags"))
        assertFalse(s.contains("isNsfw"))
    }

    @Test
    fun `decodes a minimal character with optionals defaulting to null`() {
        val dto = json.decodeFromString<CharacterDto>(
            """{"id":"c1","name":"Aria","avatarUrl":"https://cdn.example/a.png"}""",
        )
        assertEquals("c1", dto.id)
        assertNull(dto.greeting)
        assertNull(dto.persona)
        assertNull(dto.tags)
        assertNull(dto.isNsfw)
    }
}

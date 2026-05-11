package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI

class CharacterTest {

    @Test
    fun `creates with required fields only`() {
        val char = Character(id = "c1", name = "Bot")

        assertEquals("c1", char.id)
        assertEquals("Bot", char.name)
        assertNull(char.avatarUrl)
        assertNull(char.greeting)
        assertNull(char.persona)
        assertNull(char.tags)
        assertNull(char.isNsfw)
    }

    @Test
    fun `toDto preserves all fields`() {
        val character = Character(
            id = "c1",
            name = "Bot",
            avatarUrl = URI.create("https://example.com/a.png"),
            greeting = "Hi!",
            persona = "Friendly",
            tags = listOf("genre:scifi", "trait:helpful"),
            isNsfw = false,
        )

        val dto = character.toDto()

        assertEquals("c1", dto.id)
        assertEquals("Bot", dto.name)
        assertEquals("https://example.com/a.png", dto.avatarUrl)
        assertEquals("Hi!", dto.greeting)
        assertEquals("Friendly", dto.persona)
        assertEquals(listOf("genre:scifi", "trait:helpful"), dto.tags)
        assertEquals(false, dto.isNsfw)
    }

    @Test
    fun `toDto serializes URI via toString`() {
        // URI keeps the original form (no normalization, no DNS lookup).
        val character = Character(
            id = "c1",
            name = "Bot",
            avatarUrl = URI.create("https://cdn.example.com/avatars/c1.png?v=2"),
        )

        assertEquals(
            "https://cdn.example.com/avatars/c1.png?v=2",
            character.toDto().avatarUrl,
        )
    }

    @Test
    fun `toDto omits avatarUrl when null`() {
        assertNull(Character(id = "c1", name = "Bot").toDto().avatarUrl)
    }
}

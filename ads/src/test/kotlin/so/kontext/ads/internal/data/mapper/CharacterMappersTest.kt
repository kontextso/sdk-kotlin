package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.Character
import so.kontext.ads.internal.data.dto.request.CharacterDto

class CharacterMappersTest {

    @Test
    fun `Character toDto copies every field`() {
        val dto = Character(
            id = "char-1",
            name = "Max",
            avatarUrl = "https://cdn.example/a.png",
            isNsfw = false,
            greeting = "Hi",
            persona = "friendly",
            tags = listOf("fantasy", "adventure"),
        ).toDto()

        assertEquals("char-1", dto.id)
        assertEquals("Max", dto.name)
        assertEquals("https://cdn.example/a.png", dto.avatarUrl)
        assertEquals(false, dto.isNsfw)
        assertEquals("Hi", dto.greeting)
        assertEquals("friendly", dto.persona)
        assertEquals(listOf("fantasy", "adventure"), dto.tags)
    }

    @Test
    fun `Character toDto preserves null optional fields`() {
        val dto = Character().toDto()
        assertNull(dto.id)
        assertNull(dto.name)
        assertNull(dto.avatarUrl)
        assertNull(dto.isNsfw)
        assertNull(dto.greeting)
        assertNull(dto.persona)
        assertNull(dto.tags)
    }

    @Test
    fun `CharacterDto toDomain round-trips`() {
        val original = Character(
            id = "c",
            name = "n",
            avatarUrl = "u",
            isNsfw = true,
            greeting = "g",
            persona = "p",
            tags = listOf("t"),
        )
        val dto = original.toDto()
        val back = dto.toDomain()
        assertEquals(original, back)
    }

    @Test
    fun `CharacterDto toDomain handles an empty DTO`() {
        val domain = CharacterDto(
            id = null,
            name = null,
            avatarUrl = null,
            isNsfw = null,
            greeting = null,
            persona = null,
            tags = null,
        ).toDomain()
        assertEquals(Character(), domain)
    }
}

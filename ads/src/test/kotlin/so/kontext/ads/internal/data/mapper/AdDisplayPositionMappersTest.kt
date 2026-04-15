package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.AdDisplayPosition

class AdDisplayPositionMappersTest {

    @Test
    fun `toDomain maps afterUserMessage to AfterUserMessage`() {
        assertEquals(
            AdDisplayPosition.AfterUserMessage,
            AdDisplayPosition.toDomain("afterUserMessage"),
        )
    }

    @Test
    fun `toDomain maps afterAssistantMessage to AfterAssistantMessage`() {
        assertEquals(
            AdDisplayPosition.AfterAssistantMessage,
            AdDisplayPosition.toDomain("afterAssistantMessage"),
        )
    }

    @Test
    fun `toDomain falls back to AfterUserMessage for unknown string`() {
        assertEquals(
            AdDisplayPosition.AfterUserMessage,
            AdDisplayPosition.toDomain("something-else"),
        )
    }

    @Test
    fun `toDomain is case-sensitive and falls back for different casing`() {
        assertEquals(
            AdDisplayPosition.AfterUserMessage,
            AdDisplayPosition.toDomain("AfterUserMessage"),
        )
    }

    @Test
    fun `toDto maps AfterUserMessage to afterUserMessage`() {
        assertEquals(
            "afterUserMessage",
            AdDisplayPosition.AfterUserMessage.toDto(),
        )
    }

    @Test
    fun `toDto maps AfterAssistantMessage to afterAssistantMessage`() {
        assertEquals(
            "afterAssistantMessage",
            AdDisplayPosition.AfterAssistantMessage.toDto(),
        )
    }

    @Test
    fun `toDto and toDomain round-trip for every enum case`() {
        for (position in AdDisplayPosition.entries) {
            assertEquals(
                position,
                AdDisplayPosition.toDomain(position.toDto()),
                "round-trip failed for $position",
            )
        }
    }
}

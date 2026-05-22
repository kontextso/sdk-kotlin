package so.kontext.ads.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class UuidSerializerTest {

    @Serializable
    private data class Holder(
        @Serializable(with = UuidSerializer::class) val id: UUID,
    )

    private val json = Json

    @Test
    fun `serializes UUID to canonical string form`() {
        val uuid = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val out = json.encodeToString(Holder.serializer(), Holder(id = uuid))
        assertEquals("""{"id":"11111111-2222-3333-4444-555555555555"}""", out)
    }

    @Test
    fun `deserializes canonical UUID string`() {
        val parsed = json.decodeFromString(
            Holder.serializer(),
            """{"id":"11111111-2222-3333-4444-555555555555"}""",
        )
        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"), parsed.id)
    }

    @Test
    fun `deserialize throws IllegalArgumentException on malformed UUID`() {
        // Matches sdk-swift's strict UUID decode: a non-UUID string is a
        // server bug, not a recoverable case. Caller can wrap to translate
        // into a domain-level failure (Preload does this).
        assertThrows<IllegalArgumentException> {
            json.decodeFromString(Holder.serializer(), """{"id":"not-a-uuid"}""")
        }
    }
}

package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Date

class MessageTest {

    @Test
    fun `Role value is the lowercase name`() {
        assertEquals("user", Role.USER.value)
        assertEquals("assistant", Role.ASSISTANT.value)
    }

    @Test
    fun `creates with defaults`() {
        val msg = Message(id = "m1", role = Role.USER, content = "Hello")

        assertEquals("m1", msg.id)
        assertEquals(Role.USER, msg.role)
        assertEquals("Hello", msg.content)
        assertNotNull(msg.createdAt)
    }

    @Test
    fun `toDto preserves the typed Role`() {
        // The lowercase wire form (`"user"` / `"assistant"`) is produced
        // by kotlinx-serialization via @SerialName — pinned in DtoTest's
        // PreloadRequestDto serialization assertion. Here we just verify
        // the typed enum survives the toDto boundary.
        assertEquals(Role.USER, Message(id = "u", role = Role.USER, content = "Hi").toDto().role)
        assertEquals(Role.ASSISTANT, Message(id = "a", role = Role.ASSISTANT, content = "Hello").toDto().role)
    }

    @Test
    fun `toDto formats createdAt as ISO 8601 with millisecond precision in UTC`() {
        // Epoch is a stable reference point — easier than asserting "now-ish".
        val msg = Message(id = "m1", role = Role.USER, content = "x", createdAt = Date(0L))
        assertEquals("1970-01-01T00:00:00.000Z", msg.toDto().createdAt)
    }

    @Test
    fun `toDto preserves id and content verbatim`() {
        val msg = Message(
            id = "m1",
            role = Role.USER,
            content = "with \"quotes\" and \n newline",
        ).toDto()

        assertEquals("m1", msg.id)
        assertEquals("with \"quotes\" and \n newline", msg.content)
    }
}

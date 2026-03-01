package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.MessageRepresentable
import so.kontext.ads.domain.Role
import java.time.Instant

class ChatMessageMappersTest {
    @Test
    fun `timestamp in seconds is converted to ISO-8601`() {
        val timestampSeconds = 1_759_506_442L
        val message = messageRepresentable(timestampSeconds.toString())
        val chatMessage = message.toInternalMessage()

        assertEquals(Instant.ofEpochSecond(timestampSeconds).toString(), chatMessage.createdAt)
    }

    @Test
    fun `timestamp in milliseconds is converted to ISO-8601`() {
        val timestampMilliseconds = 1_759_506_442_000L
        val message = messageRepresentable(timestampMilliseconds.toString())
        val chatMessage = message.toInternalMessage()

        assertEquals(Instant.ofEpochMilli(timestampMilliseconds).toString(), chatMessage.createdAt)
    }

    @Test
    fun `ISO-8601 timestamp remains unchanged`() {
        val isoTimestamp = "2023-10-01T12:34:56Z"
        val message = messageRepresentable(isoTimestamp)
        val chatMessage = message.toInternalMessage()

        assertEquals(isoTimestamp, chatMessage.createdAt)
    }

    @Test
    fun `ChatMessage toDto() normalized epoch timestamp`() {
        val epochSeconds = 1_759_506_442L
        val chatMessage = ChatMessage(
            id = "id",
            role = Role.User,
            content = "content",
            createdAt = epochSeconds.toString(),
        )

        val dTo = chatMessage.toDto()
        assertEquals(Instant.ofEpochSecond(epochSeconds).toString(), dTo.createdAt)
    }

    @Test
    fun `ChatMessage toDto() keeps ISO-8601 timestamp unchanged`() {
        val isoTimestamp = "2023-10-01T12:34:56Z"
        val chatMessage = ChatMessage(
            id = "id",
            role = Role.User,
            content = "content",
            createdAt = isoTimestamp,
        )

        val dTo = chatMessage.toDto()
        assertEquals(isoTimestamp, dTo.createdAt)
    }

    @Test
    fun `Invalid timestamp remains unchanged`() {
        val invalidTimestamp = "invalid-timestamp"
        val message = messageRepresentable(invalidTimestamp)
        val chatMessage = message.toInternalMessage()

        assertEquals(invalidTimestamp, chatMessage.createdAt)
    }

    private fun messageRepresentable(createdAt: String) = object : MessageRepresentable {
        override val id: String = "id"
        override val role: Role = Role.User
        override val content: String = "content"
        override val createdAt: String = createdAt
    }
}

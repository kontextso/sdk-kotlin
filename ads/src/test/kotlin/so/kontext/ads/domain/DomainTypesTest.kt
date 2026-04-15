package so.kontext.ads.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DomainTypesTest {

    @Test
    fun `Bid data class equality includes every field`() {
        val a = Bid("b", "c", AdDisplayPosition.AfterAssistantMessage, OmCreativeType.DISPLAY, ImpressionTrigger.IMMEDIATE)
        val b = Bid("b", "c", AdDisplayPosition.AfterAssistantMessage, OmCreativeType.DISPLAY, ImpressionTrigger.IMMEDIATE)
        val c = a.copy(bidId = "other")
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Bid default omCreativeType is null and impressionTrigger is IMMEDIATE`() {
        val b = Bid("b", "c", AdDisplayPosition.AfterUserMessage)
        assertNull(b.omCreativeType)
        assertEquals(ImpressionTrigger.IMMEDIATE, b.impressionTrigger)
    }

    @Test
    fun `Character default constructor yields all-null fields`() {
        val c = Character()
        assertNull(c.id)
        assertNull(c.name)
        assertNull(c.avatarUrl)
        assertNull(c.isNsfw)
        assertNull(c.greeting)
        assertNull(c.persona)
        assertNull(c.tags)
    }

    @Test
    fun `Character data class equality`() {
        val c1 = Character(id = "1", name = "Max")
        val c2 = Character(id = "1", name = "Max")
        val c3 = c1.copy(name = "Alex")
        assertEquals(c1, c2)
        assertNotEquals(c1, c3)
    }

    @Test
    fun `ChatMessage data class equality`() {
        val a = ChatMessage("1", Role.User, "hi", "2025-01-01T00:00:00Z")
        val b = ChatMessage("1", Role.User, "hi", "2025-01-01T00:00:00Z")
        val c = a.copy(content = "bye")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `AdsMessage implements MessageRepresentable`() {
        val msg: MessageRepresentable = AdsMessage("1", Role.Assistant, "hi", "2025")
        assertEquals("1", msg.id)
        assertEquals(Role.Assistant, msg.role)
        assertEquals("hi", msg.content)
        assertEquals("2025", msg.createdAt)
    }

    @Test
    fun `Regulatory default constructor yields all-null fields`() {
        val r = Regulatory()
        assertNull(r.gdpr)
        assertNull(r.gdprConsent)
        assertNull(r.coppa)
        assertNull(r.gpp)
        assertNull(r.gppSid)
        assertNull(r.usPrivacy)
    }

    @Test
    fun `Role enum has two values`() {
        assertEquals(2, Role.entries.size)
    }

    @Test
    fun `AdDisplayPosition enum has two values`() {
        assertEquals(2, AdDisplayPosition.entries.size)
    }

    @Test
    fun `OmCreativeType enum has DISPLAY and VIDEO`() {
        assertEquals(setOf(OmCreativeType.DISPLAY, OmCreativeType.VIDEO), OmCreativeType.entries.toSet())
    }

    @Test
    fun `ImpressionTrigger enum has IMMEDIATE and COMPONENT`() {
        assertEquals(setOf(ImpressionTrigger.IMMEDIATE, ImpressionTrigger.COMPONENT), ImpressionTrigger.entries.toSet())
    }

    @Test
    fun `AdConfig data class equality`() {
        val bid = Bid("b", "c", AdDisplayPosition.AfterAssistantMessage)
        val a = AdConfig("a", "f", emptyList(), "m", "sdk", emptyMap(), bid)
        val b = AdConfig("a", "f", emptyList(), "m", "sdk", emptyMap(), bid)
        assertEquals(a, b)
    }
}

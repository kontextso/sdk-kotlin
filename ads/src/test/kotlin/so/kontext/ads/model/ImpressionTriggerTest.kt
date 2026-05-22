package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImpressionTriggerTest {

    @Test
    fun `fromString parses known values`() {
        assertEquals(ImpressionTrigger.IMMEDIATE, ImpressionTrigger.fromString("immediate"))
        assertEquals(ImpressionTrigger.COMPONENT, ImpressionTrigger.fromString("component"))
    }

    @Test
    fun `fromString is case-insensitive`() {
        // Server controls the wire format (always lowercase), but the
        // SDK is tolerant in case the casing ever drifts.
        assertEquals(ImpressionTrigger.IMMEDIATE, ImpressionTrigger.fromString("IMMEDIATE"))
        assertEquals(ImpressionTrigger.COMPONENT, ImpressionTrigger.fromString("COMPONENT"))
        assertEquals(ImpressionTrigger.IMMEDIATE, ImpressionTrigger.fromString("Immediate"))
    }

    @Test
    fun `fromString defaults to IMMEDIATE for unknown or null`() {
        assertEquals(ImpressionTrigger.IMMEDIATE, ImpressionTrigger.fromString("unknown"))
        assertEquals(ImpressionTrigger.IMMEDIATE, ImpressionTrigger.fromString(null))
    }
}

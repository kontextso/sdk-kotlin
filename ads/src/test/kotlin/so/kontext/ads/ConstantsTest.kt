package so.kontext.ads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins values that depend on external systems we don't control. The OMID
 * partner name and version are registered with IAB Tech Lab — silently
 * editing them breaks server-side attribution and can invalidate the
 * SDK's certification. Asserting the literals here forces the change to
 * surface in code review.
 */
class ConstantsTest {

    @Test
    fun `OMID partner name is the IAB-registered identifier`() {
        assertEquals("Kontextso", Constants.OMID_PARTNER_NAME)
    }

    @Test
    fun `OMID partner version matches the IAB-certified release`() {
        assertEquals("1.0.0", Constants.OMID_PARTNER_VERSION)
    }
}

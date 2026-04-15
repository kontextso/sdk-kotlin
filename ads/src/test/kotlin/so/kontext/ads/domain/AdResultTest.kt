package so.kontext.ads.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.data.error.KontextError

class AdResultTest {

    @Test
    fun `Filled is a sealed AdResult carrying a map of ads`() {
        val bid = Bid(
            bidId = "b-1",
            code = "inlineAd",
            adDisplayPosition = AdDisplayPosition.AfterAssistantMessage,
        )
        val ad = AdConfig(
            adServerUrl = "https://a", iFrameUrl = "https://f",
            messages = emptyList(), messageId = "m-1", sdk = "sdk-kotlin",
            otherParams = emptyMap(), bid = bid,
        )
        val filled = AdResult.Filled(ads = mapOf("m-1" to listOf(ad)))
        assertTrue(filled is AdResult)
        assertEquals(1, filled.ads.size)
        assertEquals("m-1", filled.ads.keys.first())
    }

    @Test
    fun `NoFill carries a skipCode`() {
        val nf: AdResult = AdResult.NoFill(skipCode = "unfilled_bid")
        assertTrue(nf is AdResult.NoFill)
        assertEquals("unfilled_bid", (nf as AdResult.NoFill).skipCode)
    }

    @Test
    fun `Error wraps a KontextError`() {
        val cause = KontextError.AdUnavailable()
        val e: AdResult = AdResult.Error(cause)
        assertTrue(e is AdResult.Error)
        assertEquals(cause, (e as AdResult.Error).error)
    }

    @Test
    fun `Cleared is a singleton object`() {
        assertTrue(AdResult.Cleared === AdResult.Cleared)
    }

    @Test
    fun `NoFill data class equality + copy`() {
        val a = AdResult.NoFill(skipCode = "s1")
        val b = AdResult.NoFill(skipCode = "s1")
        val c = AdResult.NoFill(skipCode = "s2")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}

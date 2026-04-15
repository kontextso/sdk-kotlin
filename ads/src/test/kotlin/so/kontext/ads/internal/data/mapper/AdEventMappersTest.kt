package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.ui.AdEvent

class AdEventMappersTest {

    @Test
    fun `Viewed callback maps to AdEvent_Viewed preserving every field`() {
        val event = IFrameEvent.CallbackEvent.Viewed(
            code = "c",
            bidId = "b",
            content = "ad",
            messageId = "m",
            format = "inline",
        ).toPublicAdEvent()

        assertTrue(event is AdEvent.Viewed)
        event as AdEvent.Viewed
        assertEquals("c", event.code)
        assertEquals("b", event.bidId)
        assertEquals("ad", event.content)
        assertEquals("m", event.messageId)
        assertEquals("inline", event.format)
    }

    @Test
    fun `Clicked callback maps to AdEvent_Clicked preserving every field`() {
        val event = IFrameEvent.CallbackEvent.Clicked(
            code = "c",
            bidId = "b",
            content = "ad",
            messageId = "m",
            url = "https://x",
            format = "inline",
            area = "cta",
        ).toPublicAdEvent()

        assertTrue(event is AdEvent.Clicked)
        event as AdEvent.Clicked
        assertEquals("https://x", event.url)
        assertEquals("cta", event.area)
    }

    @Test
    fun `RenderStarted and RenderCompleted map to matching AdEvents`() {
        val started = IFrameEvent.CallbackEvent.RenderStarted("c", "b").toPublicAdEvent()
        val completed = IFrameEvent.CallbackEvent.RenderCompleted("c", "b").toPublicAdEvent()

        assertTrue(started is AdEvent.RenderStarted)
        assertTrue(completed is AdEvent.RenderCompleted)
    }

    @Test
    fun `Error callback maps to AdEvent_Error preserving message and errCode`() {
        val event = IFrameEvent.CallbackEvent.Error(
            code = "c",
            message = "boom",
            errCode = "E42",
        ).toPublicAdEvent()

        assertTrue(event is AdEvent.Error)
        event as AdEvent.Error
        assertEquals("boom", event.message)
        assertEquals("E42", event.errCode)
    }

    @Test
    fun `VideoStarted and VideoCompleted map through`() {
        assertTrue(IFrameEvent.CallbackEvent.VideoStarted("c", "b").toPublicAdEvent() is AdEvent.VideoStarted)
        assertTrue(IFrameEvent.CallbackEvent.VideoCompleted("c", "b").toPublicAdEvent() is AdEvent.VideoCompleted)
    }

    @Test
    fun `RewardGranted maps through`() {
        assertTrue(IFrameEvent.CallbackEvent.RewardGranted("c", "b").toPublicAdEvent() is AdEvent.RewardGranted)
    }

    @Test
    fun `Generic callback preserves code and payload map`() {
        val payload = mapOf("k" to "v", "n" to 42)
        val event = IFrameEvent.CallbackEvent.Generic(
            code = "c",
            payload = payload,
        ).toPublicAdEvent()

        assertTrue(event is AdEvent.Generic)
        event as AdEvent.Generic
        assertEquals("c", event.code)
        assertEquals(payload, event.payload)
    }
}

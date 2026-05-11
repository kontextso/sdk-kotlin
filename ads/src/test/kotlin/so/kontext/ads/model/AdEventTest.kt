package so.kontext.ads.model

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdEventTest {

    private val bid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `name returns the wire-format event identifier for every case`() {
        // Pin the strings — telemetry / cross-platform JS docs key off
        // these names. A drift here is a contract break with sdk-js +
        // sdk-swift, even if no test fails.
        assertEquals("ad.filled", AdEvent.Filled(bidId = bid, code = "inlineAd").name)
        assertEquals("ad.no-fill", AdEvent.NoFill(skipCode = "no_fill").name)
        assertEquals("ad.height", AdEvent.AdHeight(bidId = bid, messageId = "m", height = 0f).name)
        assertEquals(
            "ad.viewed",
            AdEvent.Viewed(bidId = bid, content = "c", messageId = "m", format = "f").name,
        )
        assertEquals(
            "ad.clicked",
            AdEvent.Clicked(bidId = bid, content = "c", messageId = "m", url = "u", format = "f", area = "a").name,
        )
        assertEquals("ad.render-started", AdEvent.RenderStarted(bidId = bid).name)
        assertEquals("ad.render-completed", AdEvent.RenderCompleted(bidId = bid).name)
        assertEquals("ad.error", AdEvent.Error(message = "m", errCode = "e").name)
        assertEquals("video.started", AdEvent.VideoStarted(bidId = bid).name)
        assertEquals("video.completed", AdEvent.VideoCompleted(bidId = bid).name)
        assertEquals("reward.granted", AdEvent.RewardGranted(bidId = bid).name)
    }
}

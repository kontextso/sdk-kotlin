package so.kontext.ads.internal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.kontext.ads.internal.ui.model.IFrameEvent

/**
 * Runs under Robolectric so `org.json.JSONObject` (Android-only) is real.
 */
@RunWith(RobolectricTestRunner::class)
class IFrameEventParserTest {

    private val parser = IFrameEventParser()

    @Test
    fun `init-iframe parses to InitIframe`() {
        assertTrue(parser.parse("""{"type": "init-iframe"}""") is IFrameEvent.InitIframe)
    }

    @Test
    fun `show-iframe parses to ShowIframe`() {
        assertTrue(parser.parse("""{"type": "show-iframe"}""") is IFrameEvent.ShowIframe)
    }

    @Test
    fun `hide-iframe parses to HideIframe`() {
        assertTrue(parser.parse("""{"type": "hide-iframe"}""") is IFrameEvent.HideIframe)
    }

    @Test
    fun `resize-iframe parses height as float`() {
        val event = parser.parse("""{"type": "resize-iframe", "data": {"height": 240.5}}""")
        assertTrue(event is IFrameEvent.Resize)
        assertEquals(240.5f, (event as IFrameEvent.Resize).height, 0.01f)
    }

    @Test
    fun `resize-iframe without data falls back to Unknown`() {
        val event = parser.parse("""{"type": "resize-iframe"}""")
        assertTrue(event is IFrameEvent.Unknown)
    }

    @Test
    fun `view-iframe populates id content messageId`() {
        val event = parser.parse(
            """{"type": "view-iframe", "data": {"id": "b-1", "content": "ad", "messageId": "m-1"}}""",
        )
        assertTrue(event is IFrameEvent.View)
        event as IFrameEvent.View
        assertEquals("b-1", event.id)
        assertEquals("ad", event.content)
        assertEquals("m-1", event.messageId)
    }

    @Test
    fun `click-iframe populates id content messageId url`() {
        val event = parser.parse(
            """{"type": "click-iframe", "data": {"id": "b-1", "content": "ad", "messageId": "m-1", "url": "https://x"}}""",
        )
        assertTrue(event is IFrameEvent.Click)
        assertEquals("https://x", (event as IFrameEvent.Click).url)
    }

    @Test
    fun `ad-done-iframe parses fields`() {
        val event = parser.parse(
            """{"type": "ad-done-iframe", "data": {"id": "b", "content": "c", "messageId": "m"}}""",
        )
        assertTrue(event is IFrameEvent.AdDone)
    }

    @Test
    fun `ad-done-component-iframe parses to AdDoneComponent singleton`() {
        assertTrue(parser.parse("""{"type": "ad-done-component-iframe"}""") is IFrameEvent.AdDoneComponent)
    }

    @Test
    fun `error-iframe parses the top-level message`() {
        val event = parser.parse("""{"type": "error-iframe", "message": "boom"}""")
        assertTrue(event is IFrameEvent.Error)
        assertEquals("boom", (event as IFrameEvent.Error).message)
    }

    @Test
    fun `error-iframe without message uses default Unknown error string`() {
        val event = parser.parse("""{"type": "error-iframe"}""")
        assertEquals("Unknown error", (event as IFrameEvent.Error).message)
    }

    @Test
    fun `open-component-iframe for modal parses code + timeout`() {
        val event = parser.parse(
            """{"type": "open-component-iframe", "data": {"code": "inlineAd", "component": "modal", "timeout": 3000}}""",
        )
        assertTrue(event is IFrameEvent.OpenComponent)
        event as IFrameEvent.OpenComponent
        assertEquals("inlineAd", event.code)
        assertEquals("modal", event.component)
        assertEquals(3000, event.timeout)
    }

    @Test
    fun `open-component-iframe for non-modal component falls back to Unknown`() {
        val event = parser.parse(
            """{"type": "open-component-iframe", "data": {"code": "c", "component": "popup"}}""",
        )
        assertTrue(event is IFrameEvent.Unknown)
    }

    @Test
    fun `init-component close-component error-component parse matching modal events`() {
        assertTrue(
            parser.parse(
                """{"type": "init-component-iframe", "data": {"code": "c", "component": "modal"}}""",
            ) is IFrameEvent.InitComponent,
        )
        assertTrue(
            parser.parse(
                """{"type": "close-component-iframe", "data": {"code": "c", "component": "modal"}}""",
            ) is IFrameEvent.CloseComponent,
        )
        assertTrue(
            parser.parse(
                """{"type": "error-component-iframe", "data": {"code": "c", "component": "modal"}}""",
            ) is IFrameEvent.ErrorComponent,
        )
    }

    @Test
    fun `event-iframe ad_viewed parses CallbackEvent_Viewed`() {
        val event = parser.parse(
            """
            {
              "type": "event-iframe",
              "data": {
                "name": "ad.viewed",
                "code": "inlineAd",
                "payload": {"id": "b", "content": "c", "messageId": "m", "format": "inline"}
              }
            }
            """.trimIndent(),
        )
        assertTrue(event is IFrameEvent.CallbackEvent.Viewed)
        val viewed = event as IFrameEvent.CallbackEvent.Viewed
        assertEquals("b", viewed.bidId)
        assertEquals("inline", viewed.format)
    }

    @Test
    fun `event-iframe ad_clicked parses CallbackEvent_Clicked with url + format + area`() {
        val event = parser.parse(
            """
            {
              "type": "event-iframe",
              "data": {
                "name": "ad.clicked",
                "code": "inlineAd",
                "payload": {
                  "id": "b", "content": "c", "messageId": "m",
                  "url": "https://x", "format": "inline", "area": "cta"
                }
              }
            }
            """.trimIndent(),
        )
        assertTrue(event is IFrameEvent.CallbackEvent.Clicked)
        val clicked = event as IFrameEvent.CallbackEvent.Clicked
        assertEquals("https://x", clicked.url)
        assertEquals("cta", clicked.area)
    }

    @Test
    fun `event-iframe render-started and render-completed parse`() {
        val started = parser.parse(
            """{"type": "event-iframe", "data": {"name": "ad.render-started", "code": "c", "payload": {"id": "b"}}}""",
        )
        val completed = parser.parse(
            """{"type": "event-iframe", "data": {"name": "ad.render-completed", "code": "c", "payload": {"id": "b"}}}""",
        )
        assertTrue(started is IFrameEvent.CallbackEvent.RenderStarted)
        assertTrue(completed is IFrameEvent.CallbackEvent.RenderCompleted)
    }

    @Test
    fun `event-iframe ad_error parses CallbackEvent_Error with message + errCode`() {
        val event = parser.parse(
            """
            {"type": "event-iframe", "data": {"name": "ad.error", "code": "c", "payload": {"message": "boom", "errCode": "E42"}}}
            """.trimIndent(),
        )
        assertTrue(event is IFrameEvent.CallbackEvent.Error)
        val err = event as IFrameEvent.CallbackEvent.Error
        assertEquals("boom", err.message)
        assertEquals("E42", err.errCode)
    }

    @Test
    fun `event-iframe reward_granted video_started video_completed parse`() {
        val reward = parser.parse(
            """{"type": "event-iframe", "data": {"name": "reward.granted", "code": "c", "payload": {"id": "b"}}}""",
        )
        val started = parser.parse(
            """{"type": "event-iframe", "data": {"name": "video.started", "code": "c", "payload": {"id": "b"}}}""",
        )
        val completed = parser.parse(
            """{"type": "event-iframe", "data": {"name": "video.completed", "code": "c", "payload": {"id": "b"}}}""",
        )
        assertTrue(reward is IFrameEvent.CallbackEvent.RewardGranted)
        assertTrue(started is IFrameEvent.CallbackEvent.VideoStarted)
        assertTrue(completed is IFrameEvent.CallbackEvent.VideoCompleted)
    }

    @Test
    fun `event-iframe with unknown name falls back to Generic with payload map`() {
        val event = parser.parse(
            """
            {"type": "event-iframe", "data": {"name": "custom.event", "code": "c", "payload": {"foo": "bar", "n": 1}}}
            """.trimIndent(),
        )
        assertTrue(event is IFrameEvent.CallbackEvent.Generic)
        val generic = event as IFrameEvent.CallbackEvent.Generic
        assertEquals("c", generic.code)
        assertEquals("bar", generic.payload["foo"])
    }

    @Test
    fun `unknown type falls back to Unknown`() {
        val event = parser.parse("""{"type": "something-new"}""")
        assertTrue(event is IFrameEvent.Unknown)
        assertEquals("something-new", (event as IFrameEvent.Unknown).type)
    }

    @Test
    fun `malformed JSON falls back to Unknown with parse-error type`() {
        val event = parser.parse("not-json")
        assertTrue(event is IFrameEvent.Unknown)
        assertEquals("parse-error", (event as IFrameEvent.Unknown).type)
    }
}

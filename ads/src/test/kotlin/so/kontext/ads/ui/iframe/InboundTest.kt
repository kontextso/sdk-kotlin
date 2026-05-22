package so.kontext.ads.ui.iframe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `IframeEvent.parse` is the typed boundary between the iframe wire
 * protocol (JSON strings) and the kit's internal event vocabulary.
 * Mirrors iOS `IframeEvent` decoding semantics: defensive `as?` casts,
 * unknown / malformed messages return `null` (caller drops the event
 * silently rather than crashing).
 */
class InboundTest {

    // Envelope -----------------------------------------------------------------

    @Test
    fun `parse returns null for invalid JSON`() {
        assertNull(IframeEvent.parse("not json at all"))
        assertNull(IframeEvent.parse(""))
    }

    @Test
    fun `parse returns null when type field is missing`() {
        assertNull(IframeEvent.parse("""{"data":{}}"""))
    }

    @Test
    fun `parse returns null when type is empty string`() {
        assertNull(IframeEvent.parse("""{"type":"","data":{}}"""))
    }

    @Test
    fun `parse returns null for unknown type`() {
        assertNull(IframeEvent.parse("""{"type":"set-styles-iframe","data":{}}"""))
    }

    // No-payload events --------------------------------------------------------

    @Test
    fun `init-iframe parses to Init`() {
        assertEquals(IframeEvent.Init, IframeEvent.parse("""{"type":"init-iframe"}"""))
    }

    @Test
    fun `show-iframe and hide-iframe parse to Show and Hide`() {
        assertEquals(IframeEvent.Show, IframeEvent.parse("""{"type":"show-iframe"}"""))
        assertEquals(IframeEvent.Hide, IframeEvent.parse("""{"type":"hide-iframe"}"""))
    }

    @Test
    fun `error-iframe parses to Error (no payload — message hardcoded by consumer)`() {
        // Per iOS protocol, error-iframe carries no fields; the
        // consumer (Ad.handleIframeEvent) supplies the fixed error
        // strings on emit.
        assertEquals(IframeEvent.Error, IframeEvent.parse("""{"type":"error-iframe","data":{}}"""))
    }

    @Test
    fun `init-component-iframe parses to InitComponent`() {
        assertEquals(IframeEvent.InitComponent, IframeEvent.parse("""{"type":"init-component-iframe"}"""))
    }

    // Resize -------------------------------------------------------------------

    @Test
    fun `resize-iframe parses height as Float`() {
        val event = IframeEvent.parse("""{"type":"resize-iframe","data":{"height":250.5}}""")
        assertTrue(event is IframeEvent.Resize)
        assertEquals(250.5f, (event as IframeEvent.Resize).height)
    }

    @Test
    fun `resize-iframe accepts integer height (server may send Int)`() {
        // Wire format may serialise integer heights without decimals.
        val event = IframeEvent.parse("""{"type":"resize-iframe","data":{"height":300}}""")
        assertTrue(event is IframeEvent.Resize)
        assertEquals(300f, (event as IframeEvent.Resize).height)
    }

    @Test
    fun `resize-iframe returns null when height is missing or wrong type`() {
        // Defensive: drop malformed events instead of synthesising 0.
        assertNull(IframeEvent.parse("""{"type":"resize-iframe","data":{}}"""))
        assertNull(IframeEvent.parse("""{"type":"resize-iframe","data":{"height":"oops"}}"""))
    }

    // Event --------------------------------------------------------------------

    @Test
    fun `event-iframe parses name and optional payload`() {
        val event = IframeEvent.parse(
            """{"type":"event-iframe","data":{"name":"ad.viewed","payload":{"id":"bid1","format":"display"}}}""",
        )
        assertTrue(event is IframeEvent.Event)
        val ev = event as IframeEvent.Event
        assertEquals("ad.viewed", ev.name)
        assertEquals("bid1", ev.payload?.get("id"))
        assertEquals("display", ev.payload?.get("format"))
    }

    @Test
    fun `event-iframe with no payload yields null payload`() {
        val event = IframeEvent.parse("""{"type":"event-iframe","data":{"name":"video.started"}}""")
        assertTrue(event is IframeEvent.Event)
        assertEquals("video.started", (event as IframeEvent.Event).name)
        assertNull(event.payload)
    }

    @Test
    fun `event-iframe returns null when name is missing`() {
        assertNull(IframeEvent.parse("""{"type":"event-iframe","data":{}}"""))
    }

    // Click --------------------------------------------------------------------

    @Test
    fun `click-iframe parses all fields including target`() {
        val event = IframeEvent.parse(
            """{"type":"click-iframe","data":{
                "id":"bid1","content":"hello","messageId":"m1",
                "url":"https://x.com/foo","target":"in-app",
                "fallbackUrl":"https://fallback.com","appStoreId":"123"
            }}""",
        )
        assertTrue(event is IframeEvent.Click)
        val click = event as IframeEvent.Click
        assertEquals("bid1", click.id)
        assertEquals("https://x.com/foo", click.url)
        assertEquals(IframeEvent.Target.IN_APP, click.target)
        assertEquals("https://fallback.com", click.fallbackUrl)
        assertEquals("123", click.appStoreId)
    }

    @Test
    fun `click-iframe target defaults to BROWSER for missing target`() {
        // Documented protocol default per sdk-js. Missing or unknown
        // target value should land on system browser, not unknown state.
        val event = IframeEvent.parse(
            """{"type":"click-iframe","data":{"url":"https://example.com"}}""",
        )
        assertTrue(event is IframeEvent.Click)
        assertEquals(IframeEvent.Target.BROWSER, (event as IframeEvent.Click).target)
    }

    @Test
    fun `click-iframe target falls back to BROWSER for unknown wire value`() {
        val event = IframeEvent.parse(
            """{"type":"click-iframe","data":{"url":"https://example.com","target":"deeplink"}}""",
        )
        assertTrue(event is IframeEvent.Click)
        assertEquals(IframeEvent.Target.BROWSER, (event as IframeEvent.Click).target)
    }

    // OpenComponent ------------------------------------------------------------

    @Test
    fun `open-component-iframe parses timeout when positive`() {
        val event = IframeEvent.parse(
            """{"type":"open-component-iframe","data":{"timeout":8000,"brightnessDelta":0.3}}""",
        )
        assertTrue(event is IframeEvent.OpenComponent)
        val open = event as IframeEvent.OpenComponent
        assertEquals(8000, open.timeout)
        assertEquals(0.3, open.brightnessDelta)
    }

    @Test
    fun `open-component-iframe defaults timeout to 5000 when missing`() {
        // Fallback matches Constants.DEFAULT_MODAL_TIMEOUT_MS — also 5s
        // on iOS.
        val event = IframeEvent.parse("""{"type":"open-component-iframe","data":{}}""")
        assertTrue(event is IframeEvent.OpenComponent)
        assertEquals(5000, (event as IframeEvent.OpenComponent).timeout)
    }

    @Test
    fun `open-component-iframe rejects non-positive timeout (clamps to default)`() {
        // A buggy server sending timeout=0 or timeout=-100 shouldn't
        // open a modal that auto-dismisses immediately.
        val zero = IframeEvent.parse("""{"type":"open-component-iframe","data":{"timeout":0}}""")
        assertEquals(5000, (zero as IframeEvent.OpenComponent).timeout)
        val negative = IframeEvent.parse("""{"type":"open-component-iframe","data":{"timeout":-100}}""")
        assertEquals(5000, (negative as IframeEvent.OpenComponent).timeout)
    }

    @Test
    fun `open-component-iframe componentParams pass through as Map`() {
        val event = IframeEvent.parse(
            """{"type":"open-component-iframe","data":{"componentParams":{"theme":"dark","video":"intro"}}}""",
        )
        val open = event as IframeEvent.OpenComponent
        val params = checkNotNull(open.componentParams)
        assertEquals("dark", params["theme"])
        assertEquals("intro", params["video"])
    }

    // ErrorComponent -----------------------------------------------------------

    @Test
    fun `error-component-iframe parses message and errorType`() {
        val event = IframeEvent.parse(
            """{"type":"error-component-iframe","data":{"message":"crashed","errorType":"render_error"}}""",
        )
        assertTrue(event is IframeEvent.ErrorComponent)
        val err = event as IframeEvent.ErrorComponent
        assertEquals("crashed", err.message)
        assertEquals("render_error", err.errorType)
    }

    @Test
    fun `error-component-iframe with missing fields yields null fields`() {
        // Both fields nullable — Ad.handleErrorComponent applies defaults
        // when absent. Defensive parsing per iOS contract.
        val event = IframeEvent.parse("""{"type":"error-component-iframe","data":{}}""")
        assertTrue(event is IframeEvent.ErrorComponent)
        val err = event as IframeEvent.ErrorComponent
        assertNull(err.message)
        assertNull(err.errorType)
    }

    // CloseComponent / AdDoneComponent ----------------------------------------

    @Test
    fun `close-component-iframe and ad-done-component-iframe carry through data`() {
        val close = IframeEvent.parse("""{"type":"close-component-iframe","data":{"foo":"bar"}}""")
        assertTrue(close is IframeEvent.CloseComponent)
        assertEquals("bar", (close as IframeEvent.CloseComponent).data["foo"])

        val done = IframeEvent.parse("""{"type":"ad-done-component-iframe","data":{}}""")
        assertTrue(done is IframeEvent.AdDoneComponent)
    }
}

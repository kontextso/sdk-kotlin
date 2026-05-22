package so.kontext.ads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.AdEventHandler
import so.kontext.ads.model.Bid
import so.kontext.ads.model.SessionOptions
import so.kontext.ads.network.HttpClient
import so.kontext.ads.ui.iframe.IframeEvent
import java.util.UUID

class AdTest {

    // `Session.scopeOnMain` uses `Dispatchers.Main.immediate` for OMID
    // session bookkeeping. Pure-JVM tests don't have a Main dispatcher
    // installed; install one for each test so `schedulePendingDestroy()`
    // and other Main-bound launches don't throw. UnconfinedTestDispatcher
    // runs launches synchronously which keeps the existing assertion
    // pattern of "act, then check state" working.
    @BeforeEach
    fun installMainDispatcher() {
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        Dispatchers.resetMain()
    }

    private fun makeSession(onEvent: AdEventHandler? = null): Session {
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "tok",
                userId = "user",
                conversationId = "conv",
                onEvent = onEvent,
            ),
            installId = TEST_INSTALL_ID,
        )
        return Session(context = null, config = config, httpClient = NoOpHttpClient)
    }

    // ---------------------------------------------------------------------------
    // Initial state
    // ---------------------------------------------------------------------------

    @Test
    fun `Ad has correct initial state`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        assertEquals("a1", ad.messageId)
        assertEquals("inlineAd", ad.code)
        assertNull(ad.theme)
        assertNull(ad.iframeUrl)
        assertEquals(0f, ad.height)
        assertFalse(ad.isVisible)
        assertFalse(ad.destroyed)

        session.destroy()
    }

    @Test
    fun `Ad with theme stores theme`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = "dark", session = session)

        assertEquals("dark", ad.theme)
        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Destroy lifecycle
    // ---------------------------------------------------------------------------

    @Test
    fun `destroy sets destroyed flag`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.destroy()
        assertTrue(ad.destroyed)

        session.destroy()
    }

    @Test
    fun `destroy is idempotent`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.destroy()
        ad.destroy() // should not throw

        assertTrue(ad.destroyed)
        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Iframe event handling
    // ---------------------------------------------------------------------------

    @Test
    fun `resize-iframe updates height`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.handleIframeEvent(IframeEvent.Resize(height = 250.0f))
        assertEquals(250f, ad.height)

        session.destroy()
    }

    @Test
    fun `show-iframe and hide-iframe update visibility`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.handleIframeEvent(IframeEvent.Show)
        assertTrue(ad.isVisible)

        ad.handleIframeEvent(IframeEvent.Hide)
        assertFalse(ad.isVisible)
        assertEquals(0f, ad.height)

        session.destroy()
    }

    @Test
    fun `events ignored after destroy`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.destroy()
        ad.handleIframeEvent(IframeEvent.Show)
        assertFalse(ad.isVisible)

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Event emission
    // ---------------------------------------------------------------------------

    @Test
    fun `init-iframe emits RenderStarted`() {
        val events = mutableListOf<AdEvent>()
        val session = makeSession(onEvent = { events.add(it) })
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        ad.bid = makeBid()

        ad.handleIframeEvent(IframeEvent.Init)
        assertTrue(events.any { it is AdEvent.RenderStarted })
        assertEquals(BID_UUID, (events.first { it is AdEvent.RenderStarted } as AdEvent.RenderStarted).bidId)

        session.destroy()
    }

    @Test
    fun `init-iframe without resolved bid does not emit RenderStarted`() {
        // Production invariant: the iframe can't initialise before a bid
        // is resolved (no bid → no iframe URL). Pin that no event fires
        // when the precondition is violated, rather than emitting with a
        // placeholder bidId.
        val events = mutableListOf<AdEvent>()
        val session = makeSession(onEvent = { events.add(it) })
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.handleIframeEvent(IframeEvent.Init)
        assertFalse(events.any { it is AdEvent.RenderStarted })

        session.destroy()
    }

    @Test
    fun `ad-done-iframe emits RenderCompleted`() {
        val events = mutableListOf<AdEvent>()
        val session = makeSession(onEvent = { events.add(it) })
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        ad.bid = makeBid()

        ad.handleIframeEvent(IframeEvent.AdDone(id = null, content = null, messageId = null))
        assertTrue(events.any { it is AdEvent.RenderCompleted })

        session.destroy()
    }

    @Test
    fun `error-iframe emits Error event`() {
        // Per iOS protocol, error-iframe has no payload — message + code default
        // to fixed strings on emit. (Pre-typed-IframeEvent code parsed
        // data["message"] / data["code"] from the wire, but those fields aren't
        // part of the documented iframe protocol; iOS doesn't read them either.)
        val events = mutableListOf<AdEvent>()
        val session = makeSession(onEvent = { events.add(it) })
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.handleIframeEvent(IframeEvent.Error)

        val error = events.filterIsInstance<AdEvent.Error>().firstOrNull()
        assertNotNull(error)
        assertEquals("iframe error", error!!.message)
        assertEquals("iframe_error", error.errCode)

        session.destroy()
    }

    @Test
    fun `event-iframe ad viewed enriches with revenue and surfaces typed fields`() {
        val events = mutableListOf<AdEvent>()
        val session = makeSession(onEvent = { events.add(it) })
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        ad.bid = makeBid(revenue = 2.5)

        ad.handleIframeEvent(
            IframeEvent.Event(
                name = "ad.viewed",
                payload = mapOf(
                    "messageId" to "a1",
                    "content" to "ad-content",
                    "format" to "display",
                ),
            ),
        )

        val viewed = events.filterIsInstance<AdEvent.Viewed>().firstOrNull()
        assertNotNull(viewed)
        assertEquals(BID_UUID, viewed!!.bidId)
        assertEquals("a1", viewed.messageId)
        assertEquals("ad-content", viewed.content)
        assertEquals("display", viewed.format)
        // Revenue is enriched from the assigned bid, not the iframe payload.
        assertEquals(2.5, viewed.revenue)

        session.destroy()
    }

    @Test
    fun `event-iframe ad clicked surfaces typed fields`() {
        val events = mutableListOf<AdEvent>()
        val session = makeSession(onEvent = { events.add(it) })
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        ad.bid = makeBid()

        ad.handleIframeEvent(
            IframeEvent.Event(
                name = "ad.clicked",
                payload = mapOf(
                    "messageId" to "a1",
                    "content" to "click-content",
                    "url" to "https://example.com/click",
                    "format" to "display",
                    "area" to "cta",
                ),
            ),
        )

        val clicked = events.filterIsInstance<AdEvent.Clicked>().firstOrNull()
        assertNotNull(clicked)
        assertEquals(BID_UUID, clicked!!.bidId)
        assertEquals("a1", clicked.messageId)
        assertEquals("click-content", clicked.content)
        assertEquals("https://example.com/click", clicked.url)
        assertEquals("display", clicked.format)
        assertEquals("cta", clicked.area)

        session.destroy()
    }

    @Test
    fun `resize-iframe emits AdHeight event`() {
        val events = mutableListOf<AdEvent>()
        val session = makeSession(onEvent = { events.add(it) })
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        ad.bid = makeBid()

        ad.handleIframeEvent(IframeEvent.Resize(height = 300.0f))

        val heightEvent = events.filterIsInstance<AdEvent.AdHeight>().firstOrNull()
        assertNotNull(heightEvent)
        assertEquals(BID_UUID, heightEvent!!.bidId)
        assertEquals("a1", heightEvent.messageId)
        assertEquals(300f, heightEvent.height)

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Click handling
    // ---------------------------------------------------------------------------

    @Test
    fun `click-iframe does not crash with url`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        // Context is null in tests — handleClick returns early
        ad.handleIframeEvent(IframeEvent.Click(id = null, content = null, messageId = null, url = "https://example.com", target = IframeEvent.Target.BROWSER, fallbackUrl = null, appStoreId = null))
        session.destroy()
    }

    @Test
    fun `click-iframe does not crash with fallbackUrl`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        ad.handleIframeEvent(
            IframeEvent.Click(
                id = null,
                content = null,
                messageId = null,
                url = "amazon://dp/B123",
                target = IframeEvent.Target.BROWSER,
                fallbackUrl = "https://amazon.com/dp/B123",
                appStoreId = null,
            ),
        )
        session.destroy()
    }

    @Test
    fun `click-iframe does not crash with no url`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)
        ad.handleIframeEvent(
            IframeEvent.Click(
                id = null,
                content = null,
                messageId = null,
                url = null,
                target = IframeEvent.Target.BROWSER,
                fallbackUrl = null,
                appStoreId = null,
            ),
        )
        session.destroy()
    }

    @Test
    fun `bidId stringifies to lowercase canonical form for URL paths`() {
        // checkBid() builds the iframe URL via `${matchedBid.bidId}` and
        // OM modal URL via `/api/modal/${currentBid.bidId}` — both rely
        // on Java's UUID.toString() being lowercase per RFC 4122. Pin
        // the invariant here so a future refactor wrapping bidId in some
        // custom toString would surface as a broken test rather than
        // silently shipping mixed-case URLs (which sdk-js and sdk-swift
        // would NOT match — they explicitly lowercase their wire form).
        val canonical = UUID.fromString("11111111-1111-1111-1111-111111111111")
        assertEquals("11111111-1111-1111-1111-111111111111", canonical.toString())

        // fromString accepts mixed case (RFC 4122), but toString MUST
        // round-trip to lowercase.
        val mixed = UUID.fromString("AAAABBBB-CCCC-DDDD-EEEE-FFFFFFFFFFFF")
        assertEquals("aaaabbbb-cccc-dddd-eeee-ffffffffffff", mixed.toString())
    }

    private fun makeBid(revenue: Double? = null): Bid = Bid(
        bidId = BID_UUID,
        code = "inlineAd",
        revenue = revenue,
    )

// ---------------------------------------------------------------------------
    // Pending destroy — LazyColumn recycle grace window
    //
    // When the InlineAd composable leaves composition, we don't destroy the
    // Ad immediately: LazyColumn recycling rapidly disposes + re-mounts the
    // same cell. schedulePendingDestroy delays the destroy by 500 ms; if a
    // remount happens first, cancelPendingDestroy aborts the destroy.
    // ---------------------------------------------------------------------------

    @Test
    fun `schedulePendingDestroy is safe to call on a fresh Ad`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        // Schedules a launch on session.scopeOnMain — must not throw,
        // must not affect the ad's destroyed flag yet.
        ad.schedulePendingDestroy()
        assertFalse(ad.destroyed)

        // cancelPendingDestroy aborts the launch before its 500 ms delay
        // elapses. Subsequent ads should still be functional.
        ad.cancelPendingDestroy()
        assertFalse(ad.destroyed)

        session.destroy()
    }

    @Test
    fun `schedulePendingDestroy no-ops on an already-destroyed Ad`() {
        // Destroy first, then schedule — must not re-trigger any teardown
        // logic on an Ad that's already been cleaned up.
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        ad.destroy()
        assertTrue(ad.destroyed)

        ad.schedulePendingDestroy()
        assertTrue(ad.destroyed) // unchanged

        session.destroy()
    }

    @Test
    fun `cancelPendingDestroy on a fresh Ad is a no-op`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        // Calling cancel without a prior schedule must be safe — pending
        // job is null, cancel + null-assign is idempotent.
        ad.cancelPendingDestroy()
        ad.cancelPendingDestroy() // repeat — still safe

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Modal callbacks — onRequestModal / onDismissModal fire when the iframe
    // emits open-component / close-component events. The bridge layer (RN /
    // Flutter) hangs UI handlers off these.
    // ---------------------------------------------------------------------------

    @Test
    fun `onDismissModal fires on close-component-iframe event`() {
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        var dismissed = 0
        ad.onDismissModal = { dismissed++ }

        ad.handleIframeEvent(IframeEvent.CloseComponent(data = emptyMap()))
        assertEquals(1, dismissed, "onDismissModal must fire on close-component")

        session.destroy()
    }

    @Test
    fun `onDismissModal fires on error-component-iframe event`() {
        // Modal errored mid-render → host dismisses + reports the error.
        val session = makeSession()
        val ad = Ad(messageId = "a1", code = "inlineAd", theme = null, session = session)

        var dismissed = 0
        ad.onDismissModal = { dismissed++ }

        ad.handleIframeEvent(
            IframeEvent.ErrorComponent(message = "boom", errorType = "render"),
        )
        assertEquals(1, dismissed)

        session.destroy()
    }

    companion object {
        private val BID_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

        private val NoOpHttpClient = HttpClient { _, _, _, _ ->
            throw IllegalStateException("No-op")
        }
    }
}

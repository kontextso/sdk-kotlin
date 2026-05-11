package so.kontext.ads

import so.kontext.ads.model.AddMessageOptions
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.AdEventHandler
import so.kontext.ads.model.AdOptions
import so.kontext.ads.model.Character
import so.kontext.ads.model.Message
import so.kontext.ads.model.MutablePublisherOptions
import so.kontext.ads.model.Regulatory
import so.kontext.ads.model.Role
import so.kontext.ads.model.SessionOptions

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import so.kontext.ads.network.HttpClient
import so.kontext.ads.network.HttpResponse

class SessionTest {

    private fun makeConfig(onEvent: AdEventHandler? = null) = resolveConfig(
        SessionOptions(
            publisherToken = "test-token",
            userId = "test-user",
            conversationId = "test-conv",
            onEvent = onEvent,
        ),
    )

    /** Creates a test Session with no Android context (pure JVM). */
    private fun makeSession(
        httpClient: HttpClient = NoOpHttpClient,
        onEvent: AdEventHandler? = null,
    ): Session {
        return Session(
            context = null, // null context = skip GAID/OM/init
            config = makeConfig(onEvent),
            httpClient = httpClient,
        )
    }

    // ---------------------------------------------------------------------------
    // Message accumulation
    // ---------------------------------------------------------------------------

    @Test
    fun `addMessage accumulates messages`() = runTest {
        val session = makeSession()

        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "Hi"))

        assertEquals(2, session.messages.size)
        assertEquals("u1", session.messages[0].id)
        assertEquals(Role.USER, session.messages[0].role)
        assertEquals("a1", session.messages[1].id)
        assertEquals(Role.ASSISTANT, session.messages[1].role)

        session.destroy()
    }

    @Test
    fun `addMessage caps at MAX_MESSAGES`() = runTest {
        val session = makeSession()

        for (i in 1..40) {
            session.addMessage(Message(id = "m$i", role = Role.USER, content = "msg $i"))
        }

        assertEquals(Constants.MAX_MESSAGES, session.messages.size)
        assertEquals("m11", session.messages.first().id)
        assertEquals("m40", session.messages.last().id)

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Preload triggering
    // ---------------------------------------------------------------------------

    @Test
    fun `addMessage with user role triggers preload`() = runTest {
        var preloadCalled = false

        val mockClient = HttpClient { _, _, _, _ ->
            preloadCalled = true
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd"}]}""")
        }

        val session = makeSession(httpClient = mockClient)
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))

        assertTrue(preloadCalled, "Preload should be called for user message")
        session.destroy()
    }

    @Test
    fun `addMessage with assistant role does not trigger preload`() = runTest {
        var preloadCalled = false

        val mockClient = HttpClient { _, _, _, _ ->
            preloadCalled = true
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
        }

        val session = makeSession(httpClient = mockClient)
        session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "Hi"))

        assertFalse(preloadCalled, "Preload should not be called for assistant message")
        session.destroy()
    }

    @Test
    fun `disabled session does not call preload again`() = runTest {
        // First call returns the disable response and flips session.disabled = true.
        // Second call must short-circuit before the HTTP client is invoked.
        var calls = 0
        val client = HttpClient { _, _, _, _ ->
            calls++
            HttpResponse(200, """{"errCode":"geo","error":"Geo disabled","permanent":true}""")
        }

        val session = Session(context = null, config = makeConfig(), httpClient = client)
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hi"))
        assertTrue(session.disabled)
        assertEquals(1, calls)

        session.addMessage(Message(id = "u2", role = Role.USER, content = "Again"))
        assertEquals(1, calls, "Disabled session must not issue a second preload")
        session.destroy()
    }

    // Outcome assertions live in the Events section below — addMessage no
    // longer returns a PreloadResult; publishers learn outcomes via onEvent.

    @Test
    fun `sessionId is set after successful preload`() = runTest {
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"sessionId":"55555555-5555-5555-5555-555555555555","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd"}]}""")
        }

        val session = makeSession(httpClient = mockClient)
        assertNull(session.sessionId)

        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        assertEquals(java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"), session.sessionId)

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------

    @Test
    fun `emits Filled event on successful preload`() = runTest {
        val events = mutableListOf<AdEvent>()
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.0}]}""")
        }

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) })
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))

        val filled = events.filterIsInstance<AdEvent.Filled>()
        assertEquals(1, filled.size)
        assertEquals(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"), filled[0].bidId)
        assertEquals("inlineAd", filled[0].code)
        assertEquals(2.0, filled[0].revenue)

        session.destroy()
    }

    @Test
    fun `emits one Filled event per matched code with bidId and code`() = runTest {
        // Pins the multi-code disambiguation contract: when the publisher
        // registers multiple enabledPlacementCodes and the server fills
        // both, the SDK fans out one ad.filled per matched code, each
        // payload carrying its own bidId + code.
        val events = mutableListOf<AdEvent>()
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(
                200,
                """
                {
                  "sessionId":"33333333-3333-3333-3333-333333333333",
                  "bids":[
                    {"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":0.05},
                    {"bidId":"22222222-2222-2222-2222-222222222222","code":"interstitialAd","revenue":0.10}
                  ]
                }
                """.trimIndent(),
            )
        }

        // Pass both codes through enabledPlacementCodes — Preload filters
        // bids to that list before the Filled fan-out, so a default
        // (single-code) config would drop the second bid before this test
        // could observe it.
        val config = resolveConfig(
            SessionOptions(
                publisherToken = "test-token",
                userId = "test-user",
                conversationId = "test-conv",
                enabledPlacementCodes = listOf("inlineAd", "interstitialAd"),
                onEvent = { events.add(it) },
            ),
        )
        val session = Session(context = null, config = config, httpClient = mockClient)
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))

        val filled = events.filterIsInstance<AdEvent.Filled>()
        assertEquals(2, filled.size)
        assertEquals(
            listOf(
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
            ),
            filled.map { it.bidId },
        )
        assertEquals(listOf("inlineAd", "interstitialAd"), filled.map { it.code })
        assertEquals(listOf(0.05, 0.10), filled.map { it.revenue })

        session.destroy()
    }

    @Test
    fun `emits NoFill event when skip is true`() = runTest {
        val events = mutableListOf<AdEvent>()
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[],"skip":true,"skipCode":"unfilled_bid"}""")
        }

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) })
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))

        assertTrue(events.any { it is AdEvent.NoFill })
        assertEquals("unfilled_bid", (events.first { it is AdEvent.NoFill } as AdEvent.NoFill).skipCode)

        session.destroy()
    }

    @Test
    fun `emits Error event on permanent error`() = runTest {
        val events = mutableListOf<AdEvent>()
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"errCode":"geo_disabled","error":"Geo disabled","permanent":true}""")
        }

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) })
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))

        assertTrue(events.any { it is AdEvent.Error })

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // trackOnly — preload is sent for analytics; bids are not processed
    // ---------------------------------------------------------------------------

    @Test
    fun `trackOnly sets the Kontextso-Is-Disabled header`() = runTest {
        var capturedHeaders: Map<String, String>? = null
        val mockClient = HttpClient { _, headers, _, _ ->
            capturedHeaders = headers
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
        }

        val session = makeSession(httpClient = mockClient)
        session.addMessage(
            Message(id = "u1", role = Role.USER, content = "Hello"),
            AddMessageOptions(trackOnly = true),
        )

        assertEquals("1", capturedHeaders!!["Kontextso-Is-Disabled"])
        session.destroy()
    }

    @Test
    fun `trackOnly does not emit Filled even when server returns a bid`() = runTest {
        // sdk-js + sdk-swift both early-return on trackOnly so no ad.filled
        // fires for analytics-only preloads. Pin the same behavior here.
        val events = mutableListOf<AdEvent>()
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.0}]}""")
        }

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) })
        session.addMessage(
            Message(id = "u1", role = Role.USER, content = "Hello"),
            AddMessageOptions(trackOnly = true),
        )

        assertFalse(
            events.any { it is AdEvent.Filled },
            "trackOnly preloads must not emit AdEvent.Filled",
        )
        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Ad creation
    // ---------------------------------------------------------------------------

    @Test
    fun `createAd returns Ad instance`() {
        val session = makeSession()
        val ad = session.createAd("a1")

        assertEquals("a1", ad.messageId)
        assertEquals("inlineAd", ad.code)
        assertNull(ad.iframeUrl)
        assertFalse(ad.destroyed)

        session.destroy()
    }

    @Test
    fun `createAd with options passes code and theme`() {
        val session = makeSession()
        val ad = session.createAd("a1", AdOptions(code = "sidebar", theme = "dark"))

        assertEquals("sidebar", ad.code)
        assertEquals("dark", ad.theme)

        session.destroy()
    }

    @Test
    fun `createAd destroys previous ad for same messageId`() {
        val session = makeSession()
        val ad1 = session.createAd("a1")
        val ad2 = session.createAd("a1")

        assertTrue(ad1.destroyed)
        assertFalse(ad2.destroyed)

        session.destroy()
    }

    @Test
    fun `destroy cleans up all ads`() {
        val session = makeSession()
        val ad = session.createAd("a1")

        session.destroy()
        assertTrue(ad.destroyed)
    }

    // ---------------------------------------------------------------------------
    // Preload request validation
    // ---------------------------------------------------------------------------

    @Test
    fun `preload request includes correct headers`() = runTest {
        var capturedHeaders: Map<String, String>? = null
        val mockClient = HttpClient { _, headers, _, _ ->
            capturedHeaders = headers
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
        }

        val session = makeSession(httpClient = mockClient)
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))

        assertNotNull(capturedHeaders)
        assertEquals("test-token", capturedHeaders!!["Kontextso-Publisher-Token"])
        assertEquals("0", capturedHeaders!!["Kontextso-Is-Disabled"])

        session.destroy()
    }

    @Test
    fun `preload request body contains messages`() = runTest {
        var capturedBody: String? = null
        val mockClient = HttpClient { _, _, body, _ ->
            capturedBody = body
            HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
        }

        val session = makeSession(httpClient = mockClient)
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello world"))

        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("\"publisherToken\":\"test-token\""))
        assertTrue(capturedBody!!.contains("\"role\":\"user\""))
        assertTrue(capturedBody!!.contains("Hello world"))

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // updateOptions — live-update preload-scoped config (mirrors iOS)
    // ---------------------------------------------------------------------------

    @Test
    fun `updateOptions overwrites non-null fields`() {
        val session = makeSession()
        val regulatory = Regulatory(gdpr = 0, gdprConsent = "")

        session.updateOptions(
            MutablePublisherOptions(
                variantId = "v9",
                regulatory = regulatory,
                userEmail = "new@example.com",
                advertisingId = "new-gaid",
            ),
        )

        assertEquals("v9", session.config.variantId)
        assertEquals(regulatory, session.config.regulatory)
        assertEquals("new@example.com", session.config.userEmail)
        assertEquals("new-gaid", session.config.advertisingId)

        session.destroy()
    }

    @Test
    fun `updateOptions leaves null fields unchanged`() {
        val original = Character(id = "c1", name = "Original")
        val session = Session(
            context = null,
            config = resolveConfig(
                SessionOptions(
                    publisherToken = "tok",
                    userId = "user",
                    conversationId = "conv",
                    character = original,
                    variantId = "v-original",
                ),
            ),
            httpClient = NoOpHttpClient,
        )

        // Partial update: only userEmail; everything else null → keep existing.
        // `character` is set-once at construction (no longer mutable via
        // updateOptions); the original survives any updateOptions call.
        session.updateOptions(
            MutablePublisherOptions(userEmail = "added@example.com"),
        )

        assertEquals(original, session.config.character)
        assertEquals("v-original", session.config.variantId)
        assertEquals("added@example.com", session.config.userEmail)

        session.destroy()
    }

    @Test
    fun `updateOptions cannot change character`() {
        // Pins the contract: character is set-once at session construction.
        // Switching personas requires a new Session because the message
        // history accumulated in the existing one belongs to the original
        // character.
        val original = Character(id = "c1", name = "Original")
        val session = Session(
            context = null,
            config = resolveConfig(
                SessionOptions(
                    publisherToken = "tok",
                    userId = "user",
                    conversationId = "conv",
                    character = original,
                ),
            ),
            httpClient = NoOpHttpClient,
        )

        session.updateOptions(MutablePublisherOptions(variantId = "v"))

        assertEquals(original, session.config.character)

        session.destroy()
    }

    @Test
    fun `updateOptions does not touch identity fields`() {
        val session = makeSession()
        val before = session.config

        session.updateOptions(
            MutablePublisherOptions(variantId = "v-new"),
        )

        assertEquals(before.publisherToken, session.config.publisherToken)
        assertEquals(before.userId, session.config.userId)
        assertEquals(before.conversationId, session.config.conversationId)
        assertEquals(before.adServerUrl, session.config.adServerUrl)
        assertEquals(before.enabledPlacementCodes, session.config.enabledPlacementCodes)

        session.destroy()
    }

    companion object {
        private val NoOpHttpClient = HttpClient { _, _, _, _ ->
            throw Exception("No-op client — should not be called")
        }
    }
}

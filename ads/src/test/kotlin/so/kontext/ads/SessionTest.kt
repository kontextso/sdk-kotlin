package so.kontext.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.AdEventHandler
import so.kontext.ads.model.AdOptions
import so.kontext.ads.model.AddMessageOptions
import so.kontext.ads.model.Character
import so.kontext.ads.model.Message
import so.kontext.ads.model.MutablePublisherOptions
import so.kontext.ads.model.Regulatory
import so.kontext.ads.model.Role
import so.kontext.ads.model.SessionOptions
import so.kontext.ads.network.HttpClient
import so.kontext.ads.network.HttpResponse
import so.kontext.ads.network.dto.InitResponseDto
import java.net.URI

// Cohesive suite for the whole Session surface (message accumulation,
// debounced preload, bid assignment, events, init/config). Grouping these
// together is intentional; LargeClass is a maintainability heuristic that
// doesn't add value for a single-class-under-test test file.
@Suppress("LargeClass")
class SessionTest {

    private fun makeConfig(onEvent: AdEventHandler? = null) = resolveConfig(
        SessionOptions(
            publisherToken = "test-token",
            userId = "test-user",
            conversationId = "test-conv",
            onEvent = onEvent,
        ),
        installId = TEST_INSTALL_ID,
    )

    /**
     * Creates a test Session with no Android context (pure JVM). Pass
     * `scope = testScope()` from within `runTest { ... }` so the
     * preload coroutines launch on the test scheduler — that lets
     * `testScheduler.advanceUntilIdle()` flush the preload Job and
     * makes assertions on post-preload state deterministic.
     */
    private fun makeSession(
        httpClient: HttpClient = NoOpHttpClient,
        onEvent: AdEventHandler? = null,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ): Session {
        return Session(
            context = null, // null context = skip GAID/OM/init
            config = makeConfig(onEvent),
            httpClient = httpClient,
            scope = scope,
        )
    }

    /**
     * Builds a Session-private CoroutineScope bound to this `runTest`'s
     * scheduler so the preload `Job` launched inside `addMessage` is
     * advanced by `testScheduler.advanceUntilIdle()`. `SupervisorJob()`
     * keeps the scope independent — `Session.destroy()` cancels it
     * without affecting the test scope.
     */
    private fun kotlinx.coroutines.test.TestScope.testScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

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

        val session = makeSession(httpClient = mockClient, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()

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

        val session = Session(context = null, config = makeConfig(), httpClient = client, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hi"))
        testScheduler.advanceUntilIdle()
        assertTrue(session.disabled)
        assertEquals(1, calls)

        session.addMessage(Message(id = "u2", role = Role.USER, content = "Again"))
        testScheduler.advanceUntilIdle()
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

        val session = makeSession(httpClient = mockClient, scope = testScope())
        assertNull(session.sessionId)

        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()
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

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) }, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()

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
            installId = TEST_INSTALL_ID,
        )
        val session = Session(context = null, config = config, httpClient = mockClient, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()

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

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) }, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()

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

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) }, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()

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

        val session = makeSession(httpClient = mockClient, scope = testScope())
        session.addMessage(
            Message(id = "u1", role = Role.USER, content = "Hello"),
            AddMessageOptions(trackOnly = true),
        )
        testScheduler.advanceUntilIdle()

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

        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) }, scope = testScope())
        session.addMessage(
            Message(id = "u1", role = Role.USER, content = "Hello"),
            AddMessageOptions(trackOnly = true),
        )
        testScheduler.advanceUntilIdle()

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
    fun `createAd is idempotent for the same messageId + code + theme`() {
        // Pins the LazyColumn-recycling contract: scrolling an InlineAd
        // off-screen and back must reuse the same Ad instance so the
        // WebView render survives. Repeated createAd with identical args
        // returns the same Ad rather than tearing down + rebuilding.
        val session = makeSession()
        val ad1 = session.createAd("a1")
        val ad2 = session.createAd("a1")

        assertEquals(ad1, ad2)
        assertFalse(ad1.destroyed)

        session.destroy()
    }

    @Test
    fun `createAd destroys previous ad when code or theme changes`() {
        // Publisher swap (e.g. live config change): when the placement
        // code or theme differs, the old Ad is destroyed and a fresh one
        // is created so the consumer can't accidentally hold a stale
        // reference to the previous config.
        val session = makeSession()
        val ad1 = session.createAd("a1", AdOptions(code = "inlineAd", theme = "light"))
        val ad2 = session.createAd("a1", AdOptions(code = "inlineAd", theme = "dark"))

        assertTrue(ad1.destroyed)
        assertFalse(ad2.destroyed)
        assertNotEquals(ad1, ad2)

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

        val session = makeSession(httpClient = mockClient, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()

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

        val session = makeSession(httpClient = mockClient, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello world"))
        testScheduler.advanceUntilIdle()

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
        val original = Character(id = "c1", name = "Original", avatarUrl = URI.create("https://example.com/original.png"))
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
                installId = TEST_INSTALL_ID,
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
        val original = Character(id = "c1", name = "Original", avatarUrl = URI.create("https://example.com/original.png"))
        val session = Session(
            context = null,
            config = resolveConfig(
                SessionOptions(
                    publisherToken = "tok",
                    userId = "user",
                    conversationId = "conv",
                    character = original,
                ),
                installId = TEST_INSTALL_ID,
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

    // ---------------------------------------------------------------------------
    // Preload concurrency — debounce + Job cancellation
    //
    // `addMessage` is non-suspend and launches the preload internally on
    // `session.scope`. A newer USER message cancels the in-flight Job via
    // `preloadJob.cancel()` and launches a replacement, debouncing rapid
    // bursts to a single /preload. Assistant messages don't touch preload
    // state. These tests pin both contracts.
    // ---------------------------------------------------------------------------

    @Test
    fun `assistant message during in-flight preload does not invalidate it`() = runTest {
        // Gate the HTTP response on a deferred so we can interleave an
        // assistant addMessage call between Job launch and result
        // handling. Assistant messages must not affect preload state —
        // only user messages cancel the in-flight Job.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val mockClient = HttpClient { _, _, _, _ ->
            gate.await()
            HttpResponse(
                200,
                """{"sessionId":"33333333-3333-3333-3333-333333333333",""" +
                    """"bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd"}]}""",
            )
        }
        val events = mutableListOf<AdEvent>()
        val session = makeSession(
            httpClient = mockClient,
            onEvent = { events.add(it) },
            scope = testScope(),
        )

        // Kick off the user-message preload; advance past the 250 ms
        // debounce window so Job1 enters HTTP and suspends at gate.await().
        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()

        // Assistant message arrives mid-flight. Per design (only user
        // messages launch/cancel), this is a no-op for preload state.
        session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "ack"))

        // Release the HTTP response and drain the rest of Job1.
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        val filled = events.filterIsInstance<AdEvent.Filled>()
        assertEquals(
            1,
            filled.size,
            "Assistant message must not invalidate the in-flight preload — " +
                "Filled must still fire when the HTTP result returns",
        )
        assertEquals(
            java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
            filled[0].bidId,
        )

        session.destroy()
    }

    @Test
    fun `newer user message cancels older in-flight preload via Job cancellation`() = runTest {
        // Two user messages, the second cancels the first. The new
        // design cancels the in-flight Job via `preloadJob.cancel()`
        // before launching the replacement — the first preload's HTTP
        // is interrupted at `gate.await()` and never delivers a result.
        // Only the second preload's bid emits Filled.
        val firstHttpGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var callCount = 0
        val mockClient = HttpClient { _, _, _, _ ->
            val n = ++callCount
            if (n == 1) {
                // First preload hangs until released — but it gets
                // cancelled before that ever happens.
                firstHttpGate.await()
                HttpResponse(
                    200,
                    """{"sessionId":"33333333-3333-3333-3333-333333333333",""" +
                        """"bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd"}]}""",
                )
            } else {
                HttpResponse(
                    200,
                    """{"sessionId":"44444444-4444-4444-4444-444444444444",""" +
                        """"bids":[{"bidId":"22222222-2222-2222-2222-222222222222","code":"inlineAd"}]}""",
                )
            }
        }
        val events = mutableListOf<AdEvent>()
        val session = makeSession(
            httpClient = mockClient,
            onEvent = { events.add(it) },
            scope = testScope(),
        )

        // First user message → Job1 launches, hits gate.await() in HTTP.
        session.addMessage(Message(id = "u1", role = Role.USER, content = "1"))
        testScheduler.advanceUntilIdle()

        // Second user message → cancels Job1, launches Job2 with a
        // non-gated HTTP response.
        session.addMessage(Message(id = "u2", role = Role.USER, content = "2"))
        testScheduler.advanceUntilIdle()

        val filled = events.filterIsInstance<AdEvent.Filled>()
        assertEquals(
            1,
            filled.size,
            "Only the latest preload's bid emits Filled — the first " +
                "preload's HTTP is interrupted by preloadJob.cancel()",
        )
        assertEquals(
            java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
            filled[0].bidId,
            "Filled must carry the SECOND preload's bidId, not the first's",
        )

        session.destroy()
    }

    // --- applyInitResult -----------------------------------------------------
    //
    // Extracted seam covering how /init response fields land on session
    // state. Tests hit the function directly so each field's effect is
    // exercised without spinning up an HTTP layer or hitting fireInit's
    // Context-dependent AppInfoProvider path.

    @Test
    fun `applyInitResult with enabled false disables session and emits Error event`() {
        val events = mutableListOf<AdEvent>()
        val session = Session(
            context = null,
            config = makeConfig(onEvent = { events.add(it) }),
            httpClient = NoOpHttpClient,
        )

        session.applyInitResult(InitResponseDto(enabled = false))

        assertTrue(session.disabled, "session must be disabled when /init returns enabled=false")
        val errors = events.filterIsInstance<AdEvent.Error>()
        assertEquals(1, errors.size, "exactly one Error event should be emitted")
        assertEquals("session_disabled_by_init", errors.single().errCode)
        assertEquals("Session is disabled", errors.single().message)

        session.destroy()
    }

    @Test
    fun `applyInitResult with positive preloadTimeout overrides the default`() {
        val session = Session(context = null, config = makeConfig(), httpClient = NoOpHttpClient)
        val before = session.preloadTimeoutMs
        // Confirm we're actually moving the value (so the assertion below
        // can't accidentally pass by matching the default).
        assertNotEquals(8000L, before)

        session.applyInitResult(InitResponseDto(preloadTimeout = 8000))

        assertEquals(8000L, session.preloadTimeoutMs)

        session.destroy()
    }

    @Test
    fun `applyInitResult with null preloadTimeout leaves preloadTimeoutMs unchanged`() {
        val session = Session(context = null, config = makeConfig(), httpClient = NoOpHttpClient)
        val before = session.preloadTimeoutMs

        session.applyInitResult(InitResponseDto(preloadTimeout = null))

        assertEquals(before, session.preloadTimeoutMs, "null timeout must not override the SDK default")

        session.destroy()
    }

    @Test
    fun `applyInitResult with non-positive preloadTimeout leaves preloadTimeoutMs unchanged`() {
        // Defensive: a buggy server response shipping `preloadTimeout: 0`
        // or a negative value must not zero out the preload deadline
        // (which would effectively disable preload). Mirrors sdk-swift's
        // `timeout > 0` guard.
        val session = Session(context = null, config = makeConfig(), httpClient = NoOpHttpClient)
        val before = session.preloadTimeoutMs

        session.applyInitResult(InitResponseDto(preloadTimeout = 0))
        assertEquals(before, session.preloadTimeoutMs, "timeout = 0 must not override the SDK default")

        session.applyInitResult(InitResponseDto(preloadTimeout = -100))
        assertEquals(before, session.preloadTimeoutMs, "negative timeout must not override the SDK default")

        session.destroy()
    }

    @Test
    fun `applyInitResult applies reportErrors and reportDebug toggles verbatim`() {
        val session = Session(context = null, config = makeConfig(), httpClient = NoOpHttpClient)
        // Defaults out of construction: reportErrors=true, reportDebug=false.
        assertTrue(session.reportErrors)
        assertFalse(session.reportDebug)

        session.applyInitResult(InitResponseDto(reportErrors = false, reportDebug = true))

        assertFalse(session.reportErrors, "reportErrors must take the server's false")
        assertTrue(session.reportDebug, "reportDebug must take the server's true")

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Bid assignment (updateBids) + getBid (coverage additions)
    // ---------------------------------------------------------------------------

    @Test
    fun `updateBids does not leak a bid onto a stale assistant when the last message is a user`() = runTest {
        // Regression guard for the v4 trackOnly off->on->off bug: a prior
        // assistant (a1, from a trackOnly turn) holds no bid. A new real
        // turn's preload must NOT assign its bid to that stale a1 while the
        // new user message (u2) is the last message — it must wait for the
        // new assistant (a2). The old `lastOrNull { ASSISTANT }` leaked it
        // onto a1; the fix requires the LAST message to be the assistant.
        val mockClient = HttpClient { _, headers, _, _ ->
            if (headers["Kontextso-Is-Disabled"] == "1") {
                HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
            } else {
                HttpResponse(
                    200,
                    """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.0}]}""",
                )
            }
        }
        val session = makeSession(httpClient = mockClient, scope = testScope())

        // Turn 1 (trackOnly): a1 gets no bid.
        session.addMessage(Message(id = "u1", role = Role.USER, content = "x"), AddMessageOptions(trackOnly = true))
        testScheduler.advanceUntilIdle()
        session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "ack"))
        testScheduler.advanceUntilIdle()
        assertNull(session.getBid("a1", "inlineAd"), "trackOnly turn must not assign a bid")

        // Turn 2 (real): preload responds while u2 (a USER message) is last.
        session.addMessage(Message(id = "u2", role = Role.USER, content = "y"))
        testScheduler.advanceUntilIdle()
        assertNull(session.getBid("a1", "inlineAd"), "bid must NOT leak onto the stale assistant a1")

        // The reply arrives -> the bid attaches to the new assistant a2.
        session.addMessage(Message(id = "a2", role = Role.ASSISTANT, content = "reply"))
        testScheduler.advanceUntilIdle()
        assertNotNull(session.getBid("a2", "inlineAd"), "bid attaches to the new assistant a2")
        assertNull(session.getBid("a1", "inlineAd"), "a1 still has no bid")

        session.destroy()
    }

    @Test
    fun `getBid returns the assigned bid, and null for an unknown message or mismatched code`() = runTest {
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(
                200,
                """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.0}]}""",
            )
        }
        val session = makeSession(httpClient = mockClient, scope = testScope())

        session.addMessage(Message(id = "u1", role = Role.USER, content = "x"))
        testScheduler.advanceUntilIdle()
        session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "ack"))
        testScheduler.advanceUntilIdle()

        assertNotNull(session.getBid("a1", "inlineAd"), "exact (messageId, code) match returns the bid")
        assertNull(session.getBid("a1", "sidebar"), "code mismatch returns null")
        assertNull(session.getBid("nope", "inlineAd"), "unknown messageId returns null")

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // createAd — destroyed-ad reuse guard (coverage addition)
    // ---------------------------------------------------------------------------

    @Test
    fun `createAd does not reuse a destroyed ad for the same key`() {
        val session = makeSession()
        val ad1 = session.createAd("a1")
        ad1.destroy()

        val ad2 = session.createAd("a1")

        assertTrue(ad1 !== ad2, "a destroyed ad must not be reused")
        assertFalse(ad2.destroyed, "the fresh ad must be live")

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // applyInitResult — enabled=true happy path (coverage addition)
    // ---------------------------------------------------------------------------

    @Test
    fun `applyInitResult with enabled true does not disable the session or emit an Error`() {
        val events = mutableListOf<AdEvent>()
        val session = Session(context = null, config = makeConfig { events.add(it) }, httpClient = NoOpHttpClient)

        // InitResponseDto() defaults: enabled=true, reportErrors=true, reportDebug=false.
        session.applyInitResult(InitResponseDto())

        assertTrue(events.filterIsInstance<AdEvent.Error>().isEmpty(), "enabled=true must not emit a disable Error")
        assertTrue(session.reportErrors)
        assertFalse(session.reportDebug)

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // checkBid — iframe URL construction (coverage addition)
    // ---------------------------------------------------------------------------

    @Test
    fun `createAd builds the iframe URL from the assigned bid`() = runTest {
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(
                200,
                """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.0}]}""",
            )
        }
        val session = makeSession(httpClient = mockClient, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "x"))
        testScheduler.advanceUntilIdle()
        session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "ack"))
        testScheduler.advanceUntilIdle()

        val url = session.createAd("a1").iframeUrl
        assertNotNull(url)
        assertTrue(
            url!!.endsWith("/api/frame/11111111-1111-1111-1111-111111111111?code=inlineAd&messageId=a1&sdk=sdk-kotlin"),
            "unexpected iframe URL: $url",
        )

        session.destroy()
    }

    @Test
    fun `createAd appends the theme to the iframe URL when set`() = runTest {
        val mockClient = HttpClient { _, _, _, _ ->
            HttpResponse(
                200,
                """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.0}]}""",
            )
        }
        val session = makeSession(httpClient = mockClient, scope = testScope())
        session.addMessage(Message(id = "u1", role = Role.USER, content = "x"))
        testScheduler.advanceUntilIdle()
        session.addMessage(Message(id = "a1", role = Role.ASSISTANT, content = "ack"))
        testScheduler.advanceUntilIdle()

        val url = session.createAd("a1", AdOptions(code = "inlineAd", theme = "dark")).iframeUrl
        assertNotNull(url)
        assertTrue(url!!.endsWith("&theme=dark"), "theme not appended: $url")

        session.destroy()
    }

    // ---------------------------------------------------------------------------
    // Transient failure must not block subsequent preloads (regression for the
    // v2 "sticky lastError after a timeout" report). (coverage addition)
    // ---------------------------------------------------------------------------

    @Test
    fun `a transient preload failure does not block the next preload`() = runTest {
        val events = mutableListOf<AdEvent>()
        var failNext = true
        val mockClient = HttpClient { _, _, _, _ ->
            if (failNext) throw java.io.IOException("Read timed out")
            HttpResponse(
                200,
                """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.0}]}""",
            )
        }
        val session = makeSession(httpClient = mockClient, onEvent = { events.add(it) }, scope = testScope())

        // First turn: the preload times out -> Error, but the session must NOT
        // be disabled (a transient failure is not a permanent disable).
        session.addMessage(Message(id = "u1", role = Role.USER, content = "x"))
        testScheduler.advanceUntilIdle()
        assertFalse(session.disabled, "a transient timeout must not disable the session")
        assertTrue(events.any { it is AdEvent.Error }, "the failed preload emits an Error")

        // Second turn: a healthy preload must still fire and fill — proving no
        // sticky-error state blocks it.
        failNext = false
        session.addMessage(Message(id = "u2", role = Role.USER, content = "y"))
        testScheduler.advanceUntilIdle()
        assertTrue(events.any { it is AdEvent.Filled }, "the next preload fills normally after a failure")

        session.destroy()
    }

    @Test
    fun `sessionId from a skip response is reused on the next preload`() = runTest {
        val bodies = mutableListOf<String>()
        val client = HttpClient { _, _, body, _ ->
            bodies.add(body.orEmpty())
            // Server skips (ads_disabled) but still returns a sessionId.
            HttpResponse(
                200,
                """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[],""" +
                    """"skip":true,"skipCode":"ads_disabled"}""",
            )
        }
        val session = makeSession(httpClient = client, scope = testScope())

        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hello"))
        testScheduler.advanceUntilIdle()
        session.addMessage(Message(id = "u2", role = Role.USER, content = "Again"))
        testScheduler.advanceUntilIdle()

        assertEquals(2, bodies.size)
        assertFalse(
            bodies[0].contains("33333333-3333-3333-3333-333333333333"),
            "first request can't know the sessionId yet",
        )
        assertTrue(
            bodies[1].contains("\"sessionId\":\"33333333-3333-3333-3333-333333333333\""),
            "a skip response must seed sessionId so the next preload resends it",
        )

        session.destroy()
    }

    @Test
    fun `stored sessionId survives a later response that omits one`() = runTest {
        val bodies = mutableListOf<String>()
        var call = 0
        val client = HttpClient { _, _, body, _ ->
            bodies.add(body.orEmpty())
            call += 1
            if (call == 1) {
                // Seed a sessionId.
                HttpResponse(
                    200,
                    """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[],""" +
                        """"skip":true,"skipCode":"ads_disabled"}""",
                )
            } else {
                // A later response omits sessionId — it must NOT clear the stored one.
                HttpResponse(200, """{"bids":[],"skip":true,"skipCode":"ads_disabled"}""")
            }
        }
        val session = makeSession(httpClient = client, scope = testScope())

        session.addMessage(Message(id = "u1", role = Role.USER, content = "Hi"))
        testScheduler.advanceUntilIdle()
        session.addMessage(Message(id = "u2", role = Role.USER, content = "Again"))
        testScheduler.advanceUntilIdle()
        session.addMessage(Message(id = "u3", role = Role.USER, content = "Once more"))
        testScheduler.advanceUntilIdle()

        assertEquals(3, bodies.size)
        assertTrue(
            bodies[2].contains("\"sessionId\":\"33333333-3333-3333-3333-333333333333\""),
            "a response without a sessionId must not clear the previously stored one",
        )

        session.destroy()
    }

    companion object {
        private val NoOpHttpClient = HttpClient { _, _, _, _ ->
            throw IllegalStateException("No-op client — should not be called")
        }
    }
}

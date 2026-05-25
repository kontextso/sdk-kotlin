package so.kontext.ads.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.Constants
import so.kontext.ads.SDKInfo
import so.kontext.ads.TEST_INSTALL_ID
import java.io.IOException

/**
 * Tests the `POST /debug` body shape and HTTP-send path, parallel to
 * [ErrorCaptureTest]. The actual HTTP send is wired through the same
 * injectable [HttpClient] so we can capture the four POST args.
 */
class DebugCaptureTest {

    private fun ctx(
        publisherToken: String? = "tok-1",
        conversationId: String? = "conv-1",
        userId: String? = "user-1",
        installId: String? = TEST_INSTALL_ID,
        sessionId: String? = "sess-1",
    ) = DebugContext(
        adServerUrl = "https://server.example.com",
        publisherToken = publisherToken,
        conversationId = conversationId,
        userId = userId,
        installId = installId,
        sessionId = sessionId,
    )

    private class CapturingHttpClient : HttpClient {
        var url: String? = null
        var headers: Map<String, String>? = null
        var body: String? = null
        var timeoutMs: Long? = null
        var thrown: Throwable? = null

        override suspend fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpResponse {
            this.url = url
            this.headers = headers
            this.body = body
            this.timeoutMs = timeoutMs
            thrown?.let { throw it }
            return HttpResponse(204, "")
        }
    }

    @Test
    fun `postDebugReport posts to {adServerUrl}slashdebug with Content-Type and timeout`() = runTest {
        val client = CapturingHttpClient()
        postDebugReport(
            context = ctx(),
            name = "Session: probe",
            data = """{"k":"v"}""",
            httpClient = client,
        )

        assertEquals("https://server.example.com/debug", client.url)
        assertEquals("application/json", client.headers!!["Content-Type"])
        assertEquals(Constants.ERROR_REPORT_TIMEOUT_MS, client.timeoutMs)
    }

    @Test
    fun `body has name and data at top level`() = runTest {
        val client = CapturingHttpClient()
        postDebugReport(
            context = ctx(),
            name = "Session: probe",
            data = """{"k":"v"}""",
            httpClient = client,
        )
        val body = JSONObject(client.body!!)
        assertEquals("Session: probe", body.getString("name"))
        assertEquals("""{"k":"v"}""", body.getString("data"))
    }

    @Test
    fun `data omitted when null (keeps wire payload minimal)`() = runTest {
        val client = CapturingHttpClient()
        postDebugReport(context = ctx(), name = "Session: heartbeat", data = null, httpClient = client)
        val body = JSONObject(client.body!!)
        assertFalse(body.has("data"))
    }

    @Test
    fun `additionalData carries publisherToken, conversationId, userId, sessionId`() = runTest {
        val client = CapturingHttpClient()
        postDebugReport(context = ctx(), name = "Session: x", data = null, httpClient = client)
        val additional = JSONObject(client.body!!).getJSONObject("additionalData")
        assertEquals("tok-1", additional.getString("publisherToken"))
        assertEquals("conv-1", additional.getString("conversationId"))
        assertEquals("user-1", additional.getString("userId"))
        assertEquals("sess-1", additional.getString("sessionId"))
    }

    @Test
    fun `additionalData carries installId for per-install attribution`() = runTest {
        // installId lives on additionalData (parallel to ErrorRequestDto)
        // so the server's debug-ingestion pipeline can attribute events to
        // a stable install identity. Mirrors sdk-swift `DebugRequestDTO`.
        val client = CapturingHttpClient()
        postDebugReport(context = ctx(), name = "Session: x", data = null, httpClient = client)
        val additional = JSONObject(client.body!!).getJSONObject("additionalData")
        assertEquals(TEST_INSTALL_ID, additional.getString("installId"))
    }

    @Test
    fun `additionalData_sdk has name, version, AND platform`() = runTest {
        val client = CapturingHttpClient()
        postDebugReport(context = ctx(), name = "x", data = null, httpClient = client)
        val sdk = JSONObject(client.body!!).getJSONObject("additionalData").getJSONObject("sdk")
        assertEquals(SDKInfo.NAME, sdk.getString("name"))
        assertEquals(SDKInfo.VERSION, sdk.getString("version"))
        assertEquals(SDKInfo.PLATFORM, sdk.getString("platform"))
    }

    @Test
    fun `null identifier fields are omitted from the wire`() = runTest {
        val client = CapturingHttpClient()
        postDebugReport(
            context = ctx(
                publisherToken = null,
                conversationId = null,
                userId = null,
                installId = null,
                sessionId = null,
            ),
            name = "x",
            data = null,
            httpClient = client,
        )
        val additional = JSONObject(client.body!!).getJSONObject("additionalData")
        assertFalse(additional.has("publisherToken"))
        assertFalse(additional.has("conversationId"))
        assertFalse(additional.has("userId"))
        assertFalse(additional.has("installId"))
        assertFalse(additional.has("sessionId"))
    }

    @Test
    fun `postDebugReport swallows IOException from HttpClient`() = runTest {
        val client = CapturingHttpClient().apply { thrown = IOException("connection refused") }
        postDebugReport(context = ctx(), name = "x", data = null, httpClient = client)
        assertNotNull(client.url, "delegate should have been invoked despite throwing")
    }

    @Test
    fun `postDebugReport swallows generic Exception from HttpClient`() = runTest {
        val client = CapturingHttpClient().apply { thrown = RuntimeException("unexpected") }
        postDebugReport(context = ctx(), name = "x", data = null, httpClient = client)
        assertNotNull(client.url)
    }

    // --- capture entry point (detached scope) --------------------------------
    //
    // `capture(...)` launches `postDebugReport` on a process-wide
    // `Dispatchers.IO` scope. The body-shape tests above call
    // `postDebugReport` directly; this one goes through the entry point
    // so the detached scope launch is exercised end-to-end.

    @Test
    fun `capture fires POST through detached scope with injected HttpClient`() = runBlocking {
        // Pins the entry-point wire path. `runBlocking` + real-time
        // `delay` because the launched coroutine escapes any test
        // dispatcher onto Dispatchers.IO. Parallel to ErrorCapture's
        // kill-switch tests — DebugCapture has no kill switch of its
        // own (the `reportDebug = true` gate lives one layer up in
        // Session), so we only need the positive-case test here.
        val client = CapturingHttpClient()
        DebugCapture.capture(
            name = "Session: heartbeat",
            data = mapOf("foo" to "bar"),
            context = ctx().copy(adServerUrl = "https://server.example.com"),
            httpClient = client,
        )

        var url: String? = null
        repeat(20) {
            url = client.url
            if (url != null) return@repeat
            delay(50)
        }

        assertEquals("https://server.example.com/debug", url)
        assertEquals("application/json", client.headers!!["Content-Type"])
        val parsed = JSONObject(client.body!!)
        assertEquals("Session: heartbeat", parsed.getString("name"))
    }

    @Test
    fun `body round-trips through JSONObject (well-formed JSON)`() = runTest {
        val client = CapturingHttpClient()
        postDebugReport(
            context = ctx(),
            name = "Special chars: \" \\ \n",
            data = "with \"quotes\"",
            httpClient = client,
        )
        val parsed = JSONObject(client.body!!)
        assertEquals("Special chars: \" \\ \n", parsed.getString("name"))
        assertEquals("with \"quotes\"", parsed.getString("data"))
    }

    @Test
    fun `DebugContext default null fields`() {
        // Pre-init / minimal-context callers can pass just the URL.
        val context = DebugContext(adServerUrl = "https://example.com")
        assertEquals("https://example.com", context.adServerUrl)
        assertEquals(null, context.publisherToken)
        assertEquals(null, context.conversationId)
        assertEquals(null, context.userId)
        assertEquals(null, context.sessionId)
        // sanity: sentinel call doesn't crash
        assertTrue(true)
    }

    // ---------------------------------------------------------------------------
    // stringify(data) — the only nontrivial logic in DebugCapture, reached via
    // capture(). Detached IO scope → runBlocking + poll (parallel to the
    // detached-scope test above). (coverage additions)
    // ---------------------------------------------------------------------------

    private suspend fun awaitBody(client: CapturingHttpClient): String {
        repeat(40) {
            client.body?.let { return it }
            delay(25)
        }
        error("no debug body captured")
    }

    @Test
    fun `stringify encodes a Map as a JSON object`() = runBlocking {
        val client = CapturingHttpClient()
        DebugCapture.capture(name = "x", data = mapOf("a" to 1, "b" to "two"), context = ctx(), httpClient = client)
        val parsed = JSONObject(JSONObject(awaitBody(client)).getString("data"))
        assertEquals(1, parsed.getInt("a"))
        assertEquals("two", parsed.getString("b"))
    }

    @Test
    fun `stringify encodes a Collection as a JSON array`() = runBlocking {
        val client = CapturingHttpClient()
        DebugCapture.capture(name = "x", data = listOf(1, 2, 3), context = ctx(), httpClient = client)
        assertEquals("[1,2,3]", JSONObject(awaitBody(client)).getString("data"))
    }

    @Test
    fun `stringify renders a scalar as its bare string`() = runBlocking {
        val client = CapturingHttpClient()
        DebugCapture.capture(name = "x", data = 42, context = ctx(), httpClient = client)
        assertEquals("42", JSONObject(awaitBody(client)).getString("data"))
    }
}

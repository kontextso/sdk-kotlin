package so.kontext.ads.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import so.kontext.ads.SDKInfo
import so.kontext.ads.model.SessionOptions
import so.kontext.ads.network.dto.InitRequestDto
import so.kontext.ads.resolveConfig

/**
 * `Init.fetch(...)` POSTs to `/init` to retrieve per-publisher config
 * (whether the session is enabled, an override preload timeout, etc.).
 * `HttpClient` is injectable, so tests use a fake client that captures
 * + replies — no real network. Mirrors the PreloadTest pattern.
 */
class InitTest {

    private val parser = Json { ignoreUnknownKeys = true }

    private val testApp = InitRequestDto.AppMetadata(
        bundleId = "so.kontext.test",
        version = "1.0.0",
    )

    private fun makeConfig() = resolveConfig(
        SessionOptions(
            publisherToken = "test-token",
            userId = "test-user",
            conversationId = "test-conv",
        ),
    )

    private fun makeConfigWithDebugCapture(events: MutableList<Pair<String, Any?>>) = resolveConfig(
        SessionOptions(
            publisherToken = "test-token",
            userId = "test-user",
            conversationId = "test-conv",
            onDebugEvent = { event, data -> events.add(event to data) },
        ),
    )

    // URL + headers ------------------------------------------------------------

    @Test
    fun `fetch posts to adServerUrl + slash init`() = runTest {
        var capturedUrl: String? = null
        val client = HttpClient { url, _, _, _ ->
            capturedUrl = url
            HttpResponse(200, """{"enabled":true}""")
        }

        Init.fetch(makeConfig(), client, app = testApp)
        assertEquals("https://server.megabrain.co/init", capturedUrl)
    }

    @Test
    fun `fetch sends Content-Type and Kontextso-Publisher-Token headers`() = runTest {
        var capturedHeaders: Map<String, String>? = null
        val client = HttpClient { _, headers, _, _ ->
            capturedHeaders = headers
            HttpResponse(200, """{}""")
        }

        Init.fetch(makeConfig(), client, app = testApp)
        assertEquals("application/json", capturedHeaders!!["Content-Type"])
        assertEquals("test-token", capturedHeaders!!["Kontextso-Publisher-Token"])
    }

    // Body shape ---------------------------------------------------------------

    @Test
    fun `fetch body contains publisherToken, sdk descriptor, and app metadata`() = runTest {
        var capturedBody: String? = null
        val client = HttpClient { _, _, body, _ ->
            capturedBody = body
            HttpResponse(200, """{}""")
        }

        Init.fetch(makeConfig(), client, app = testApp)
        val body = parser.parseToJsonElement(capturedBody!!).jsonObject
        assertEquals("test-token", body["publisherToken"]?.jsonPrimitive?.content)
        // userId is sent on every /init so the server can target
        // per-user toggles (reportErrors / reportDebug) in the response.
        assertEquals("test-user", body["userId"]?.jsonPrimitive?.content)

        val sdk = body["sdk"]?.jsonObject
        assertNotNull(sdk)
        assertEquals(SDKInfo.NAME, sdk!!["name"]?.jsonPrimitive?.content)
        assertEquals(SDKInfo.PLATFORM, sdk["platform"]?.jsonPrimitive?.content)
        assertEquals(SDKInfo.VERSION, sdk["version"]?.jsonPrimitive?.content)

        val app = body["app"]?.jsonObject
        assertNotNull(app)
        assertEquals("so.kontext.test", app!!["bundleId"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", app["version"]?.jsonPrimitive?.content)
    }

    // Response decoding --------------------------------------------------------

    @Test
    fun `2xx response decodes into InitResponseDto`() = runTest {
        val client = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"enabled":true,"preloadTimeout":12000}""")
        }

        val result = Init.fetch(makeConfig(), client, app = testApp)
        assertNotNull(result)
        assertEquals(true, result!!.enabled)
        assertEquals(12_000, result.preloadTimeout)
    }

    @Test
    fun `2xx response with unknown JSON keys still decodes (ignoreUnknownKeys)`() = runTest {
        // Server may add new fields ahead of SDK rollout; we must not
        // break decoding when unfamiliar keys appear.
        val client = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"enabled":true,"newFutureField":"ignored"}""")
        }

        val result = Init.fetch(makeConfig(), client, app = testApp)
        assertNotNull(result)
        assertEquals(true, result!!.enabled)
    }

    @Test
    fun `2xx response with empty body decodes with enabled=true and null preloadTimeout`() = runTest {
        // Mirrors sdk-swift's tolerant decode: a missing `enabled` key
        // collapses to the default `true` (only an explicit `false`
        // disables a session). `preloadTimeout` stays null when absent.
        val client = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{}""")
        }

        val result = Init.fetch(makeConfig(), client, app = testApp)
        assertNotNull(result)
        assertTrue(result!!.enabled)
        assertNull(result.preloadTimeout)
    }

    // Error paths --------------------------------------------------------------

    @Test
    fun `4xx response returns null (init failure is not session-fatal)`() = runTest {
        // The server returning 4xx (e.g. invalid publisher token) shouldn't
        // crash the SDK — Session continues with defaults; the next preload
        // either succeeds or surfaces the issue separately.
        val client = HttpClient { _, _, _, _ ->
            HttpResponse(400, """{"error":"invalid token"}""")
        }
        assertNull(Init.fetch(makeConfig(), client, app = testApp))
    }

    @Test
    fun `5xx response returns null`() = runTest {
        val client = HttpClient { _, _, _, _ -> HttpResponse(500, "internal error") }
        assertNull(Init.fetch(makeConfig(), client, app = testApp))
    }

    @Test
    fun `HttpClient throwing returns null (caught + reported)`() = runTest {
        // Network exception → fetch swallows + reports via ErrorCapture;
        // returns null so caller can apply defaults.
        val client = HttpClient { _, _, _, _ -> throw java.net.SocketTimeoutException("timeout") }
        assertNull(Init.fetch(makeConfig(), client, app = testApp))
    }

    @Test
    fun `2xx response with malformed JSON returns null (caught)`() = runTest {
        // Invalid JSON in a "successful" response shouldn't throw past
        // the SDK boundary. fetch catches the decode failure + reports
        // it via ErrorCapture, returns null.
        val client = HttpClient { _, _, _, _ -> HttpResponse(200, "not json") }
        assertNull(Init.fetch(makeConfig(), client, app = testApp))
    }

    // No-content / empty-body / cancellation paths -----------------------------

    @Test
    fun `204 returns null without firing ErrorCapture`() = runTest {
        // 204 No Content is the server's "publisher disabled / unknown,
        // no config to send" signal. Pre-fix this fell through to
        // `decodeFromString("")` which threw SerializationException →
        // false-positive ErrorCapture report. Now: explicit null return
        // + "Init: no-content" debug event.
        val events = mutableListOf<Pair<String, Any?>>()
        val client = HttpClient { _, _, _, _ -> HttpResponse(204, "") }

        val result = Init.fetch(makeConfigWithDebugCapture(events), client, app = testApp)

        assertNull(result)
        assertTrue(events.any { it.first == "Init: no-content" }, "missing debug event in: $events")
        assertTrue(events.none { it.first == "Init: error" }, "ErrorCapture path fired in: $events")
    }

    @Test
    fun `2xx with truly empty body returns null without firing ErrorCapture`() = runTest {
        // Tolerates empty body on any 2xx (205 Reset Content, 200 with
        // no body). Same intent as 204 with a less-specific status.
        // Decoding `""` would throw — explicit null + "Init: empty-body"
        // debug event short-circuits before that.
        val events = mutableListOf<Pair<String, Any?>>()
        val client = HttpClient { _, _, _, _ -> HttpResponse(200, "") }

        val result = Init.fetch(makeConfigWithDebugCapture(events), client, app = testApp)

        assertNull(result)
        assertTrue(events.any { it.first == "Init: empty-body" }, "missing debug event in: $events")
        assertTrue(events.none { it.first == "Init: error" }, "ErrorCapture path fired in: $events")
    }

    @Test
    fun `CancellationException rethrows without firing ErrorCapture`() = runTest {
        // Session destroyed mid-init: CancellationException must propagate
        // per Kotlin coroutine convention so structured concurrency unwinds
        // correctly. Must NOT route through the generic catch arm (which
        // would fire ErrorCapture for what is actually a normal lifecycle
        // event). Mirrors sdk-swift's `CancellationError` filter in
        // Init.swift's handleError.
        val events = mutableListOf<Pair<String, Any?>>()
        val client = HttpClient { _, _, _, _ -> throw CancellationException("session destroyed") }

        assertThrows<CancellationException> {
            Init.fetch(makeConfigWithDebugCapture(events), client, app = testApp)
        }
        assertTrue(events.any { it.first == "Init: cancelled" }, "missing debug event in: $events")
        assertTrue(events.none { it.first == "Init: error" }, "ErrorCapture path fired in: $events")
    }

    @Test
    fun `happy path emits start then response debug events with correct payloads`() = runTest {
        // Pins the diagnostic surface publishers wire to their analytics:
        // every successful /init fires "Init: start" (no payload) followed
        // by "Init: response" (carrying the parsed InitResponseDto).
        // Prevents silent regressions where someone refactors the body
        // and drops one of the events.
        val events = mutableListOf<Pair<String, Any?>>()
        val client = HttpClient { _, _, _, _ ->
            HttpResponse(200, """{"enabled":true,"preloadTimeout":12000}""")
        }

        Init.fetch(makeConfigWithDebugCapture(events), client, app = testApp)

        val keys = events.map { it.first }
        assertEquals(listOf("Init: start", "Init: response"), keys)
        // The "Init: response" payload carries the parsed DTO so publishers
        // can log the resolved config.
        val responseEvent = events.last { it.first == "Init: response" }
        val payload = responseEvent.second as Map<*, *>
        assertNotNull(payload["response"])
    }
}

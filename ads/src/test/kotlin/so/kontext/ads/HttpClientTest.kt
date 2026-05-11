@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package so.kontext.ads

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import so.kontext.ads.network.DefaultHttpClient
import so.kontext.ads.network.HttpClient
import so.kontext.ads.network.HttpResponse
import so.kontext.ads.network.RetryHttpClient
import java.io.IOException
import java.net.ServerSocket

/**
 * Drives `RetryHttpClient` against scripted lambda fakes, plus a tiny
 * embedded `ServerSocket` for `DefaultHttpClient`. Each retry-policy
 * branch (200, 4xx, 5xx, 429, network error, mixed, cancellation,
 * backoff timing) is verified in isolation.
 *
 * Mirrors sdk-swift's `Networking/HTTPRetryTests` and sdk-js's
 * `utils/__tests__/request.test.ts` — the three SDKs implement the
 * same retry policy:
 *
 * - 2xx / 3xx / 4xx (non-429) / 5xx → returned as-is, no retry
 * - 429 → retried with exponential backoff
 * - Network errors (`IOException`) → retried
 * - Cooperative cancellation honored within one backoff window
 */
class HttpClientTest {

    // ---------------------------------------------------------------------------
    // Scripted fake — pulls one outcome per call so a multi-attempt retry
    // can simulate "fail twice, then succeed".
    // ---------------------------------------------------------------------------

    /** One scripted outcome per attempt; `error != null` raises instead of returning. */
    private class StubResponse(val response: HttpResponse? = null, val error: IOException? = null) {
        companion object {
            fun ok(body: String = "") = StubResponse(response = HttpResponse(200, body))
            fun status(code: Int, body: String = "") = StubResponse(response = HttpResponse(code, body))
            fun networkError(message: String = "boom") = StubResponse(error = IOException(message))
        }
    }

    private class ScriptedClient(private val script: List<StubResponse>) : HttpClient {
        var attempts = 0
            private set

        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: String,
            timeoutMs: Long,
        ): HttpResponse {
            val index = attempts.also { attempts = it + 1 }
            val stub = script.getOrElse(index) {
                throw AssertionError("ScriptedClient: ran out of scripted responses at attempt $index")
            }
            stub.error?.let { throw it }
            return stub.response!!
        }
    }

    // ---------------------------------------------------------------------------
    // 2xx — happy path
    // ---------------------------------------------------------------------------

    @Test
    fun `returns immediately on 200`() = runTest {
        val stub = ScriptedClient(listOf(StubResponse.ok("""{"ok":true}""")))
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)

        assertEquals(200, response.statusCode)
        assertEquals("""{"ok":true}""", response.body)
        assertEquals(1, stub.attempts)
    }

    // ---------------------------------------------------------------------------
    // 4xx — returned as-is, NOT retried
    // ---------------------------------------------------------------------------

    @Test
    fun `returns 4xx as-is without retry`() = runTest {
        val stub = ScriptedClient(listOf(StubResponse.status(404, "not found")))
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)

        assertEquals(404, response.statusCode)
        assertEquals(1, stub.attempts)
    }

    // ---------------------------------------------------------------------------
    // 5xx — returned as-is, NOT retried (matches sdk-js + sdk-swift)
    // ---------------------------------------------------------------------------

    @Test
    fun `returns 5xx as-is without retry`() = runTest {
        // sdk-js parity: `request.test.ts` pins "does NOT retry on 500/503".
        // sdk-swift parity: `HTTPRetryTests.returns5xxAsIsWithoutRetry`.
        val stub = ScriptedClient(listOf(StubResponse.status(503, "unavailable")))
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)

        assertEquals(503, response.statusCode)
        assertEquals(1, stub.attempts)
    }

    // ---------------------------------------------------------------------------
    // 429 — IS retried with exponential backoff
    // ---------------------------------------------------------------------------

    @Test
    fun `retries on 429 then succeeds`() = runTest {
        val stub = ScriptedClient(
            listOf(
                StubResponse.status(429),
                StubResponse.status(429),
                StubResponse.ok("recovered"),
            ),
        )
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)

        assertEquals(200, response.statusCode)
        assertEquals("recovered", response.body)
        assertEquals(3, stub.attempts)
    }

    @Test
    fun `exhausts 429 retries then throws RateLimited`() = runTest {
        // attempt 0 + 3 retries = 4 total 429s. Matches sdk-swift's
        // HTTPRetryError.rateLimited and sdk-js's manufactured
        // Error("HTTP 429") on the same exhaustion path.
        val stub = ScriptedClient(List(4) { StubResponse.status(429, "rate limited") })
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val thrown = assertThrows<so.kontext.ads.network.HttpRetryException.RateLimited> {
            client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)
        }
        assertEquals(429, thrown.statusCode)
        assertEquals(4, stub.attempts)
    }

    // ---------------------------------------------------------------------------
    // Network errors — IS retried
    // ---------------------------------------------------------------------------

    @Test
    fun `retries on IOException then succeeds`() = runTest {
        val stub = ScriptedClient(
            listOf(
                StubResponse.networkError("connection reset"),
                StubResponse.networkError("timeout"),
                StubResponse.ok("late ok"),
            ),
        )
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)

        assertEquals(200, response.statusCode)
        assertEquals("late ok", response.body)
        assertEquals(3, stub.attempts)
    }

    @Test
    fun `exhausts network retries then throws last IOException`() = runTest {
        val stub = ScriptedClient(List(4) { StubResponse.networkError("timeout") })
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val thrown = assertThrows<IOException> {
            client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)
        }
        assertEquals("timeout", thrown.message)
        assertEquals(4, stub.attempts)
    }

    // ---------------------------------------------------------------------------
    // Mixed: network error then 5xx (which is returned as-is)
    // ---------------------------------------------------------------------------

    @Test
    fun `network error then 5xx stops retrying and returns 5xx`() = runTest {
        val stub = ScriptedClient(
            listOf(
                StubResponse.networkError("connection reset"),
                StubResponse.status(502, "bad gateway"),
            ),
        )
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 1)

        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)

        // First attempt is a network error → retried.
        // Second attempt returns 502 → not retryable, returned as-is.
        assertEquals(502, response.statusCode)
        assertEquals("bad gateway", response.body)
        assertEquals(2, stub.attempts)
    }

    // ---------------------------------------------------------------------------
    // maxRetries = 0 (single-attempt mode)
    // ---------------------------------------------------------------------------

    @Test
    fun `maxRetries zero runs once on success`() = runTest {
        val stub = ScriptedClient(listOf(StubResponse.ok("once")))
        val client = RetryHttpClient(stub, maxRetries = 0, baseDelayMs = 1)

        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)

        assertEquals("once", response.body)
        assertEquals(1, stub.attempts)
    }

    @Test
    fun `maxRetries zero runs once on retryable error`() = runTest {
        // Even though IOException is retryable, with maxRetries=0 we get
        // exactly one attempt and then propagate the error.
        val stub = ScriptedClient(listOf(StubResponse.networkError("timeout")))
        val client = RetryHttpClient(stub, maxRetries = 0, baseDelayMs = 1)

        assertThrows<IOException> {
            client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)
        }
        assertEquals(1, stub.attempts)
    }

    @Test
    fun `negative maxRetries throws on construction`() {
        // Guard the precondition — silently allowing -1 would cause the
        // for-loop to never execute and unreachable error to fire.
        assertThrows<IllegalArgumentException> {
            RetryHttpClient(ScriptedClient(emptyList()), maxRetries = -1)
        }
    }

    // ---------------------------------------------------------------------------
    // Backoff timing (uses runTest virtual clock)
    // ---------------------------------------------------------------------------

    @Test
    fun `applies exponential backoff between attempts`() = runTest {
        // 4 network errors → 3 backoffs in between (no sleep after final attempt).
        // baseDelayMs=100, factor=2 ⇒ 100 + 200 + 400 = 700ms cumulative.
        val stub = ScriptedClient(List(4) { StubResponse.networkError("timeout") })
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 100, backoffFactor = 2.0)

        val startVirtual = currentTime
        assertThrows<IOException> {
            client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)
        }
        val elapsed = currentTime - startVirtual

        assertEquals(700L, elapsed, "expected 100+200+400=700ms cumulative backoff, got $elapsed")
        assertEquals(4, stub.attempts)
    }

    @Test
    fun `success on first attempt does not sleep`() = runTest {
        // If the implementation accidentally slept on success, virtual
        // time would advance by baseDelayMs.
        val stub = ScriptedClient(listOf(StubResponse.ok("immediate")))
        val client = RetryHttpClient(stub, maxRetries = 3, baseDelayMs = 5_000, backoffFactor = 2.0)

        val startVirtual = currentTime
        val response = client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)
        val elapsed = currentTime - startVirtual

        assertEquals("immediate", response.body)
        assertEquals(0L, elapsed, "first-attempt success must not sleep, advanced $elapsed ms")
    }

    // ---------------------------------------------------------------------------
    // Cancellation
    // ---------------------------------------------------------------------------

    @Test
    fun `cancellation during backoff stops retry loop`() = runTest {
        val stub = ScriptedClient(List(10) { StubResponse.networkError("timeout") })
        val client = RetryHttpClient(stub, maxRetries = 9, baseDelayMs = 1_000, backoffFactor = 1.0)

        val deferred = async {
            client.post("http://test", emptyMap(), "{}", timeoutMs = 1_000)
        }

        // Let the first attempt run + enter backoff.
        runCurrent()
        // Cancel mid-backoff — the next ensureActive() (or the delay
        // itself) must surface CancellationException.
        deferred.cancel()
        advanceUntilIdle()

        assertTrue(deferred.isCancelled, "expected the post() coroutine to be cancelled")
        // We don't assert the exact attempt count: the racy moment is
        // "did the first attempt land before cancel reached the delay"
        // and that's platform-dependent. Cancellation landed — that's
        // the contract.
        assertTrue(stub.attempts >= 1, "expected at least one attempt before cancellation, got ${stub.attempts}")
    }

    // ---------------------------------------------------------------------------
    // Pass-through (url, headers, body forwarded unchanged)
    // ---------------------------------------------------------------------------

    @Test
    fun `passes url, headers, body, and timeoutMs through to delegate`() = runTest {
        var capturedUrl: String? = null
        var capturedHeaders: Map<String, String>? = null
        var capturedBody: String? = null
        var capturedTimeoutMs: Long? = null

        val delegate = HttpClient { url, headers, body, timeoutMs ->
            capturedUrl = url
            capturedHeaders = headers
            capturedBody = body
            capturedTimeoutMs = timeoutMs
            HttpResponse(200, "")
        }
        val client = RetryHttpClient(delegate, maxRetries = 0, baseDelayMs = 1)

        client.post(
            "http://example.com/preload",
            mapOf("X-Token" to "abc", "Content-Type" to "application/json"),
            """{"msg":"hello"}""",
            timeoutMs = 4_242,
        )

        assertEquals("http://example.com/preload", capturedUrl)
        assertEquals("abc", capturedHeaders!!["X-Token"])
        assertEquals("application/json", capturedHeaders!!["Content-Type"])
        assertEquals("""{"msg":"hello"}""", capturedBody)
        // The retry decorator must forward the per-call timeout — Session
        // sets it from `Session.preloadTimeoutMs`, which is updated by
        // the `/init` response.
        assertEquals(4_242L, capturedTimeoutMs)
    }

    // ---------------------------------------------------------------------------
    // DefaultHttpClient integration — exercises the real HttpURLConnection
    // path against an in-process ServerSocket. Catches regressions that
    // pure lambda fakes can't (header serialization, body encoding,
    // status-code parsing).
    // ---------------------------------------------------------------------------

    @Test
    fun `DefaultHttpClient sends POST with body and parses 2xx response`() = runTest {
        val server = StubHttpServer(
            responseStatus = "200 OK",
            responseBody = """{"echo":"received"}""",
        )
        try {
            val response = DefaultHttpClient.post(
                url = "http://127.0.0.1:${server.port}/preload",
                headers = mapOf("Content-Type" to "application/json", "X-Token" to "tok"),
                body = """{"hello":"world"}""",
                timeoutMs = 5_000,
            )

            assertEquals(200, response.statusCode)
            assertEquals("""{"echo":"received"}""", response.body)

            val request = server.awaitRequest()
            assertTrue(request.requestLine.startsWith("POST /preload"), "request line: ${request.requestLine}")
            assertEquals("application/json", request.headers["content-type"])
            assertEquals("tok", request.headers["x-token"])
            assertEquals("""{"hello":"world"}""", request.body)
        } finally {
            server.close()
        }
    }

    @Test
    fun `DefaultHttpClient honors timeoutMs and throws SocketTimeoutException on slow server`() = runTest {
        // Server accepts the connection but never writes a response.
        // With timeoutMs=200, DefaultHttpClient must raise inside ~200ms.
        val server = SilentHttpServer()
        try {
            val start = System.currentTimeMillis()
            assertThrows<java.net.SocketTimeoutException> {
                DefaultHttpClient.post(
                    url = "http://127.0.0.1:${server.port}/preload",
                    headers = emptyMap(),
                    body = "",
                    timeoutMs = 200,
                )
            }
            val elapsed = System.currentTimeMillis() - start
            // Generous upper bound — the assertion is "did NOT use the
            // 16s default", not "took exactly 200ms".
            assertTrue(elapsed < 5_000, "timeout took ${elapsed}ms; expected < 5s")
        } finally {
            server.close()
        }
    }

    @Test
    fun `DefaultHttpClient reads errorStream body on 4xx response`() = runTest {
        val server = StubHttpServer(
            responseStatus = "418 I'm a teapot",
            responseBody = """{"error":"short and stout"}""",
        )
        try {
            val response = DefaultHttpClient.post(
                url = "http://127.0.0.1:${server.port}/preload",
                headers = emptyMap(),
                body = "",
                timeoutMs = 5_000,
            )

            assertEquals(418, response.statusCode)
            assertEquals("""{"error":"short and stout"}""", response.body)
        } finally {
            server.close()
        }
    }
}

/**
 * Single-shot HTTP/1.1 server backed by raw `ServerSocket`. Reads a
 * POST request (request line + headers + Content-Length-bounded body),
 * writes back a fixed status + body, exposes the captured request for
 * assertions.
 *
 * Single-shot is enough for these tests — we want round-trip coverage,
 * not a full server. Lifetime ends in [close].
 */
private class StubHttpServer(
    private val responseStatus: String,
    private val responseBody: String,
) {
    private val server = ServerSocket(0)
    val port: Int = server.localPort

    private val requestSlot = java.util.concurrent.LinkedBlockingQueue<CapturedRequest>()
    private val thread = Thread {
        try {
            server.accept().use { socket ->
                // Read raw bytes — HttpURLConnection may use Transfer-Encoding:
                // chunked for the body, so we parse status line + headers,
                // then read either Content-Length bytes or chunked-encoded
                // body manually.
                val input = java.io.DataInputStream(socket.getInputStream())

                val requestLine = readLineCRLF(input) ?: return@use

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLineCRLF(input) ?: break
                    if (line.isEmpty()) break
                    val (k, v) = line.split(":", limit = 2).let { it[0].trim().lowercase() to it[1].trim() }
                    headers[k] = v
                }

                val body = if (headers["transfer-encoding"]?.equals("chunked", ignoreCase = true) == true) {
                    readChunkedBody(input)
                } else {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val bytes = ByteArray(contentLength)
                    if (contentLength > 0) input.readFully(bytes)
                    String(bytes, Charsets.UTF_8)
                }

                requestSlot.put(CapturedRequest(requestLine, headers, body))

                val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
                val responseHeaders = buildString {
                    append("HTTP/1.1 ").append(responseStatus).append("\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ").append(responseBytes.size).append("\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().apply {
                    write(responseHeaders.toByteArray(Charsets.UTF_8))
                    write(responseBytes)
                    flush()
                }
            }
        } catch (_: Throwable) {
            // Server-side errors surface as test timeouts on awaitRequest
            // or as a non-200 from the client — either way the assertion
            // captures the failure better than logging from a daemon thread.
        }
    }.also {
        it.isDaemon = true
        it.start()
    }

    private fun readLineCRLF(input: java.io.DataInputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty() && prev == -1) null else sb.toString()
            if (prev == '\r'.code && b == '\n'.code) {
                sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    private fun readChunkedBody(input: java.io.DataInputStream): String {
        val acc = java.io.ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLineCRLF(input) ?: break
            val size = sizeLine.takeWhile { it != ';' }.trim().toInt(16)
            if (size == 0) {
                // Trailing CRLF after the zero-length chunk.
                readLineCRLF(input)
                break
            }
            val chunk = ByteArray(size)
            input.readFully(chunk)
            acc.write(chunk)
            // Trailing CRLF after each chunk's data.
            readLineCRLF(input)
        }
        return String(acc.toByteArray(), Charsets.UTF_8)
    }

    fun awaitRequest(): CapturedRequest =
        requestSlot.poll(5, java.util.concurrent.TimeUnit.SECONDS)
            ?: error("StubHttpServer: no request received within 5s")

    fun close() {
        runCatching { server.close() }
        thread.interrupt()
    }

    data class CapturedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: String,
    )
}

/**
 * Accepts a single connection and never replies. Used to verify that
 * `DefaultHttpClient` honors the per-call `timeoutMs` rather than
 * silently falling back to the connection's default.
 */
private class SilentHttpServer {
    private val server = ServerSocket(0)
    val port: Int = server.localPort

    private val thread = Thread {
        try {
            // Hold the connection open until the test closes the server.
            server.accept().use {
                Thread.sleep(60_000)
            }
        } catch (_: Throwable) {
            // Closing the ServerSocket from the test releases accept().
        }
    }.also {
        it.isDaemon = true
        it.start()
    }

    fun close() {
        runCatching { server.close() }
        thread.interrupt()
    }
}

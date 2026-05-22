package so.kontext.ads.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * HTTP client abstraction. The single test seam for `Init` / `Preload`:
 * production wires `RetryHttpClient(DefaultHttpClient)`; tests inject
 * lambda fakes via the SAM constructor.
 *
 * `timeoutMs` is per-call, not per-instance, because `/init` and
 * `/preload` have different timeouts and the `/preload` value is
 * dynamic (overridden by the `preloadTimeout` field of the `/init`
 * response). Mirrors sdk-swift's per-request `URLRequest.timeoutInterval`.
 *
 * `suspend` so callers get cooperative cancellation through their
 * coroutine context — destroying a `Session` mid-request unwinds the
 * loop instead of leaking a background thread.
 */
internal fun interface HttpClient {
    suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): HttpResponse
}

internal data class HttpResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * Errors raised by [RetryHttpClient] for retry-loop outcomes that
 * don't map onto a network-level [IOException].
 *
 * Mirrors sdk-swift's `HTTPRetryError` and sdk-js's manufactured
 * `Error("HTTP 429")` — all three SDKs surface 429-retry exhaustion
 * as a typed throw rather than returning the last 429 response.
 *
 * Extends [RuntimeException] (not [IOException]) so the retry loop's
 * `catch (e: IOException)` branch doesn't accidentally treat it as
 * a transient network failure.
 */
internal sealed class HttpRetryException(message: String) : RuntimeException(message) {
    class RateLimited(val statusCode: Int = 429) :
        HttpRetryException("HTTP $statusCode: rate limited (retries exhausted)")
}

/**
 * Default HTTP client backed by `java.net.HttpURLConnection`. The
 * connection API is blocking, so the actual I/O runs on
 * `Dispatchers.IO`; the caller can stay on whatever dispatcher it
 * prefers without thread-blocking.
 */
internal object DefaultHttpClient : HttpClient {

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val timeout = timeoutMs.toInt()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeout
            readTimeout = timeout
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: ""

            HttpResponse(statusCode, responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Wraps an [HttpClient] with exponential-backoff retry on transient
 * failures. Retry policy mirrors `sdk-js/src/utils/request.ts` and
 * sdk-swift's `Networking/HTTPRetry.swift`:
 *
 * - **HTTP 429** (rate-limited) → retried; throws [HttpRetryException.RateLimited] when retries exhausted
 * - **Network errors** ([IOException] from the delegate) → retried; rethrown when retries exhausted
 * - **HTTP 2xx / 3xx / 4xx (non-429) / 5xx** → returned as-is
 *
 * 5xx is intentionally NOT retried — the caller decides whether to
 * surface or swallow it. This matches sdk-js (`request.test.ts` pins
 * "does NOT retry on 500/503") and sdk-swift (`HTTPRetryTests`
 * `returns5xxAsIsWithoutRetry`).
 *
 * Cooperative cancellation: the wrapping coroutine's cancellation is
 * checked at the start of each attempt and inside the backoff sleep
 * (via [delay]), so a cancelled `Session` unwinds within at most one
 * backoff window.
 */
internal class RetryHttpClient(
    private val delegate: HttpClient = DefaultHttpClient,
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1_000,
    private val backoffFactor: Double = 2.0,
) : HttpClient {

    init {
        require(maxRetries >= 0) { "RetryHttpClient: maxRetries must be non-negative, got $maxRetries" }
    }

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Long,
    ): HttpResponse {
        var lastNetworkError: IOException? = null

        for (attempt in 0..maxRetries) {
            coroutineContext.ensureActive()

            val response: HttpResponse = try {
                delegate.post(url, headers, body, timeoutMs)
            } catch (e: IOException) {
                // Network-level failure — retryable per sdk-js / sdk-swift.
                lastNetworkError = e
                if (attempt == maxRetries) throw e
                delay(backoffMs(attempt))
                continue
            }

            // 429 → retry; everything else (2xx / 3xx / 4xx non-429 / 5xx)
            // is returned as-is so the caller can inspect status + body.
            if (response.statusCode != 429) {
                return response
            }

            if (attempt == maxRetries) {
                throw HttpRetryException.RateLimited(response.statusCode)
            }
            delay(backoffMs(attempt))
        }

        // Unreachable: the loop body always either returns, continues, or throws.
        // Kotlin can't prove that, so we error explicitly.
        error("RetryHttpClient.post fell through the retry loop (lastNetworkError=$lastNetworkError)")
    }

    private fun backoffMs(attempt: Int): Long =
        (baseDelayMs * Math.pow(backoffFactor, attempt.toDouble())).toLong()
}

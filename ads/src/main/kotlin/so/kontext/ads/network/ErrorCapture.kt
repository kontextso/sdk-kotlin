package so.kontext.ads.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import so.kontext.ads.Constants
import so.kontext.ads.SDKInfo
import so.kontext.ads.network.dto.ErrorRequestDto
import so.kontext.ads.network.dto.SdkDto
import java.util.UUID

/**
 * JSON config for `/error` request bodies. Omits null fields entirely
 * (matches sdk-swift's default `Encodable` behaviour) so identifiers
 * like `publisherToken` / `bidId` / `stack` aren't written as `null`
 * keys when absent — the server treats absent and `null` differently
 * when filtering attribution events.
 */
private val errorJson = Json {
    explicitNulls = false
    encodeDefaults = false
}

/**
 * Context bundle for `/error` reports. Mirrors sdk-swift's
 * `ErrorContext` struct: a focused 5-field carrier, separate from the
 * full `ResolvedConfig` so callers don't need a Session to report a
 * standalone failure (e.g. `Init.fetch` runs before a session exists).
 *
 * `bidId` is typed as [UUID] for symmetry with the public
 * `AdEvent.bidId` and `Bid.bidId`. The wire form is the lowercase
 * canonical string emitted by `UUID.toString()` (Java guarantees
 * lowercase per RFC 4122; pinned by `AdTest.\`bidId stringifies to
 * lowercase canonical form for URL paths\``).
 */
internal data class ErrorContext(
    val adServerUrl: String,
    val publisherToken: String? = null,
    val conversationId: String? = null,
    val userId: String? = null,
    val bidId: UUID? = null,
)

/**
 * Fire-and-forget error reporting to the ad server's `/error` endpoint.
 *
 * Two overloads share one POST implementation, mirroring sdk-swift's
 * `enum ErrorCapture { static func capture(...) }` with the same two
 * shapes:
 *   - [capture] taking a [Throwable] — auto-stringifies the message
 *     and stack trace; called by `Session.reportError`.
 *   - [capture] taking a pre-built [message] and optional [stack] —
 *     called by `Init.fetch` and `Preload.requestAd` for named
 *     diagnostic reports.
 *
 * Two-leg behaviour mirrors sdk-swift / sdk-js: the local leg
 * ([Log.e] under tag `KontextAds`) always runs so a publisher running
 * the app in Android Studio sees the error in real time. The network
 * leg (`POST /error`) is gated by `reportEnabled` — defaults to `true`
 * for pre-init paths (which can't yet know the server flag), and
 * Session passes its `/init`-supplied flag explicitly.
 *
 * Both launch on an internal [SupervisorJob] scope so the calling
 * coroutine / lifecycle doesn't stall on a slow `/error` POST. The
 * scope is process-wide (telemetry outlives any one Session — `Init`
 * reports failures before a session exists) but each launched coroutine
 * is self-contained: a failure or timeout in one report doesn't affect
 * other reports or the rest of the SDK.
 *
 * Both swallow every exception — error reporting must never propagate
 * failures back into the SDK.
 *
 * Mirrors iOS `Networking/ErrorCapture.swift`.
 */
internal object ErrorCapture {

    private const val LOG_TAG = "KontextAds"

    /**
     * Process-wide scope for fire-and-forget error reports. `SupervisorJob`
     * means one failed report doesn't cancel sibling reports. Lifetime
     * matches the JVM process — telemetry outlives any `Session`.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun capture(error: Throwable, source: String? = null, context: ErrorContext, reportEnabled: Boolean = true) {
        capture(
            message = error.message ?: error.toString(),
            stack = source ?: error.stackTraceToString(),
            context = context,
            reportEnabled = reportEnabled,
        )
    }

    fun capture(message: String, stack: String? = null, context: ErrorContext, reportEnabled: Boolean = true) {
        // Local leg: always runs. Logcat tag is grep-friendly and
        // identifies the SDK in mixed app logs. Mirrors `console.error`
        // in sdk-js and `print` in sdk-swift.
        if (stack != null) Log.e(LOG_TAG, message, RuntimeException(stack)) else Log.e(LOG_TAG, message)
        if (!reportEnabled) return
        scope.launch {
            postErrorReport(context, message, stack)
        }
    }
}

/**
 * Builds the JSON body for a `POST /error` request. Extracted from
 * [postErrorReport] so the body shape (which is what regresses) can be
 * unit-tested without spinning up an HTTP server.
 *
 * Wire format matches sdk-js / sdk-swift exactly — the ad server keys
 * off `error` + `additionalData.publisherToken` + `additionalData.sdk.name`,
 * so a regression here breaks server-side error attribution silently.
 */
internal fun buildErrorReportBody(
    context: ErrorContext,
    message: String,
    stack: String?,
): String {
    val dto = ErrorRequestDto(
        error = message,
        stack = stack,
        additionalData = ErrorRequestDto.AdditionalData(
            publisherToken = context.publisherToken,
            conversationId = context.conversationId,
            userId = context.userId,
            bidId = context.bidId?.toString(),
            sdk = SdkDto(name = SDKInfo.NAME, platform = SDKInfo.PLATFORM, version = SDKInfo.VERSION),
        ),
    )
    return errorJson.encodeToString(dto)
}

/**
 * Sends the error body to `/error` via [DefaultHttpClient]. Routes
 * through the same HTTP path as `/init` and `/preload` (single
 * connection-management codebase, single timeout knob, single
 * cancellation contract) instead of duplicating `HttpURLConnection`
 * plumbing.
 *
 * Suspending: the calling [ErrorCapture] coroutine carries the work.
 * The internal scope is `SupervisorJob + Dispatchers.IO` so an
 * exception here can't tear down sibling reports — but we still
 * swallow defensively because error reporting must never throw back
 * into the SDK.
 *
 * No retry decorator: `/error` is fire-and-forget telemetry; retrying
 * a failed error report on transient network errors would cascade into
 * background work the publisher didn't ask for. `RetryHttpClient` stays
 * scoped to `/init` and `/preload`.
 */
internal suspend fun postErrorReport(
    context: ErrorContext,
    message: String,
    stack: String?,
    httpClient: HttpClient = DefaultHttpClient,
) {
    try {
        httpClient.post(
            url = "${context.adServerUrl}/error",
            headers = mapOf("Content-Type" to "application/json"),
            body = buildErrorReportBody(context, message, stack),
            timeoutMs = Constants.ERROR_REPORT_TIMEOUT_MS,
        )
    } catch (_: Exception) {
        // Error reporting must never throw
    }
}

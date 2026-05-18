@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package so.kontext.ads.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import so.kontext.ads.Constants
import so.kontext.ads.SDKInfo
import so.kontext.ads.network.dto.DebugRequestDto
import so.kontext.ads.network.dto.SdkDto

/**
 * JSON config for `/debug` request bodies. Same shape rules as
 * [errorJson] in [ErrorCapture]: nulls omitted, defaults skipped.
 */
private val debugJson = Json {
    explicitNulls = false
    encodeDefaults = false
}

/**
 * Context bundle for `/debug` reports. Parallel to [ErrorContext]
 * minus `bidId` — debug events are session-scoped, not bid-scoped.
 * `sessionId` is included so the server can filter for one
 * diagnostic session at a time.
 *
 * Mirrors iOS `DebugContext` (`Networking/DebugCapture.swift`).
 */
internal data class DebugContext(
    val adServerUrl: String,
    val publisherToken: String? = null,
    val conversationId: String? = null,
    val userId: String? = null,
    val installId: String? = null,
    val sessionId: String? = null,
)

/**
 * Forwards `Session.debug(...)` events to the ad server when the
 * publisher has been opted in via the `/init` response
 * (`reportDebug = true`).
 *
 * The publisher's `onDebugEvent` callback is the local leg and runs
 * unconditionally inside Session — [DebugCapture] is purely the
 * network leg, so the entry point is a single [capture] instead of
 * the dual-leg shape [ErrorCapture] uses.
 *
 * Fire-and-forget, mirroring [ErrorCapture]. Defaults to off because
 * debug payloads can be large and contain structured session state;
 * the server flips it on per-userId only when actively diagnosing.
 *
 * Mirrors iOS `DebugCapture` (`Networking/DebugCapture.swift`).
 */
internal object DebugCapture {

    /**
     * Process-wide scope for fire-and-forget debug reports. Same
     * supervisor pattern as [ErrorCapture].
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * `httpClient` is injectable purely for tests — production calls
     * use [DefaultHttpClient]. Mirrors sdk-swift's `session: URLSession`
     * test seam, parallel to [ErrorCapture.capture]. Lets tests assert
     * that the entry point actually fires a POST through the detached
     * scope (not just that [postDebugReport] does the right thing when
     * called directly).
     */
    fun capture(name: String, data: Any? = null, context: DebugContext, httpClient: HttpClient = DefaultHttpClient) {
        scope.launch {
            postDebugReport(context, name, stringify(data), httpClient)
        }
    }

    /**
     * Best-effort serialisation of an arbitrary `data: Any?` to a
     * JSON-shaped string. Falls back to `toString()` so non-JSON
     * values (errors, custom classes, dates) still survive on the
     * wire — the server treats `data` as opaque text either way.
     */
    private fun stringify(value: Any?): String? {
        if (value == null) return null
        return runCatching {
            when (value) {
                is Map<*, *> -> JSONObject(value.mapKeys { it.key.toString() }).toString()
                is Collection<*> -> JSONArray(value).toString()
                is Array<*> -> JSONArray(value).toString()
                is Number, is Boolean, is String -> value.toString()
                else -> value.toString()
            }
        }.getOrDefault(value.toString())
    }
}

/**
 * Sends the debug body to `/debug` via [DefaultHttpClient]. Mirrors
 * [postErrorReport]: same retry-less, fire-and-forget contract;
 * never throws back into the SDK.
 */
internal suspend fun postDebugReport(
    context: DebugContext,
    name: String,
    data: String?,
    httpClient: HttpClient = DefaultHttpClient,
) {
    try {
        val dto = DebugRequestDto(
            name = name,
            data = data,
            additionalData = DebugRequestDto.AdditionalData(
                publisherToken = context.publisherToken,
                conversationId = context.conversationId,
                userId = context.userId,
                installId = context.installId,
                sessionId = context.sessionId,
                sdk = SdkDto(name = SDKInfo.NAME, platform = SDKInfo.PLATFORM, version = SDKInfo.VERSION),
            ),
        )
        httpClient.post(
            url = "${context.adServerUrl}/debug",
            headers = mapOf("Content-Type" to "application/json"),
            body = debugJson.encodeToString(dto),
            timeoutMs = Constants.ERROR_REPORT_TIMEOUT_MS,
        )
    } catch (_: Exception) {
        // Debug reporting must never throw
    }
}

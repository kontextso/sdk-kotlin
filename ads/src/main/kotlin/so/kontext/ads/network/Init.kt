@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package so.kontext.ads.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import so.kontext.ads.Constants
import so.kontext.ads.ResolvedConfig
import so.kontext.ads.SDKInfo
import so.kontext.ads.network.dto.InitRequestDto
import so.kontext.ads.network.dto.InitResponseDto
import so.kontext.ads.network.dto.SdkDto

/**
 * Fires a `POST /init` request to fetch per-publisher configuration.
 *
 * Takes a typed [InitRequestDto.AppMetadata] rather than an Android
 * `Context`: the caller (`Session.fireInit`) handles the
 * `AppInfoProvider.collect` step, and tests can supply a fake metadata
 * struct without spinning up a `Context`. Mirrors sdk-swift's
 * `Init.fetch(config:app:)` shape.
 *
 * Never throws — returns null on any failure so the SDK never blocks
 * the publisher's app. Diagnostic events route through
 * `config.onDebugEvent` (the public callback publishers wire to their
 * own analytics); failures route through [ErrorCapture] for server-side
 * logging. Cancellation propagates per coroutine convention but doesn't
 * fire ErrorCapture (Session was destroyed mid-init — not an error).
 *
 * The JSON instance:
 *  - `ignoreUnknownKeys = true` — server can add response fields without
 *    breaking older SDK clients
 *  - `encodeDefaults = false` — properties at their default value are
 *    omitted from the wire payload
 *  - `explicitNulls = false` — null fields are omitted instead of emitted
 *    as `"key": null` (matches sdk-swift's default `Encodable`; the
 *    server distinguishes absent from null for some attribution filters)
 */
internal object Init {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    suspend fun fetch(
        config: ResolvedConfig,
        httpClient: HttpClient = DefaultHttpClient,
        app: InitRequestDto.AppMetadata,
    ): InitResponseDto? {
        config.onDebugEvent?.invoke("Init: start", null)
        return try {
            val request = InitRequestDto(
                publisherToken = config.publisherToken,
                userId = config.userId,
                installId = config.installId,
                sdk = SdkDto(name = SDKInfo.NAME, platform = SDKInfo.PLATFORM, version = SDKInfo.VERSION),
                app = app,
            )
            val body = json.encodeToString(request)

            val response = httpClient.post(
                url = "${config.adServerUrl}/init",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Kontextso-Publisher-Token" to config.publisherToken,
                ),
                body = body,
                timeoutMs = Constants.INIT_TIMEOUT_MS,
            )

            when {
                // Server explicitly opted out of returning a body
                // (publisher disabled / unknown). Not an error.
                response.statusCode == 204 -> {
                    config.onDebugEvent?.invoke("Init: no-content", mapOf("status" to 204))
                    null
                }
                response.statusCode !in 200..299 -> {
                    config.onDebugEvent?.invoke("Init: non-ok", mapOf("status" to response.statusCode))
                    null
                }
                // Tolerate empty body on any 2xx (205, 200 with no body) —
                // same intent as 204, just with a less-specific status code.
                // Don't try to decode `""`; that would throw and surface as
                // a false-positive ErrorCapture report.
                response.body.isBlank() -> {
                    config.onDebugEvent?.invoke("Init: empty-body", mapOf("status" to response.statusCode))
                    null
                }
                else -> {
                    val parsed = json.decodeFromString<InitResponseDto>(response.body)
                    config.onDebugEvent?.invoke("Init: response", mapOf("response" to parsed))
                    parsed
                }
            }
        } catch (e: CancellationException) {
            // Session was destroyed while /init was in flight. Re-throw
            // per coroutine convention so structured concurrency unwinds
            // correctly; don't fire ErrorCapture (cancellation isn't an
            // error worth reporting to the server).
            config.onDebugEvent?.invoke("Init: cancelled", null)
            throw e
        } catch (e: Exception) {
            config.onDebugEvent?.invoke("Init: error", mapOf("error" to (e.message ?: e.toString())))
            ErrorCapture.capture(
                error = e,
                source = "Init.fetch",
                context = ErrorContext(
                    adServerUrl = config.adServerUrl,
                    publisherToken = config.publisherToken,
                    conversationId = config.conversationId,
                    userId = config.userId,
                    installId = config.installId,
                ),
            )
            null
        }
    }
}

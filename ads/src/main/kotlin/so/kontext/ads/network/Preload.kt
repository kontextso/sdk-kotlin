package so.kontext.ads.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import so.kontext.ads.Constants
import so.kontext.ads.ResolvedConfig
import so.kontext.ads.SDKInfo
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.Bid
import so.kontext.ads.model.Message
import so.kontext.ads.model.PreloadResult
import so.kontext.ads.model.Regulatory
import so.kontext.ads.model.toDomain
import so.kontext.ads.model.toDto
import so.kontext.ads.network.dto.AppDto
import so.kontext.ads.network.dto.DeviceDto
import so.kontext.ads.network.dto.PreloadRequestDto
import so.kontext.ads.network.dto.PreloadResponseDto
import so.kontext.ads.network.dto.RegulatoryDto
import so.kontext.ads.network.dto.SdkDto
import so.kontext.kit.privacy.TCFDataProvider

/**
 * Handles a single preload request to the /preload endpoint.
 *
 * Immutable snapshot of messages at the time of preload. `device`,
 * `app`, and `tcf` are pre-collected by `Session.addMessage` (which
 * has the Android `Context`) and passed in typed — Preload itself is
 * Context-free, so tests can construct it without mocking. Mirrors
 * the Init.fetch pattern.
 */
internal class Preload(
    private val messages: List<Message>,
    private val config: ResolvedConfig,
    private val device: DeviceDto,
    private val app: AppDto,
    private val tcf: TCFDataProvider.TCFData? = null,
    private val httpClient: HttpClient = RetryHttpClient(),
    /**
     * Per-call timeout for the `/preload` POST. Defaults to
     * [Constants.DEFAULT_PRELOAD_TIMEOUT_MS]; `Session.fireRequest`
     * passes the dynamic value from `Session.preloadTimeoutMs`, which
     * may have been overridden by the `preloadTimeout` field of the
     * `/init` response.
     */
    private val timeoutMs: Long = so.kontext.ads.Constants.DEFAULT_PRELOAD_TIMEOUT_MS,
    /**
     * Server-controlled `/error` POST gate, threaded through from
     * `Session.reportErrors` so the network-failure path here honours
     * the same kill switch as `Session.reportError`. Defaults to `true`
     * for tests / pre-init paths that don't override.
     */
    private val reportErrors: Boolean = true,
) {
    // coerceInputValues = true makes unknown enum values fall back to
    // null on nullable fields (e.g. BidDto.impressionTrigger) instead of
    // throwing the whole response decode — matches sdk-swift's `try?`
    // tolerance on optional metadata fields.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        coerceInputValues = true
    }

    /**
     * Bids returned by the most recent successful preload, filtered to
     * `config.enabledPlacementCodes`. Read by `Session.getBid` to find
     * a bid matching a specific placement code (one bid per placement).
     */
    internal var bids: List<Bid> = emptyList()
        private set

    private fun debug(event: String, data: Any? = null) {
        android.util.Log.d("KontextAds", "$event ${data ?: ""}")
        config.onDebugEvent?.invoke(event, data)
    }

    fun hasBid(): Boolean = bids.isNotEmpty()

    /**
     * Performs the preload request. Suspends — call from a coroutine.
     */
    suspend fun requestAd(
        sessionId: java.util.UUID?,
        disabled: Boolean,
        advertisingId: String? = null,
    ): PreloadResult {
        if (messages.isEmpty()) {
            debug("Preload: no-messages", null)
            return PreloadResult.Failure(reason = "No messages", disableSession = false)
        }

        return try {
            val mergedRegulatory = mergeRegulatory(config.regulatory, tcf)

            val dto = buildRequestDto(sessionId, mergedRegulatory, advertisingId)
            val body = json.encodeToString(dto)

            debug(
                "Preload: request-ad-start",
                mapOf("url" to "${config.adServerUrl}/preload", "messageCount" to messages.size),
            )

            val response = httpClient.post(
                url = "${config.adServerUrl}/preload",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Kontextso-Publisher-Token" to config.publisherToken,
                    "Kontextso-Is-Disabled" to if (disabled) "1" else "0",
                ),
                body = body,
                timeoutMs = timeoutMs,
            )

            debug(
                "Preload: request-ad-response",
                mapOf("statusCode" to response.statusCode, "bodyPreview" to response.body.take(200)),
            )

            if (response.statusCode !in 200..299) {
                return PreloadResult.Failure(
                    reason = "HTTP ${response.statusCode}",
                    event = AdEvent.Error(message = "HTTP ${response.statusCode}", errCode = "request_failed"),
                )
            }

            val preloadResponse = json.decodeFromString<PreloadResponseDto>(response.body)
            handleResponse(preloadResponse)
        } catch (e: Exception) {
            android.util.Log.e("KontextAds", "Preload: exception", e)
            debug("Preload: error-preloading-ads", mapOf("error" to (e.message ?: "Unknown error")))
            ErrorCapture.capture(
                message = "Error preloading ads: $e",
                stack = e.stackTraceToString(),
                context = ErrorContext(
                    adServerUrl = config.adServerUrl,
                    publisherToken = config.publisherToken,
                    conversationId = config.conversationId,
                    userId = config.userId,
                ),
                reportEnabled = reportErrors,
            )
            PreloadResult.Failure(
                reason = e.message ?: "Unknown error",
                event = AdEvent.Error(message = e.message ?: "Unknown error", errCode = "request_failed"),
            )
        }
    }

    private fun handleResponse(response: PreloadResponseDto): PreloadResult {
        // Error state
        if (response.errCode != null || response.sessionId == null) {
            val isPermanent = response.permanent == true
            val errCode = response.errCode ?: if (isPermanent) "session_disabled" else "unknown"
            val message = if (isPermanent) "Session is disabled" else "Ad generation skipped"
            if (isPermanent) {
                debug("Preload: session-disabled", mapOf("errCode" to errCode))
            }
            debug("Preload: ad-generation-error", mapOf("errCode" to errCode))
            return PreloadResult.Failure(
                reason = message,
                event = AdEvent.Error(message = message, errCode = errCode),
                disableSession = isPermanent,
            )
        }

        // Skip / no-fill
        if (response.skip == true) {
            debug("Preload: ad-generation-skipped", mapOf("skipCode" to (response.skipCode ?: "unknown")))
            return PreloadResult.Failure(
                reason = response.skipCode ?: "no_fill",
                event = AdEvent.NoFill(skipCode = response.skipCode ?: "no_fill"),
            )
        }

        if (response.bids.isNullOrEmpty()) {
            return PreloadResult.Failure(
                reason = response.skipCode ?: "no_fill",
                event = AdEvent.NoFill(skipCode = response.skipCode ?: "no_fill"),
            )
        }

        // Bids returned
        debug("Preload: ad-generation-success", mapOf("bidCount" to response.bids.size))

        // Filter by enabled placement codes
        val matchingBids = response.bids.filter { it.code in config.enabledPlacementCodes }

        if (matchingBids.isEmpty()) {
            debug(
                "Preload: no-bids-for-placement-codes",
                mapOf(
                    "enabledCodes" to config.enabledPlacementCodes,
                    "bidCodes" to response.bids.map { it.code },
                ),
            )
        }

        bids = matchingBids.map { it.toDomain() }

        return PreloadResult.Success(
            bids = bids,
            sessionId = response.sessionId,
        )
    }

    private fun buildRequestDto(
        sessionId: java.util.UUID?,
        regulatory: RegulatoryDto?,
        advertisingId: String? = null,
    ): PreloadRequestDto {
        val lastMessages = if (messages.size > Constants.MAX_MESSAGES) {
            messages.takeLast(Constants.MAX_MESSAGES)
        } else {
            messages
        }

        return PreloadRequestDto(
            publisherToken = config.publisherToken,
            userId = config.userId,
            conversationId = config.conversationId,
            enabledPlacementCodes = config.enabledPlacementCodes,
            messages = lastMessages.map { it.toDto() },
            sdk = SdkDto(name = SDKInfo.NAME, version = SDKInfo.VERSION, platform = SDKInfo.PLATFORM),
            device = device,
            app = app,
            sessionId = sessionId,
            character = config.character?.toDto(),
            regulatory = regulatory,
            userEmail = config.userEmail,
            variantId = config.variantId,
            advertisingId = advertisingId ?: config.advertisingId,
        )
    }

    private fun mergeRegulatory(
        existing: Regulatory?,
        tcf: TCFDataProvider.TCFData?,
    ): RegulatoryDto? {
        // TCF wins for gdpr / gdprConsent (on-device source-of-truth);
        // publisher values are used as-is for everything else.
        val merged = Regulatory(
            gdpr = tcf?.gdpr ?: existing?.gdpr,
            gdprConsent = tcf?.gdprConsent ?: existing?.gdprConsent,
            coppa = existing?.coppa,
            gpp = existing?.gpp,
            gppSid = existing?.gppSid,
            usPrivacy = existing?.usPrivacy,
        )
        return if (merged.isAllNull()) null else merged.toDto()
    }
}

private fun Regulatory.isAllNull(): Boolean =
    gdpr == null && gdprConsent == null && coppa == null &&
        gpp == null && gppSid == null && usPrivacy == null

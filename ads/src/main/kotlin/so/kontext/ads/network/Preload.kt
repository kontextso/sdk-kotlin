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
 * Bundle of inputs for a single [Preload] request.
 *
 * `device`, `app`, and `tcf` are pre-collected by `Session.addMessage`
 * (which has the Android `Context`) and passed in typed so [Preload]
 * itself stays Context-free and tests can build it without mocking.
 */
internal data class PreloadParams(
    val messages: List<Message>,
    val config: ResolvedConfig,
    val device: DeviceDto,
    val app: AppDto,
    val tcf: TCFDataProvider.TCFData? = null,
    val httpClient: HttpClient = RetryHttpClient(),
    /**
     * Per-call timeout for the `/preload` POST. Defaults to
     * [Constants.DEFAULT_PRELOAD_TIMEOUT_MS]; `Session.fireRequest`
     * passes the dynamic value from `Session.preloadTimeoutMs`, which
     * may have been overridden by the `preloadTimeout` field of the
     * `/init` response.
     */
    val timeoutMs: Long = so.kontext.ads.Constants.DEFAULT_PRELOAD_TIMEOUT_MS,
    /**
     * Server-controlled `/error` POST gate, threaded through from
     * `Session.reportErrors` so the network-failure path here honours
     * the same kill switch as `Session.reportError`. Defaults to `true`
     * for tests / pre-init paths that don't override.
     */
    val reportErrors: Boolean = true,
)

/**
 * Handles a single preload request to the /preload endpoint.
 *
 * Immutable snapshot of messages at the time of preload. Mirrors the
 * Init.fetch pattern.
 */
internal class Preload(private val params: PreloadParams) {
    private val messages: List<Message> get() = params.messages
    private val config: ResolvedConfig get() = params.config
    private val device: DeviceDto get() = params.device
    private val app: AppDto get() = params.app
    private val tcf: TCFDataProvider.TCFData? get() = params.tcf
    private val httpClient: HttpClient get() = params.httpClient
    private val timeoutMs: Long get() = params.timeoutMs
    private val reportErrors: Boolean get() = params.reportErrors

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
                mapOf(
                    "url" to "${config.adServerUrl}/preload",
                    "messageCount" to messages.size,
                    // Surface the per-call timeout alongside the request
                    // body so manual testers can verify the /init response's
                    // preloadTimeout actually threaded through (Session
                    // applies it in applyInitResult; PreloadParams forwards
                    // it; this is the last hop before HttpClient.post).
                    "timeoutMs" to timeoutMs,
                    "body" to body,
                ),
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
                mapOf("statusCode" to response.statusCode, "body" to response.body),
            )

            // 204 No Content is the server's explicit "no fill" — empty
            // body, no bid. Branch before the 2xx decode below: 204 is in
            // 200..299 so the non-2xx guard doesn't catch it, and an empty
            // body fails `decodeFromString` and would otherwise route to
            // the catch block as `AdEvent.Error` + `/error` report.
            // Mirrors sdk-swift `Preload.handleResponse` (status == 204
            // branch).
            if (response.statusCode == 204) {
                debug("Preload: no-content", mapOf("statusCode" to 204))
                return PreloadResult.Failure(
                    reason = "No content",
                    event = AdEvent.NoFill(skipCode = "unfilled_bid"),
                )
            }

            if (response.statusCode !in 200..299) {
                return PreloadResult.Failure(
                    reason = "HTTP ${response.statusCode}",
                    event = AdEvent.Error(message = "HTTP ${response.statusCode}", errCode = "request_failed"),
                )
            }

            val preloadResponse = json.decodeFromString<PreloadResponseDto>(response.body)
            handleResponse(preloadResponse)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // A newer user message cancelled this Job mid-request via
            // `Session.preloadJob?.cancel()`. Let cancellation propagate
            // so the launched coroutine completes as Cancelled instead
            // of routing through `handlePreloadResult(Failure)` and
            // emitting a spurious AdEvent.Error for the superseded call.
            throw e
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
                    installId = config.installId,
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
        handleErrorResponse(response)?.let { return it }
        handleSkipResponse(response)?.let { return it }
        return handleBidsResponse(response)
    }

    private fun handleErrorResponse(response: PreloadResponseDto): PreloadResult? {
        if (response.errCode == null && response.sessionId != null) return null
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

    private fun handleSkipResponse(response: PreloadResponseDto): PreloadResult? {
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
        return null
    }

    private fun handleBidsResponse(response: PreloadResponseDto): PreloadResult {
        val responseBids = response.bids.orEmpty()
        debug("Preload: ad-generation-success", mapOf("bidCount" to responseBids.size))

        val matchingBids = responseBids.filter { it.code in config.enabledPlacementCodes }
        bids = matchingBids.map { it.toDomain() }

        // Server returned bids but none matched the publisher's enabled
        // placement codes — same publisher-visible outcome as the
        // server-emitted `bids: []` case, so emit `NoFill` rather than
        // returning a silent `Success(bids = [])` that drops on the floor
        // in `Session.handlePreloadSuccess`. Mirrors sdk-swift
        // `Preload.handleResponse` (the `if bids.isEmpty` check after
        // `recordBids`).
        if (bids.isEmpty()) {
            debug(
                "Preload: no-bids-for-placement-codes",
                mapOf(
                    "enabledCodes" to config.enabledPlacementCodes,
                    "bidCodes" to responseBids.map { it.code },
                ),
            )
            return PreloadResult.Failure(
                reason = "No bids in response",
                event = AdEvent.NoFill(skipCode = "unfilled_bid"),
            )
        }

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
            installId = config.installId,
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

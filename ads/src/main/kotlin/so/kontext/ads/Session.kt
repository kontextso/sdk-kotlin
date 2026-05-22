package so.kontext.ads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.AdOptions
import so.kontext.ads.model.AddMessageOptions
import so.kontext.ads.model.Bid
import so.kontext.ads.model.Message
import so.kontext.ads.model.MutablePublisherOptions
import so.kontext.ads.model.PreloadResult
import so.kontext.ads.model.Role
import so.kontext.ads.model.UserEventName
import so.kontext.ads.network.DebugCapture
import so.kontext.ads.network.DebugContext
import so.kontext.ads.network.ErrorCapture
import so.kontext.ads.network.ErrorContext
import so.kontext.ads.network.Init
import so.kontext.ads.network.Preload
import so.kontext.ads.ui.WebViewPool
import so.kontext.kit.deviceinfo.AdvertisingIdProvider
import so.kontext.kit.omsdk.OmManager
import so.kontext.kit.omsdk.OmPartner
import java.io.Closeable

/**
 * Manages the ad session lifecycle: messages, preloads, and bid tracking.
 *
 * Implements the v4 pattern: createSession → addMessage → createAd.
 *
 * - User messages trigger a preload request.
 * - Bids are assigned to the last assistant message.
 * - Ad instances subscribe to bid updates and resolve iframeUrls when bids arrive.
 */
public class Session internal constructor(
    internal val context: Context?,
    config: ResolvedConfig,
    internal val httpClient: so.kontext.ads.network.HttpClient = so.kontext.ads.network.RetryHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Closeable {

    /**
     * Resolved publisher config. Identity fields are fixed; preload-scoped
     * fields can be updated via [updateOptions]. Preload reads through
     * `config.X` at request time so updates propagate without re-creating
     * the session.
     */
    internal var config: ResolvedConfig = config
        private set

    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------

    private val _messages = mutableListOf<Message>()
    public val messages: List<Message> get() = synchronized(_messages) { _messages.toList() }

    public var sessionId: java.util.UUID? = null
        private set

    public var disabled: Boolean = false
        private set

    /** Dynamic preload timeout from /init response (milliseconds). */
    internal var preloadTimeoutMs: Long = Constants.DEFAULT_PRELOAD_TIMEOUT_MS

    /**
     * Server-controlled `/error` POST gate. Defaults to `true`; flipped
     * to `false` only by an explicit `reportErrors: false` in the
     * `/init` response. Local error logging (Log.e) always runs
     * regardless — this only gates the network leg. `internal` for
     * tests; the publisher cannot override the server flag.
     */
    internal var reportErrors: Boolean = true
        private set

    /**
     * Server-controlled `/debug` forwarding gate. Defaults to `false`;
     * flipped to `true` only by an explicit `reportDebug: true` in the
     * `/init` response. Publisher's `onDebugEvent` callback always
     * fires regardless — this only gates the additional network leg,
     * opted in per-user for targeted diagnostics.
     */
    internal var reportDebug: Boolean = false
        private set

    /** Collected advertising ID (GAID). */
    internal var collectedAdvertisingId: String? = null
        private set

    /** Assigned bids: preloadInstance → messageId mapping. */
    private val assignedBids = mutableListOf<AssignedBid>()

    /** Currently active preload instance, if any. */
    private var preloadInstance: Preload? = null

    /** Listeners that Ad instances register to be notified when bids change. */
    internal val bidUpdateListeners = mutableListOf<() -> Unit>()

    /** Ad instances tracked for cleanup and user event broadcasting. */
    private val ads = mutableListOf<Ad>()

    /**
     * Main-thread scope for OMID session creation. The OMID native SDK
     * requires `mainAdView` assignment + session start on the UI thread;
     * `Dispatchers.Main.immediate` keeps already-on-main calls
     * synchronous so the 50 ms geometry-stability delay doesn't add an
     * extra dispatcher hop. Cancelled in `destroy()`.
     */
    internal val scopeOnMain: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------

    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 32)
    public val events: SharedFlow<AdEvent> = _events.asSharedFlow()

    // ---------------------------------------------------------------------------
    // Init
    // ---------------------------------------------------------------------------

    /**
     * OMID manager for this session. Created with the per-SDK partner identity
     * + activated synchronously on Session init (when running with a real
     * Context). `activate()` is idempotent if the OMID native SDK is already
     * up. `null` in test environments where `context` was deliberately omitted.
     */
    internal val omManager: OmManager? = context?.let { ctx ->
        OmManager(OmPartner(name = Constants.OMID_PARTNER_NAME, version = Constants.OMID_PARTNER_VERSION))
            .also { it.activate(ctx) }
    }

    init {
        debug(
            "Session: created",
            mapOf("publisher" to config.publisherToken, "conv" to config.conversationId),
        )

        // Context-dependent initialization (skipped in tests with null context)
        if (context != null) {
            // Track the host app's foreground Activity so click handling
            // can attach Chrome Custom Tabs even when the publisher passed
            // `applicationContext` into createSession. See ActivityTracker.
            so.kontext.ads.internal.ActivityTracker.ensureRegistered(context)
            scope.launch { fireInit() }
            scope.launch { collectAdvertisingId() }
        }
    }

    private suspend fun fireInit() {
        val ctx = context ?: return
        val appInfo = so.kontext.kit.deviceinfo.AppInfoProvider.collect(ctx)
        val app = so.kontext.ads.network.dto.InitRequestDto.AppMetadata(
            bundleId = appInfo.bundleId,
            version = appInfo.version,
        )
        val result = withContext(Dispatchers.IO) {
            Init.fetch(config, httpClient, app = app)
        }
        result ?: return
        applyInitResult(result)
    }

    /**
     * Applies the `/init` response to per-session state. Extracted from
     * [fireInit] as a pure post-fetch seam so each field's effect can be
     * unit-tested without spinning up an HTTP layer:
     *
     * * `enabled = false` flips `disabled` to true and emits an
     *   `AdEvent.Error(errCode = "session_disabled_by_init")` so the
     *   publisher's `onEvent` handler can react (e.g. hide the inline
     *   ad slot).
     * * `preloadTimeout` overrides [preloadTimeoutMs] when it's set
     *   AND positive; null / 0 / negative values leave the default in
     *   place. The positivity guard prevents a buggy server response
     *   from disabling preload by zeroing the timeout.
     * * `reportErrors` / `reportDebug` are applied verbatim — both
     *   flags are stable for the session's lifetime; a fresh `/init`
     *   (i.e. session recreation) is what flips them.
     *
     * Mirrors sdk-swift `Session.applyInitResult(_:)`.
     */
    internal fun applyInitResult(result: so.kontext.ads.network.dto.InitResponseDto) {
        if (!result.enabled) {
            disabled = true
            debug("Init: disabled", mapOf("reason" to "enabled=false"))
            emitEvent(AdEvent.Error(message = "Session is disabled", errCode = "session_disabled_by_init"))
        }

        val timeout = result.preloadTimeout
        if (timeout != null && timeout > 0) {
            preloadTimeoutMs = timeout.toLong()
            // Match sdk-swift's "Init: preload-timeout-updated" key — these
            // timeouts come from the /init response, so they namespace
            // under Init: regardless of which file emits them.
            debug("Init: preload-timeout-updated", mapOf("preloadTimeout" to preloadTimeoutMs))
        }

        // Apply server-controlled reporting toggles. Both are stable
        // for the session's lifetime — a fresh `/init` (i.e. session
        // recreation) is what flips them.
        reportErrors = result.reportErrors
        reportDebug = result.reportDebug
        debug(
            "Init: reporting-applied",
            mapOf(
                "reportErrors" to result.reportErrors,
                "reportDebug" to result.reportDebug,
            ),
        )
    }

    private suspend fun collectAdvertisingId() {
        try {
            // resolveId merges the publisher override (normalised — empty /
            // whitespace / zero-UUID fall back to system) with the Play
            // Services GAID. Mirrors iOS Session's resolveIds call site.
            collectedAdvertisingId = withContext(Dispatchers.IO) {
                AdvertisingIdProvider.resolveId(context, config.advertisingId)
            }
            debug("Session: gaid-collected", mapOf("hasGaid" to (collectedAdvertisingId != null)))
        } catch (e: Exception) {
            debug("Session: gaid-collection-failed", null)
            reportError(e, "session-collect-advertising-id")
        }
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Adds a message to the conversation.
     *
     * For user messages, triggers a debounced preload request. For assistant
     * messages, attaches any pending bid to the message. Outcomes (Filled /
     * NoFill / Error) flow through [events] and the `onEvent` callback —
     * `addMessage` returns no value, matching iOS.
     *
     * Preloads are debounced (see Constants.ADD_MESSAGE_DEBOUNCE_MS) — rapid
     * consecutive calls (e.g. loading conversation history in a loop) coalesce
     * into a single preload request.
     */
    public fun addMessage(message: Message, options: AddMessageOptions? = null) {
        synchronized(_messages) {
            _messages.add(message)
            while (_messages.size > Constants.MAX_MESSAGES) {
                _messages.removeAt(0)
            }
        }

        debug("Session: message-added", mapOf("role" to message.role.value, "id" to message.id))

        // Every addMessage tries to attach a pending bid — mirrors sdk-js.
        // Important: an assistant message arriving while a preload is
        // in flight runs updateBids() now (no-op if the preload hasn't
        // returned yet), and the SAME updateBids() inside
        // handlePreloadSuccess re-runs once the preload result arrives,
        // attaching the bid to this assistant message.
        updateBids()

        // Assistant / system messages don't drive preloads — exit BEFORE
        // any preload-state mutation so they can't invalidate the
        // in-flight preload's identity (the bug v4 Kotlin shipped with).
        if (message.role != Role.USER) return

        // Cancel any in-flight preload Job — including its debounce sleep
        // AND its HTTP round-trip. Rapid consecutive user messages
        // (loading conversation history in a tight loop) collapse to a
        // single /preload because each new addMessage cancels the previous
        // Job before its debounce fires. Mirrors sdk-swift's
        // `preloadTask?.cancel()` pattern and sdk-js's debounce. The
        // function's doc has promised this coalescing behaviour for a
        // while; v4 finally makes the implementation match.
        preloadJob?.cancel()

        if (disabled) {
            debug("Session: preload-skipped-disabled", null)
            return
        }

        val trackOnly = options?.trackOnly ?: false
        preloadJob = scope.launch {
            // Debounce. delay() is cancellable; if a later addMessage
            // arrives within this 10 ms window, the cancel above kicks
            // in and the rest of this block doesn't run — coalescing N
            // rapid calls into 1.
            delay(Constants.ADD_MESSAGE_DEBOUNCE_MS)

            // Early bail: skip the device / app / tcf collection altogether
            // when /init has already disabled the session by the time the
            // debounce elapses.
            if (disabled) {
                debug("Session: preload-skipped-disabled", null)
                return@launch
            }

            // Log if a previous preload is still in flight — the new one
            // will replace it via `preloadInstance = ...` below.
            // Old ads bound to earlier assistant messages stay alive: each
            // Ad owns its own WebView lifecycle via `Ad.destroy()`, so
            // clearing the shared pool here would tear down the WebViews
            // (and their OMID sessions) of ads the publisher is still
            // displaying.
            if (preloadInstance != null) {
                debug("Session: preload-instance-running", null)
            }

            val snapshot = synchronized(_messages) { _messages.toList() }
            // Production: real Context → real device/app/tcf. Tests with
            // context = null get placeholder DTOs whose required fields use
            // obviously-fake values. Both flows give Preload the
            // typed-required inputs Swift's PreloadRequestDTO expects.
            val device = context?.let { so.kontext.ads.network.collectors.DeviceCollector.collect(it) }
                ?: placeholderDeviceDto()
            val app = context?.let { so.kontext.ads.network.collectors.AppCollector.collect(it) }
                ?: so.kontext.ads.network.dto.AppDto(bundleId = "", version = "")
            val tcf = context?.let { so.kontext.kit.privacy.TCFDataProvider.collect(it) }

            // Re-check `disabled` after the collection completes. On cold
            // start, device / app / tcf collection can take several hundred
            // ms (Settings.System brightness probe, TCFDataProvider reading
            // the whole default SharedPreferences file from disk). That's
            // ample time for `applyInitResult(enabled = false)` running on
            // the Init background coroutine to flip `disabled` to true.
            // Without this final guard, the SDK ships a `/preload` for a
            // session the server has already disabled.
            if (disabled) {
                debug("Session: preload-skipped-disabled", null)
                return@launch
            }

            val preload = Preload(
                so.kontext.ads.network.PreloadParams(
                    messages = snapshot,
                    config = config,
                    device = device,
                    app = app,
                    tcf = tcf,
                    httpClient = httpClient,
                    timeoutMs = preloadTimeoutMs,
                    reportErrors = reportErrors,
                ),
            )
            preloadInstance = preload

            debug("Session: preload-start", mapOf("messageCount" to snapshot.size))

            val result = preload.requestAd(
                sessionId = sessionId,
                disabled = trackOnly,
                advertisingId = collectedAdvertisingId,
            )

            // Stale-result guard. A newer user message replaces preloadInstance
            // before its own debounce kicks off the next Preload; destroy()
            // sets it to null. Reference check is robust to assistant messages
            // arriving mid-flight (which the v4 generation-counter port got
            // wrong). Mirrors sdk-js `this.preloadInstance !== preload`.
            if (preloadInstance !== preload) return@launch

            handlePreloadResult(result, trackOnly)
        }
    }

    /**
     * Currently-active preload coroutine, if any. Internal so tests can
     * deterministically await preload completion after a non-suspend
     * [addMessage] call (`testScheduler.advanceUntilIdle()` works too
     * when Session is constructed with a `TestScope`-backed scope).
     * Production code should not touch this; it's transient and replaced
     * on every user message.
     */
    @Volatile
    internal var preloadJob: Job? = null
        private set

    private fun handlePreloadResult(result: PreloadResult, trackOnly: Boolean) {
        when (result) {
            is PreloadResult.Success -> handlePreloadSuccess(result, trackOnly)
            is PreloadResult.Failure -> handlePreloadFailure(result)
        }
    }

    private fun handlePreloadSuccess(result: PreloadResult.Success, trackOnly: Boolean) {
        // sessionId is nullable because trackOnly responses can come
        // back with an empty body (no sessionId). Only update when set.
        result.sessionId?.let { sessionId = it }
        // trackOnly: server preload is for analytics only — drop the
        // preload instance so updateBids() can't pick the bid up later
        // and skip the Filled events. Mirrors sdk-js / sdk-swift.
        if (trackOnly) {
            preloadInstance = null
            debug("Session: preload-track-only-skip-bids", mapOf("sessionId" to result.sessionId))
            return
        }
        updateBids()
        // One Filled per matching placement — Swift / sdk-js fan out
        // the same way. bidId + code on the payload let publishers
        // with multiple enabledPlacementCodes attribute each event.
        result.bids.forEach { bid ->
            emitEvent(AdEvent.Filled(bidId = bid.bidId, code = bid.code, revenue = bid.revenue))
        }
        debug(
            "Session: preload-success",
            mapOf("bidIds" to result.bids.map { it.bidId.toString() }),
        )
    }

    private fun handlePreloadFailure(result: PreloadResult.Failure) {
        if (result.disableSession) {
            disabled = true
        }
        result.event?.let { emitEvent(it) }
        debug("Session: preload-failure", mapOf("reason" to result.reason))
    }

    /**
     * Creates an Ad instance for the given message.
     * The Ad subscribes to bid updates and resolves its iframeUrl when a bid is available.
     */
    public fun createAd(messageId: String, options: AdOptions? = null): Ad {
        val code = options?.code ?: Constants.DEFAULT_PLACEMENT_CODE
        val theme = options?.theme

        // Reuse existing Ad for the same (messageId, code, theme) — this is
        // what keeps LazyColumn recycling cheap. Without this, scrolling an
        // InlineAd off-screen + back triggers a full Ad rebuild and iframe
        // reload (the InlineAd composable's DisposableEffect no longer
        // destroys, but if we created a fresh Ad here the WebView would
        // still throw away the existing render).
        val existing = ads.firstOrNull {
            it.messageId == messageId && it.code == code && it.theme == theme && !it.destroyed
        }
        if (existing != null) return existing

        // Different code or theme for the same messageId → drop the old one
        // so the publisher can swap config; otherwise we'd leak Ad objects.
        ads.filter { it.messageId == messageId }.forEach { it.destroy() }

        val ad = Ad(
            messageId = messageId,
            code = code,
            theme = theme,
            session = this,
        )
        ads.add(ad)
        return ad
    }

    /**
     * Convenience alias for [createAd].
     */
    public fun render(messageId: String, options: AdOptions? = null): Ad = createAd(messageId, options)

    /**
     * Live-updates preload-scoped configuration on this session.
     *
     * The accepted fields (`variantId`, `regulatory`, `userEmail`,
     * `advertisingId`) are read from `config` at `/preload` request time,
     * so the mutation takes effect on the next preload — no session
     * recreation needed.
     *
     * Only non-null fields are applied; fields left as `null` are
     * **not changed**. To clear a field, recreate the session.
     *
     * Auth-/server-identity fields and `character` are intentionally not
     * accepted: auth changes mid-session would desync the `/init`
     * registration, and switching `character` would leave the accumulated
     * message history targeted at the wrong persona. Recreate the
     * session for those.
     *
     * Mirrors iOS `Session.updateOptions(_:)`, minus `vendorId` — Android
     * has no `IdentifierForVendor` equivalent.
     */
    public fun updateOptions(partial: MutablePublisherOptions) {
        config = config.copy(
            variantId = partial.variantId ?: config.variantId,
            regulatory = partial.regulatory ?: config.regulatory,
            userEmail = partial.userEmail ?: config.userEmail,
            advertisingId = partial.advertisingId ?: config.advertisingId,
        )
        debug(
            "Session: options-updated",
            mapOf(
                "variantId" to partial.variantId,
                "regulatory" to partial.regulatory,
                "userEmail" to partial.userEmail,
                "advertisingId" to partial.advertisingId,
            ),
        )
    }

    /**
     * Sends a user event from the publisher app into mounted ad iframes
     * matching [code]. The code is embedded on the wire message; iframes
     * whose configured code differs ignore the event (mirrors sdk-js's
     * filter), and we also gate native-side delivery on the same code.
     *
     * @param name Strongly-typed event identifier.
     * @param payload Free-form JSON-shaped payload.
     * @param code Target placement. Defaults to [Constants.DEFAULT_PLACEMENT_CODE].
     */
    public fun sendUserEvent(
        name: UserEventName,
        payload: Map<String, Any>? = null,
        code: String = Constants.DEFAULT_PLACEMENT_CODE,
    ) {
        ads.filter { !it.destroyed && it.code == code }.forEach { ad ->
            ad.sendUserEvent(name.wireValue, payload, code)
        }
        debug(
            "Session: send-user-event",
            mapOf("name" to name.wireValue, "code" to code),
        )
    }

    /**
     * Destroys the session: cancels in-flight preloads, destroys all ads, cleans up resources.
     */
    public fun destroy() {
        // Each Ad.destroy() retires + finishes its own OmSession; the
        // shared OmManager has no per-session state to tear down (the
        // OMID native SDK stays activated process-wide, mirroring iOS).
        ads.toList().forEach { it.destroy() }
        ads.clear()
        bidUpdateListeners.clear()
        preloadInstance = null
        WebViewPool.clearAll()
        scope.cancel()
        scopeOnMain.cancel()
    }

    public override fun close(): Unit = destroy()

    // ---------------------------------------------------------------------------
    // Bid Management (internal)
    // ---------------------------------------------------------------------------

    internal fun getBid(messageId: String, code: String): Bid? {
        return assignedBids
            .firstOrNull { it.messageId == messageId }
            ?.let { ab -> ab.preload.bids.firstOrNull { it.code == code } }
    }

    private fun updateBids() {
        val preload = preloadInstance ?: return
        if (!preload.hasBid()) {
            debug("Session: no-bid", null)
            return
        }

        val lastAssistantMsg = synchronized(_messages) {
            _messages.lastOrNull { it.role == Role.ASSISTANT }
        }
        if (lastAssistantMsg == null) {
            debug("Session: no-last-message", null)
            return
        }

        if (assignedBids.any { it.messageId == lastAssistantMsg.id }) {
            debug("Session: bid-already-assigned", mapOf("messageId" to lastAssistantMsg.id))
            return
        }

        assignedBids.add(AssignedBid(preload = preload, messageId = lastAssistantMsg.id))
        debug("Session: bid-assigned", mapOf("messageId" to lastAssistantMsg.id))

        bidUpdateListeners.toList().forEach { it() }
    }

    internal fun removeAd(ad: Ad) {
        ads.remove(ad)
    }

    private fun emitEvent(event: AdEvent) {
        log("Event: $event")
        config.onEvent?.invoke(event)
        _events.tryEmit(event)
    }

    internal fun emitAdEvent(event: AdEvent) = emitEvent(event)

    /**
     * Emits a debug event.
     *
     * Local leg ([log] + `onDebugEvent` callback) always fires —
     * that's the publisher's contract. Network leg (`POST /debug`)
     * only fires when the server flipped `reportDebug = true` for
     * this user via `/init`, off by default for privacy.
     */
    internal fun debug(event: String, data: Any? = null) {
        log("$event ${data ?: ""}")
        config.onDebugEvent?.invoke(event, data)
        if (!reportDebug) return
        DebugCapture.capture(
            name = event,
            data = data,
            context = DebugContext(
                adServerUrl = config.adServerUrl,
                publisherToken = config.publisherToken,
                conversationId = config.conversationId,
                userId = config.userId,
                installId = config.installId,
                sessionId = sessionId?.toString(),
            ),
        )
    }

    private fun log(msg: String) {
        android.util.Log.d(LOG_TAG, msg)
    }

    /**
     * Reports an error to the ad server's `/error` endpoint.
     * Fire-and-forget — never throws or disrupts the SDK.
     *
     * Pass `bidId` when the failing operation is associated with a
     * resolved bid (e.g. SKAN / OM lifecycle errors inside `Ad`); it's
     * stored on the server's error log for cross-referencing. Encoded
     * to its lowercase canonical string at the wire boundary by
     * [buildErrorReportBody] so server-side log readers see a
     * consistent form across SDKs (sdk-js / sdk-swift also send
     * lowercase). Mirrors sdk-swift's `Session.reportError`.
     */
    internal fun reportError(error: Throwable, source: String? = null, bidId: java.util.UUID? = null) {
        ErrorCapture.capture(
            error = error,
            source = source,
            context = ErrorContext(
                adServerUrl = config.adServerUrl,
                publisherToken = config.publisherToken,
                conversationId = config.conversationId,
                userId = config.userId,
                installId = config.installId,
                bidId = bidId,
            ),
            reportEnabled = reportErrors,
        )
    }

    internal companion object {
        internal const val LOG_TAG = "KontextAds"
    }
}

internal data class AssignedBid(
    val preload: Preload,
    val messageId: String,
)

/**
 * Empty `DeviceDto` for the test-only path where `Session.context` is
 * null and `DeviceCollector` can't run. Required fields use
 * obviously-fake values so a wire body produced this way is recognisable
 * in logs as a synthetic test payload, not real Android data.
 */
private fun placeholderDeviceDto(): so.kontext.ads.network.dto.DeviceDto =
    so.kontext.ads.network.dto.DeviceDto(
        hardware = so.kontext.ads.network.dto.HardwareDto(
            type = so.kontext.ads.network.dto.HardwareType.OTHER,
            brand = "",
            model = "",
            bootTime = 0L,
            sdCardAvailable = false,
        ),
        os = so.kontext.ads.network.dto.OsDto(
            name = "android",
            version = "",
            locale = "",
            timezone = "",
        ),
        screen = so.kontext.ads.network.dto.ScreenDto(
            width = 0,
            height = 0,
            dpr = 1.0,
            darkMode = false,
            orientation = so.kontext.ads.network.dto.ScreenOrientation.PORTRAIT,
            brightness = 0.0,
        ),
        power = so.kontext.ads.network.dto.PowerDto(
            lowPowerMode = false,
            batteryState = so.kontext.ads.network.dto.BatteryState.UNKNOWN,
        ),
        audio = so.kontext.ads.network.dto.AudioDto(
            volume = 0,
            muted = true,
            outputPluggedIn = false,
            outputType = emptyList(),
        ),
    )

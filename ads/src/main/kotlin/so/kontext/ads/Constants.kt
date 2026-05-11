package so.kontext.ads

/**
 * SDK-wide defaults and identifiers.
 *
 * Mirrors `sdk-js/src/constants.ts` and sdk-swift's `Constants.swift` so
 * cross-SDK behaviour stays aligned. OMID partner identity lives here too —
 * `OMID_PARTNER_NAME` is shared across all Kontext SDKs (it identifies the
 * company), but `OMID_PARTNER_VERSION` is per-SDK because each SDK has its
 * own externally-registered IAB certification version.
 *
 * Time-valued constants are named in milliseconds (matching sdk-js naming).
 */
internal object Constants {

    // Server

    /** Production ad-server base URL. */
    const val DEFAULT_AD_SERVER_URL: String = "https://server.megabrain.co"

    /**
     * Default placement code used when the publisher omits
     * `enabledPlacementCodes` or doesn't pass `code` to `Session.createAd()`.
     */
    const val DEFAULT_PLACEMENT_CODE: String = "inlineAd"

    // Session lifecycle

    /**
     * Maximum number of messages retained in a session. Older messages are
     * trimmed; matches iOS so cross-platform conversations cap at the same point.
     */
    const val MAX_MESSAGES: Int = 30

    /**
     * `Session.addMessage` debounce window. Rapid consecutive calls (e.g.
     * loading conversation history in a loop) coalesce into a single preload
     * request.
     */
    const val ADD_MESSAGE_DEBOUNCE_MS: Long = 10L

    // Network

    /**
     * Default `/preload` request timeout. Overridable per-session by the
     * `preloadTimeout` field of the `/init` response.
     */
    const val DEFAULT_PRELOAD_TIMEOUT_MS: Long = 16_000L

    /** `/init` request timeout. */
    const val INIT_TIMEOUT_MS: Long = 16_000L

    /**
     * `/error` request timeout. Shorter than [INIT_TIMEOUT_MS] /
     * [DEFAULT_PRELOAD_TIMEOUT_MS] because error reporting is fire-and-forget
     * on a daemon thread — if 100 errors fire on a slow network we don't want
     * 100 threads holding sockets open for 16s each. Loss of a single error
     * report is acceptable; daemon-thread accumulation isn't.
     */
    const val ERROR_REPORT_TIMEOUT_MS: Long = 5_000L

    // UI / iframe

    /**
     * Default modal auto-close timeout. Overridable per-creative via the
     * `timeout` field of the `open-component-iframe` payload.
     *
     * Typed as `Int` (not `Long` like the network timeouts) because the
     * value is bounded by its wire format: the iframe payload's `timeout`
     * field is a JSON integer, and downstream the value rides through
     * `Intent.putExtra(key, Int)` into `InterstitialAdActivity`. Matches
     * sdk-swift's `Constants.defaultModalTimeoutMs: Int` for the same
     * reason. Network timeouts (`INIT_TIMEOUT_MS`, `DEFAULT_PRELOAD_TIMEOUT_MS`,
     * `ERROR_REPORT_TIMEOUT_MS`) stay `Long` because they feed
     * `kotlinx.coroutines.delay` and `Thread.sleep`, both of which take Long.
     */
    const val DEFAULT_MODAL_TIMEOUT_MS: Int = 5_000

    /**
     * Interval at which the SDK reports container dimensions (and viewport
     * position / keyboard height) to the ad iframe.
     */
    const val DIMENSION_REPORT_INTERVAL_MS: Long = 200L

    // OMID

    /** Partner name registered with IAB Tech Lab. Same across all Kontext SDKs. */
    const val OMID_PARTNER_NAME: String = "Kontextso"

    /**
     * sdk-kotlin's OMID-implementation version. Bumped only as part of a
     * coordinated IAB certification update. Independent of the SDK release version.
     */
    const val OMID_PARTNER_VERSION: String = "1.0.0"
}

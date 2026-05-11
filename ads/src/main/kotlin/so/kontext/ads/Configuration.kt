package so.kontext.ads

import so.kontext.ads.model.AdEventHandler
import so.kontext.ads.model.Character
import so.kontext.ads.model.DebugEventHandler
import so.kontext.ads.model.Regulatory
import so.kontext.ads.model.SessionOptions

/**
 * `SessionOptions` validated + normalized into the on-the-wire shape held
 * by `Session` for the lifetime of the session.
 *
 * Identity / server-binding fields (`publisherToken`, `userId`,
 * `conversationId`, `adServerUrl`, `enabledPlacementCodes`, callbacks) are
 * fixed at construction — changing them mid-session would desync the
 * `/init` registration.
 *
 * Preload-scoped fields (`character`, `variantId`, `regulatory`,
 * `userEmail`, `advertisingId`) can be replaced via
 * `Session.updateOptions(...)`, which swaps the whole `ResolvedConfig`
 * via `data class` `copy(...)`. Preload reads `config.X` at request time,
 * so the next `/preload` picks up the new values.
 *
 * Kept `internal` because there's no public read accessor on `Session`;
 * publishers configure via `SessionOptions` and update via
 * `MutablePublisherOptions`.
 */
internal data class ResolvedConfig(
    val publisherToken: String,
    val userId: String,
    val conversationId: String,
    val enabledPlacementCodes: List<String>,
    val adServerUrl: String,
    val character: Character?,
    val variantId: String?,
    val regulatory: Regulatory?,
    val userEmail: String?,
    val advertisingId: String?,
    val onEvent: AdEventHandler?,
    val onDebugEvent: DebugEventHandler?,
)

/**
 * Resolves raw publisher options into a [ResolvedConfig]. The single
 * place defaults are applied — mirrors sdk-js + sdk-swift, which both
 * default at session-creation rather than at field capture:
 *
 * * `enabledPlacementCodes` — `null` or empty falls back to
 *   `[Constants.DEFAULT_PLACEMENT_CODE]`. The empty-list case is
 *   defensive: a publisher accidentally passing `emptyList()` shouldn't
 *   silently disable all placements.
 * * `adServerUrl` — `null` falls back to `Constants.DEFAULT_AD_SERVER_URL`.
 *
 * Callers: `KontextAds.createSession` only.
 */
internal fun resolveConfig(options: SessionOptions): ResolvedConfig = ResolvedConfig(
    publisherToken = options.publisherToken,
    userId = options.userId,
    conversationId = options.conversationId,
    enabledPlacementCodes = options.enabledPlacementCodes
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(Constants.DEFAULT_PLACEMENT_CODE),
    adServerUrl = options.adServerUrl ?: Constants.DEFAULT_AD_SERVER_URL,
    character = options.character,
    variantId = options.variantId,
    regulatory = options.regulatory,
    userEmail = options.userEmail,
    advertisingId = options.advertisingId,
    onEvent = options.onEvent,
    onDebugEvent = options.onDebugEvent,
)

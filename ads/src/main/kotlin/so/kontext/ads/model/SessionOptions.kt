package so.kontext.ads.model

/**
 * Publisher-facing configuration passed to `KontextAds.createSession()`.
 *
 * `publisherToken` is required — issued by Kontext per publisher app.
 * `userId` and `conversationId` are publisher-controlled strings that
 * scope frequency capping + dedup; both must be stable across an entire
 * conversation but unique across users / conversations.
 *
 * `enabledPlacementCodes` and `adServerUrl` are nullable inputs — the
 * publisher can pass `null` (or omit them) and defaults are applied at
 * the resolution boundary inside `resolveConfig`, mirroring sdk-swift +
 * sdk-js. Eager defaults on the data class would obscure the
 * "publisher didn't specify" state.
 *
 * Mirrors iOS `SessionOptions` (`KontextSwiftSDK/Model/SessionOptions.swift`).
 */
public data class SessionOptions(
    val publisherToken: String,
    val userId: String,
    val conversationId: String,
    val enabledPlacementCodes: List<String>? = null,
    val adServerUrl: String? = null,
    val character: Character? = null,
    val variantId: String? = null,
    val regulatory: Regulatory? = null,
    val userEmail: String? = null,
    val advertisingId: String? = null,
    val onEvent: AdEventHandler? = null,
    val onDebugEvent: DebugEventHandler? = null,
)

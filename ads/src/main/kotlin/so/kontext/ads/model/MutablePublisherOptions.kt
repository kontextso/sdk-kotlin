package so.kontext.ads.model

/**
 * Subset of [SessionOptions] that can be live-updated on an active session
 * via `Session.updateOptions(...)`.
 *
 * These fields are read from `session.config` at `/preload` request time,
 * so an update propagates automatically on the next preload — no session
 * recreation needed.
 *
 * **Excluded from live-update:**
 * - Auth/server-identity fields (`publisherToken`, `userId`, `conversationId`,
 *   `adServerUrl`, `enabledPlacementCodes`) — changing them mid-session
 *   would desync the existing `/init` registration from subsequent requests.
 * - `character` — the conversation history accumulated in the session
 *   belongs to the original character; swapping mid-session leaves
 *   messages targeted at the wrong persona. **Recreate the session to
 *   switch character.**
 *
 * Semantics: every non-null field overwrites the corresponding value on
 * `session.config`. Fields left as `null` are **not changed** — to
 * distinguish "not provided" from "clear to null", recreate the session.
 *
 * Mirrors iOS `MutablePublisherOptions`
 * (`KontextSwiftSDK/Model/MutablePublisherOptions.swift`), minus
 * `vendorId` — Android has no `IdentifierForVendor` equivalent.
 */
public data class MutablePublisherOptions(
    val variantId: String? = null,
    val regulatory: Regulatory? = null,
    val userEmail: String? = null,
    val advertisingId: String? = null,
)

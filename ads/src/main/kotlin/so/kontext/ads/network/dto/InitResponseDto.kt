package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Response from `POST /init`. Mirrors iOS
 * `Networking/DTO/InitResponseDTO.swift`.
 *
 * `enabled = false` disables the session at startup (typically a
 * geo-restriction or publisher-token outage); the client still keeps
 * the Session alive so subsequent retries can re-enable it. `enabled`
 * is non-null and defaults to `true` when the server omits the key —
 * mirrors sdk-swift's tolerant decode (only explicit `enabled: false`
 * disables a session). `preloadTimeout` (ms) overrides the default
 * 16 s preload deadline if the server wants to tighten / loosen it
 * for this publisher.
 *
 * `reportErrors` and `reportDebug` are server-controlled toggles for
 * the two telemetry network legs:
 *
 *  - `reportErrors` (default `true`): kill switch for `/error` POSTs.
 *    Local error logging (`Log.e`) always runs; this only gates the
 *    network leg so the server can suppress a feedback-loop firehose
 *    per-user.
 *  - `reportDebug` (default `false`): opt-in for `/debug` forwarding.
 *    Publisher's `onDebugEvent` callback always fires; when `true`,
 *    the SDK additionally POSTs each debug event to `/debug`.
 *    Default-off for privacy — the server flips it on per-userId
 *    when actively diagnosing.
 */
@Serializable
internal data class InitResponseDto(
    val enabled: Boolean = true,
    val preloadTimeout: Int? = null,
    val reportErrors: Boolean = true,
    val reportDebug: Boolean = false,
)

package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * SDK build identity — `name`, `platform`, `version`. Shared across
 * `/preload`, `/init`, and `/error` request bodies; populated from the
 * compile-time constants in [so.kontext.ads.SDKInfo].
 *
 * `name` and `platform` are typed as `String` (not enums) because
 * sdk-kotlin only ever emits `"sdk-kotlin"` and `"android"` —
 * modelling the server's other 7 / 4 enum values would add noise here
 * without value. The constants live in `SDKInfo`; tests pin the wire
 * spelling.
 *
 * Mirrors iOS `SDKDTO` (`Networking/DTO/SDKDTO.swift`).
 */
@Serializable
internal data class SdkDto(
    val name: String,
    val platform: String,
    val version: String,
)

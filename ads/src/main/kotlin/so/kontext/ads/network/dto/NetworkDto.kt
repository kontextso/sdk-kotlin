package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Network connectivity information. Server treats every field as
 * optional; Android's `NetworkInfoProvider` always classifies `type`
 * (falling back to [NetworkType.OTHER] when the connection is unknown)
 * but the rest can be missing — Wi-Fi has no `carrier`, `userAgent`
 * requires a WebView eval that can fail, and `detail` is only present
 * on cellular.
 *
 * Mirrors iOS `NetworkDTO` (`Networking/DTO/NetworkDTO.swift`).
 */
@Serializable
internal data class NetworkDto(
    val type: NetworkType,
    val carrier: String? = null,
    val detail: String? = null,
    val userAgent: String? = null,
)

package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape for the `regulatory` block in the `/preload` request body.
 * The domain counterpart is `so.kontext.ads.model.Regulatory`; pure
 * conversion goes through its `toDto()` extension.
 *
 * `Preload.mergeRegulatory(...)` overlays on-device TCF data
 * (`gdpr` / `gdprConsent`) on top of the publisher-supplied `Regulatory`,
 * collapsing to `null` when every field is empty so the wire payload
 * stays minimal.
 *
 * Mirrors iOS `RegulatoryDTO` (`Networking/DTO/PreloadRequestDTO.swift`).
 * Swift uses a mutable `var` struct that gets the TCF overlay applied
 * post-construction; Kotlin builds a merged `Regulatory` from immutable
 * values and calls `toDto()` once.
 */
@Serializable
internal data class RegulatoryDto(
    val gdpr: Int? = null,
    val gdprConsent: String? = null,
    val coppa: Int? = null,
    val gpp: String? = null,
    val gppSid: List<Int>? = null,
    val usPrivacy: String? = null,
)

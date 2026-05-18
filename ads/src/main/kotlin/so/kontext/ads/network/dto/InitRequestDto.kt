package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * JSON body sent to `POST /init` for per-publisher configuration.
 *
 * Narrower than [PreloadRequestDto] — `/init` only needs publisher
 * identity, SDK build, and app bundle metadata.
 *
 * Mirrors iOS `InitRequestDTO` (`Networking/DTO/InitRequestDTO.swift`),
 * minus `skan` — SKAdNetwork is Apple-only and Android has no
 * equivalent attribution framework.
 *
 * `app` is required (matches Swift). The caller computes [AppMetadata]
 * from its Android `Context`; test paths supply a fixed fake.
 */
@Serializable
internal data class InitRequestDto(
    val publisherToken: String,
    val userId: String,
    val installId: String,
    val sdk: SdkDto,
    val app: AppMetadata,
) {
    /**
     * Minimal app metadata for `/init` — strictly narrower than [AppDto]
     * (which also carries install / update / start times for `/preload`
     * targeting).
     */
    @Serializable
    internal data class AppMetadata(
        val bundleId: String,
        val version: String,
    )
}

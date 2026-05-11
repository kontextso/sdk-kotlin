package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Host-app snapshot (package id, version, install timestamps) embedded
 * in `PreloadRequestDto.app`. Built from `AppCollector.collect(...)` —
 * a thin passthrough over KontextKit's `AppInfoProvider`, which is the
 * single source of truth for both bundleId/version and the `PackageInfo`
 * timestamps. Used only by `/preload`; `/init` carries a narrower shape
 * via `InitRequestDto.AppMetadata` (no install / update / start
 * timestamps).
 *
 * `lastUpdateTime` is always nil on iOS — there's no public API for
 * "last app update time"; the field exists for cross-platform parity
 * (sdk-kotlin populates it from `PackageInfo.lastUpdateTime`).
 *
 * Mirrors iOS `AppDTO` (`Networking/DTO/AppDTO.swift`).
 */
@Serializable
internal data class AppDto(
    val bundleId: String,
    val version: String,
    val firstInstallTime: Long? = null,
    val lastUpdateTime: Long? = null,
    val startTime: Long? = null,
)

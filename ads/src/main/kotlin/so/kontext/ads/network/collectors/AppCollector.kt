package so.kontext.ads.network.collectors

import android.content.Context
import so.kontext.ads.network.dto.AppDto
import so.kontext.kit.deviceinfo.AppInfoProvider

/**
 * Builds the [AppDto] snapshot embedded in `PreloadRequestDto.app`.
 * Pure passthrough — every field comes from KontextKit's
 * [AppInfoProvider] so the cross-platform definitions can't drift
 * between sdk-kotlin and sdk-swift. Mirrors iOS
 * `Networking/Collectors/AppCollector.swift`.
 */
internal object AppCollector {

    fun collect(context: Context): AppDto {
        val info = AppInfoProvider.collect(context)
        return AppDto(
            bundleId = info.bundleId,
            version = info.version,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            startTime = AppInfoProvider.processStartMs,
        )
    }
}

package so.kontext.ads.internal.data.mapper

import so.kontext.ads.internal.data.dto.request.AppDto
import so.kontext.ads.internal.utils.deviceinfo.AppInfo

internal fun AppInfo.toDto(): AppDto {
    return AppDto(
        bundleId = appBundleId,
        version = appVersion,
        storeUrl = appStoreUrl,
        firstInstallTime = firstInstallTime,
        lastUpdateTime = lastUpdateTime,
        startTime = startTime,
    )
}

package so.kontext.ads.internal.data.mapper

import so.kontext.ads.internal.data.dto.request.DeviceInfoDto
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo

internal fun DeviceInfo.toDto(): DeviceInfoDto {
    return DeviceInfoDto(
        os = os,
        systemVersion = systemVersion,
        model = model,
        brand = brand,
        deviceType = deviceType,
        appBundleId = appBundleId,
        appVersion = appVersion,
        soundOn = soundOn,
        appStoreUrl = appStoreUrl,
    )
}

package com.kontext.ads.internal.data.mapper

import com.kontext.ads.domain.DeviceInfo
import com.kontext.ads.internal.data.dto.request.DeviceInfoDto

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

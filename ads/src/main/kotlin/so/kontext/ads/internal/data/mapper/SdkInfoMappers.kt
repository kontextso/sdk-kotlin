package so.kontext.ads.internal.data.mapper

import so.kontext.ads.internal.data.dto.request.SdkDto
import so.kontext.ads.internal.utils.deviceinfo.SdkInfo

internal fun SdkInfo.toDto(): SdkDto {
    return SdkDto(
        name = sdkName,
        version = sdkVersion,
        platform = sdkPlatform,
    )
}

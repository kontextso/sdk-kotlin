package so.kontext.ads.internal.data.mapper

import so.kontext.ads.internal.data.dto.request.AudioDto
import so.kontext.ads.internal.data.dto.request.DeviceDto
import so.kontext.ads.internal.data.dto.request.HardwareDto
import so.kontext.ads.internal.data.dto.request.NetworkDto
import so.kontext.ads.internal.data.dto.request.OsDto
import so.kontext.ads.internal.data.dto.request.PowerDto
import so.kontext.ads.internal.data.dto.request.ScreenDto
import so.kontext.ads.internal.utils.deviceinfo.AudioInfo
import so.kontext.ads.internal.utils.deviceinfo.AudioOutputType
import so.kontext.ads.internal.utils.deviceinfo.BatteryState
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import so.kontext.ads.internal.utils.deviceinfo.DeviceType
import so.kontext.ads.internal.utils.deviceinfo.HardwareInfo
import so.kontext.ads.internal.utils.deviceinfo.NetworkDetailType
import so.kontext.ads.internal.utils.deviceinfo.NetworkInfo
import so.kontext.ads.internal.utils.deviceinfo.NetworkType
import so.kontext.ads.internal.utils.deviceinfo.OsInfo
import so.kontext.ads.internal.utils.deviceinfo.PowerInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenInfo
import so.kontext.ads.internal.utils.deviceinfo.ScreenOrientation

internal fun DeviceInfo.toDto(): DeviceDto {
    return DeviceDto(
        os = osInfo.toDto(),
        hardware = hardwareInfo.toDto(),
        screen = screenInfo.toDto(),
        power = powerInfo.toDto(),
        audio = audioInfo.toDto(),
        network = networkInfo.toDto(),
    )
}

internal fun OsInfo.toDto(): OsDto {
    return OsDto(
        name = osName,
        version = osVersion,
        locale = locale,
        timezone = timezone,
    )
}

internal fun HardwareInfo.toDto(): HardwareDto {
    return HardwareDto(
        brand = brand,
        model = model,
        deviceType = deviceType.toDto(),
        bootTime = bootTime,
        sdCardAvailable = sdCardAvailable,
    )
}

internal fun ScreenInfo.toDto(): ScreenDto {
    return ScreenDto(
        width = screenWidth,
        height = screenHeight,
        dpr = dpr,
        orientation = screenOrientation.toDto(),
        darkMode = isDarkMode,
    )
}

internal fun PowerInfo.toDto(): PowerDto {
    return PowerDto(
        batteryLevel = batteryLevel,
        batteryState = batteryState.toDto(),
        lowPowerMode = isLowPowerMode,
    )
}

internal fun AudioInfo.toDto(): AudioDto {
    return AudioDto(
        volume = volume,
        muted = isMuted,
        outputPluggedIn = isAudioOutputPluggedIn,
        outputType = audioOutputTypes.map { it.toDto() },
    )
}

internal fun NetworkInfo.toDto(): NetworkDto {
    return NetworkDto(
        userAgent = userAgent,
        type = networkType?.toDto(),
        detail = networkDetail?.toDto(),
        carrier = carrier,
    )
}

internal fun DeviceType.toDto(): String {
    return when (this) {
        DeviceType.Handset -> "handset"
        DeviceType.Tablet -> "tablet"
        DeviceType.Tv -> "tv"
    }
}

internal fun ScreenOrientation.toDto(): String {
    return when (this) {
        ScreenOrientation.Portrait -> "portrait"
        ScreenOrientation.Landscape -> "landscape"
    }
}

internal fun BatteryState.toDto(): String {
    return when (this) {
        BatteryState.Charging -> "charging"
        BatteryState.Full -> "full"
        BatteryState.Unplugged -> "unplugged"
        BatteryState.Unknown -> "unknown"
    }
}

internal fun AudioOutputType.toDto(): String {
    return when (this) {
        AudioOutputType.Wired -> "wired"
        AudioOutputType.Bluetooth -> "bluetooth"
        AudioOutputType.Hdmi -> "hdmi"
        AudioOutputType.Usb -> "usb"
        AudioOutputType.Other -> "other"
    }
}

internal fun NetworkType.toDto(): String {
    return when (this) {
        NetworkType.Wifi -> "wifi"
        NetworkType.Cellular -> "cellular"
        NetworkType.Ethernet -> "ethernet"
        NetworkType.Other -> "other"
    }
}

internal fun NetworkDetailType.toDto(): String {
    return when (this) {
        NetworkDetailType.Gprs -> "gprs"
        NetworkDetailType.Edge -> "edge"
        NetworkDetailType.TwoG -> "2g"
        NetworkDetailType.Three3 -> "3g"
        NetworkDetailType.Hspa -> "hspa"
        NetworkDetailType.FourG -> "4g"
        NetworkDetailType.FiveG -> "5g"
        NetworkDetailType.Cellular -> "cellular"
    }
}

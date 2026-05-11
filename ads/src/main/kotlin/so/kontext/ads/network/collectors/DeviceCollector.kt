package so.kontext.ads.network.collectors

import android.content.Context
import so.kontext.ads.network.dto.AudioDto
import so.kontext.ads.network.dto.AudioOutputType
import so.kontext.ads.network.dto.BatteryState
import so.kontext.ads.network.dto.DeviceDto
import so.kontext.ads.network.dto.HardwareDto
import so.kontext.ads.network.dto.HardwareType
import so.kontext.ads.network.dto.NetworkDto
import so.kontext.ads.network.dto.NetworkType
import so.kontext.ads.network.dto.OsDto
import so.kontext.ads.network.dto.PowerDto
import so.kontext.ads.network.dto.ScreenDto
import so.kontext.ads.network.dto.ScreenOrientation
import so.kontext.kit.deviceinfo.AudioInfoProvider
import so.kontext.kit.deviceinfo.BatteryInfoProvider
import so.kontext.kit.deviceinfo.HardwareInfoProvider
import so.kontext.kit.deviceinfo.NetworkInfoProvider
import so.kontext.kit.deviceinfo.OSInfoProvider
import so.kontext.kit.deviceinfo.ScreenInfoProvider

/**
 * Builds the `DeviceDto` snapshot embedded in `PreloadRequestDto.device`.
 * Pure orchestration — every actual measurement happens in a KontextKit
 * provider (`OSInfoProvider`, `HardwareInfoProvider`, etc.). KontextKit
 * emits stringly-typed enum-shaped fields; we map them to the typed
 * DTO enums at this boundary so the rest of the SDK works with
 * compile-time-checked values.
 *
 * Mirrors iOS `Networking/Collectors/DeviceCollector.swift`.
 *
 * Split from `AppCollector` because the device payload is rebuilt on
 * every preload (battery / audio / network state change in real time)
 * while the app payload is essentially static — keeping them separate
 * lets us cache the app side independently if that ever becomes a
 * concern.
 */
internal object DeviceCollector {

    fun collect(context: Context): DeviceDto {
        val os = OSInfoProvider.collect(context)
        val hardware = HardwareInfoProvider.collect(context)
        val screen = ScreenInfoProvider.collect(context)
        val battery = BatteryInfoProvider.collect(context)
        val audio = AudioInfoProvider.collect(context)
        val network = NetworkInfoProvider.collect(context)

        return DeviceDto(
            hardware = HardwareDto(
                // Fallback location matches sdk-swift's
                // `HardwareType(rawValue: hw.type) ?? .other` — call-site
                // chooses the catch-all, not the parser.
                type = HardwareType.fromString(hardware.type) ?: HardwareType.OTHER,
                brand = hardware.brand,
                model = hardware.model,
                bootTime = hardware.bootTime,
                sdCardAvailable = hardware.sdCardAvailable,
            ),
            os = OsDto(
                name = os.name,
                version = os.version,
                locale = os.locale,
                timezone = os.timezone,
            ),
            screen = ScreenDto(
                width = screen.width,
                height = screen.height,
                dpr = screen.dpr,
                darkMode = screen.darkMode,
                orientation = ScreenOrientation.fromString(screen.orientation) ?: ScreenOrientation.PORTRAIT,
                brightness = screen.brightness,
            ),
            power = PowerDto(
                lowPowerMode = battery.lowPowerMode,
                batteryState = BatteryState.fromString(battery.batteryState) ?: BatteryState.UNKNOWN,
                batteryLevel = battery.batteryLevel,
            ),
            audio = AudioDto(
                volume = audio.volume,
                muted = audio.muted,
                outputPluggedIn = audio.outputPluggedIn,
                // Drop wire-format strings KontextKit emits that aren't in
                // the AudioOutputType enum — matches sdk-swift's behaviour.
                outputType = audio.outputType.mapNotNull { AudioOutputType.fromString(it) },
            ),
            network = NetworkDto(
                type = NetworkType.fromString(network.type) ?: NetworkType.OTHER,
                carrier = network.carrier,
                detail = network.detail,
                userAgent = network.userAgent,
            ),
        )
    }
}

package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Device snapshot embedded in `PreloadRequestDto.device`. Composed by
 * `DeviceCollector.collect(...)` from the kit's per-domain providers
 * (OS / hardware / screen / battery / audio / network).
 *
 * Server's `deviceSchema` marks `audio` / `network` / `power` as
 * optional, but KontextKit's providers always emit them on Android —
 * keeping `audio` and `power` required documents the platform contract
 * without changing the wire output (matches sdk-swift's stance).
 * `network` stays nullable for parity with sdk-swift, where the sync
 * `DeviceCollector.collect()` path deliberately omits network to skip
 * the 100ms `NWPathMonitor` wait; production preload always uses the
 * async path so the wire output always has network in practice.
 *
 * Mirrors iOS `DeviceDTO` (`Networking/DTO/DeviceDTO.swift`).
 */
@Serializable
internal data class DeviceDto(
    val hardware: HardwareDto,
    val os: OsDto,
    val screen: ScreenDto,
    val power: PowerDto,
    val audio: AudioDto,
    val network: NetworkDto? = null,
)

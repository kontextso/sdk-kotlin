package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Hardware characteristics of the end-user device. Server requires
 * `type`; the rest is optional on the wire but always populated by
 * KontextKit's `HardwareInfoProvider` on Android. `brand` / `model`
 * are non-nullable to match Swift's `HardwareDTO` and the Android
 * provider contract. `bootTime` is epoch ms at which the OS booted.
 * `sdCardAvailable` is Android-specific (no SD card concept on iOS,
 * so Swift's `HardwareDTO` omits it).
 *
 * Mirrors iOS `HardwareDTO` (`Networking/DTO/HardwareDTO.swift`).
 */
@Serializable
internal data class HardwareDto(
    val type: HardwareType,
    val brand: String,
    val model: String,
    val bootTime: Long,
    val sdCardAvailable: Boolean,
)

package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Power / battery state. Server treats every field as optional, but
 * KontextKit's providers always emit `lowPowerMode` and `batteryState`
 * — `batteryState` falls back to [BatteryState.UNKNOWN] when the
 * underlying status is indeterminate, never to nothing. Only
 * `batteryLevel` is honestly nullable: stripped-down devices without
 * `BatteryManager` (some TV sticks / IoT) and the iOS simulator
 * legitimately have no battery to report.
 *
 * `batteryLevel` is a percentage (0–100), matching the server's
 * `batteryLevel` describe.
 *
 * Mirrors iOS `PowerDTO` (`Networking/DTO/PowerDTO.swift`).
 */
@Serializable
internal data class PowerDto(
    val lowPowerMode: Boolean,
    val batteryState: BatteryState,
    val batteryLevel: Double? = null,
)

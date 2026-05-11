package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Screen and display information. All fields are required — both
 * KontextKit platforms (Android `ScreenInfoProvider` + `BrightnessManager`,
 * iOS `ScreenInfoProvider`) always provide a value. `orientation` falls
 * back to portrait if unknown, `brightness` falls back to 50% when
 * `SCREEN_BRIGHTNESS` is unreadable.
 *
 * `width` and `height` are reported in **physical pixels** (CSS pixels
 * × `dpr`) so the ad server receives resolution-independent dimensions.
 *
 * `brightness` is **0–100** to match the convention used by
 * `audio.volume` and `power.batteryLevel`. iOS normalises its native
 * `UIScreen.main.brightness` (0–1) by multiplying at the
 * `BrightnessManager` boundary.
 *
 * Mirrors iOS `ScreenDTO` (`Networking/DTO/ScreenDTO.swift`).
 */
@Serializable
internal data class ScreenDto(
    val width: Int,
    val height: Int,
    val dpr: Double,
    val darkMode: Boolean,
    val orientation: ScreenOrientation,
    val brightness: Double,
)

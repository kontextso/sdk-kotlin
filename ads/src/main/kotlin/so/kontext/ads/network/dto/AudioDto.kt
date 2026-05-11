package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Audio output state. Server treats every field as optional, but
 * Android's `AudioInfoProvider` always provides all of them — keeping
 * them required documents the platform contract (matches sdk-swift's
 * "iOS always provides these values" stance).
 *
 * `volume` is 0–100.
 *
 * `outputPluggedIn` reports **external-output presence only** — the
 * built-in speaker is excluded. The server's `audioSchema` description
 * (`"Whether ANY audio output is connected"`) reflects v3's wire
 * meaning, when both SDKs counted built-in speakers and the field was
 * therefore always `true`. v4 (this SDK + sdk-swift) report the more
 * useful "external output present" signal so an empty `outputType`
 * always pairs with `outputPluggedIn = false`. Server-schema doc is
 * stale — the actual signal mobile SDKs send is "external only".
 *
 * `outputType` is the list of currently-connected external outputs —
 * empty when only the built-in speaker is active.
 *
 * Mirrors iOS `AudioDTO` (`Networking/DTO/AudioDTO.swift`).
 */
@Serializable
internal data class AudioDto(
    val volume: Int,
    val muted: Boolean,
    val outputPluggedIn: Boolean,
    val outputType: List<AudioOutputType>,
)

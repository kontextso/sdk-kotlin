package so.kontext.ads.model

import so.kontext.ads.network.dto.CharacterDto
import java.net.URI

/**
 * Optional character / persona description forwarded to the ad server.
 * Used for character-themed creative selection (e.g. an NSFW persona
 * gets filtered creatives, a child-safe persona gets COPPA-safe ones).
 *
 * `id` is publisher-controlled and must be stable across sessions for
 * the same character — the server uses it for frequency capping.
 *
 * `avatarUrl` is required — publishers without a real avatar should
 * supply a stable placeholder URI rather than a sentinel value. The
 * server uses the URI host as a frequency-cap signal alongside `id`.
 * Typed as [java.net.URI] (not `String`) so malformed URLs fail at
 * construction. `URI` is preferred over `java.net.URL` (whose `equals`
 * does DNS resolution) and over `android.net.Uri` (which requires
 * Robolectric for unit tests). Wire encoding goes through
 * [CharacterDto], where the URI is serialized via `toString()`.
 *
 * Mirrors iOS `Character` (`KontextSwiftSDK/Model/Character.swift`).
 */
public data class Character(
    val id: String,
    val name: String,
    val avatarUrl: URI,
    val greeting: String? = null,
    val persona: String? = null,
    val tags: List<String>? = null,
    val isNsfw: Boolean? = null,
)

internal fun Character.toDto(): CharacterDto = CharacterDto(
    id = id,
    name = name,
    avatarUrl = avatarUrl.toString(),
    greeting = greeting,
    persona = persona,
    tags = tags,
    isNsfw = isNsfw,
)

package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Operating-system metadata. All four fields are required by the
 * server's `osSchema` and always known on Android (`OSInfoProvider`
 * never returns null for any of them).
 *
 * `name` is the lowercase platform identifier (`"android"`), matching
 * the server's `osSchema` example and the SDK's own `sdk.platform`.
 * `locale` is a BCP-47 tag (e.g. `"en-US"`), not POSIX (`"en_US"`).
 * `timezone` is an IANA identifier.
 *
 * Mirrors iOS `OSDTO` (`Networking/DTO/OSDTO.swift`).
 */
@Serializable
internal data class OsDto(
    val name: String,
    val version: String,
    val locale: String,
    val timezone: String,
)

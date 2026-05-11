package so.kontext.ads.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * kotlinx.serialization adapter for [java.util.UUID]. Encodes via the
 * canonical string form (`toString()`); decodes via `UUID.fromString()`,
 * which throws `IllegalArgumentException` on malformed input — matches
 * sdk-swift's strict UUID decode semantics.
 *
 * Used at the field level on DTOs that mirror Swift's `UUID?` typing
 * (e.g. `PreloadRequestDto.sessionId`).
 */
internal object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

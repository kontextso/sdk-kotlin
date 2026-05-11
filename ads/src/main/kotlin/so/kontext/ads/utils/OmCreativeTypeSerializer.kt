package so.kontext.ads.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import so.kontext.kit.omsdk.OmCreativeType

/**
 * kotlinx.serialization adapter for [OmCreativeType]. The enum lives in
 * KontextKit (which doesn't depend on kotlinx.serialization), so we
 * keep the wire-format adapter here in sdk-kotlin instead of annotating
 * the enum.
 *
 * Encodes via `OmCreativeType.wireValue` (lowercase `"display"` /
 * `"video"`); decodes via `OmCreativeType.fromString`, which returns
 * `null` for unknown values — matches sdk-swift's tolerant decode of
 * `OMCreativeType?` (`try? c.decode` swallows unknown enum values to
 * `nil` so server-side additions don't break old SDKs).
 *
 * Implemented as a `KSerializer<OmCreativeType?>` so the unknown-value
 * coercion happens inside the serializer rather than relying on
 * `Json { coerceInputValues = true }` (which only kicks in for the
 * built-in enum serializer, not custom adapters).
 *
 * Used at the field level on `BidDto.creativeType`.
 */
internal object OmCreativeTypeSerializer : KSerializer<OmCreativeType?> {
    override val descriptor: SerialDescriptor = String.serializer().nullable.descriptor

    override fun serialize(encoder: Encoder, value: OmCreativeType?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.wireValue)
    }

    override fun deserialize(decoder: Decoder): OmCreativeType? {
        return if (decoder.decodeNotNullMark()) {
            OmCreativeType.fromString(decoder.decodeString())
        } else {
            decoder.decodeNull()
        }
    }
}

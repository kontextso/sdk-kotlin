package so.kontext.ads.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.kit.omsdk.OmCreativeType

/**
 * Pins the wire contract of [OmCreativeTypeSerializer]: encode via
 * `wireValue` (`display`/`video`), decode via `OmCreativeType.fromString`
 * with unknown values tolerantly coerced to `null` (so server-side enum
 * additions don't break old SDKs), and explicit JSON-null handling.
 */
class OmCreativeTypeSerializerTest {

    @Serializable
    private data class Holder(
        @Serializable(with = OmCreativeTypeSerializer::class) val t: OmCreativeType?,
    )

    private val json = Json

    @Test
    fun `serializes display and video to their lowercase wire values`() {
        assertEquals("""{"t":"display"}""", json.encodeToString(Holder.serializer(), Holder(OmCreativeType.DISPLAY)))
        assertEquals("""{"t":"video"}""", json.encodeToString(Holder.serializer(), Holder(OmCreativeType.VIDEO)))
    }

    @Test
    fun `serializes null to an explicit JSON null`() {
        assertEquals("""{"t":null}""", json.encodeToString(Holder.serializer(), Holder(null)))
    }

    @Test
    fun `deserializes known wire values`() {
        assertEquals(OmCreativeType.DISPLAY, json.decodeFromString(Holder.serializer(), """{"t":"display"}""").t)
        assertEquals(OmCreativeType.VIDEO, json.decodeFromString(Holder.serializer(), """{"t":"video"}""").t)
    }

    @Test
    fun `deserializes an unknown wire value to null`() {
        // Tolerant decode — coercion happens inside the serializer, not via
        // Json { coerceInputValues }.
        assertNull(json.decodeFromString(Holder.serializer(), """{"t":"interactive"}""").t)
    }

    @Test
    fun `deserializes an explicit JSON null to null`() {
        assertNull(json.decodeFromString(Holder.serializer(), """{"t":null}""").t)
    }

    @Test
    fun `round-trips display, video, and null`() {
        for (value in listOf(OmCreativeType.DISPLAY, OmCreativeType.VIDEO, null)) {
            val encoded = json.encodeToString(Holder.serializer(), Holder(value))
            assertEquals(value, json.decodeFromString(Holder.serializer(), encoded).t)
        }
    }
}

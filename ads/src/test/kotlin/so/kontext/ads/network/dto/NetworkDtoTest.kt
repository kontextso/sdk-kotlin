package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes cellular with all optional fields and the typed enum wire form`() {
        val s = json.encodeToString(
            NetworkDto(type = NetworkType.CELLULAR, carrier = "Verizon", detail = "LTE", userAgent = "UA/1.0"),
        )
        assertTrue(s.contains("\"type\":\"cellular\""))
        assertTrue(s.contains("\"carrier\":\"Verizon\""))
        assertTrue(s.contains("\"detail\":\"LTE\""))
        assertTrue(s.contains("\"userAgent\":\"UA/1.0\""))
    }

    @Test
    fun `omits null optionals for wifi with encodeDefaults false`() {
        val s = json.encodeToString(NetworkDto(type = NetworkType.WIFI))
        assertTrue(s.contains("\"type\":\"wifi\""))
        assertFalse(s.contains("carrier"))
        assertFalse(s.contains("detail"))
        assertFalse(s.contains("userAgent"))
    }

    @Test
    fun `decodes the enum inside the DTO with optionals defaulting to null`() {
        val dto = json.decodeFromString<NetworkDto>("""{"type":"wifi"}""")
        assertEquals(NetworkType.WIFI, dto.type)
        assertNull(dto.carrier)
        assertNull(dto.detail)
        assertNull(dto.userAgent)
    }
}

package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NetworkTypeTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `fromString parses canonical wire values`() {
        assertEquals(NetworkType.WIFI, NetworkType.fromString("wifi"))
        assertEquals(NetworkType.CELLULAR, NetworkType.fromString("cellular"))
        assertEquals(NetworkType.ETHERNET, NetworkType.fromString("ethernet"))
        assertEquals(NetworkType.OTHER, NetworkType.fromString("other"))
    }

    @Test
    fun `fromString is case-insensitive`() {
        assertEquals(NetworkType.WIFI, NetworkType.fromString("WIFI"))
        assertEquals(NetworkType.CELLULAR, NetworkType.fromString("Cellular"))
    }

    @Test
    fun `fromString returns null for unknown, empty, or null input`() {
        // Mirrors Swift's `NetworkType(rawValue:)` shape — fallback to
        // OTHER is the call-site's job (DeviceCollector does
        // `?: NetworkType.OTHER`), not the helper's.
        assertNull(NetworkType.fromString("5g"))
        assertNull(NetworkType.fromString(""))
        assertNull(NetworkType.fromString(null))
    }

    @Test
    fun `serializes to lowercase wire value`() {
        assertEquals("\"wifi\"", json.encodeToString(NetworkType.WIFI))
        assertEquals("\"cellular\"", json.encodeToString(NetworkType.CELLULAR))
        assertEquals("\"ethernet\"", json.encodeToString(NetworkType.ETHERNET))
        assertEquals("\"other\"", json.encodeToString(NetworkType.OTHER))
    }
}

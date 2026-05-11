package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HardwareTypeTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `fromString parses canonical wire values`() {
        assertEquals(HardwareType.HANDSET, HardwareType.fromString("handset"))
        assertEquals(HardwareType.TABLET, HardwareType.fromString("tablet"))
        assertEquals(HardwareType.DESKTOP, HardwareType.fromString("desktop"))
        assertEquals(HardwareType.TV, HardwareType.fromString("tv"))
        assertEquals(HardwareType.OTHER, HardwareType.fromString("other"))
    }

    @Test
    fun `fromString is case-insensitive`() {
        assertEquals(HardwareType.HANDSET, HardwareType.fromString("HANDSET"))
        assertEquals(HardwareType.TABLET, HardwareType.fromString("Tablet"))
    }

    @Test
    fun `fromString returns null for unknown, empty, or null input`() {
        // Mirrors Swift's `HardwareType(rawValue:)` shape — fallback to
        // OTHER is the call-site's job (DeviceCollector does
        // `?: HardwareType.OTHER`), not the helper's. Keeps the
        // fallback-location decision consistent with sdk-swift.
        assertNull(HardwareType.fromString("watch"))
        assertNull(HardwareType.fromString(""))
        assertNull(HardwareType.fromString(null))
    }

    @Test
    fun `serializes to lowercase wire value`() {
        assertEquals("\"handset\"", json.encodeToString(HardwareType.HANDSET))
        assertEquals("\"tablet\"", json.encodeToString(HardwareType.TABLET))
        assertEquals("\"desktop\"", json.encodeToString(HardwareType.DESKTOP))
        assertEquals("\"tv\"", json.encodeToString(HardwareType.TV))
        assertEquals("\"other\"", json.encodeToString(HardwareType.OTHER))
    }
}

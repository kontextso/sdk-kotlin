package so.kontext.ads.network.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes all fields including the nullable timestamps`() {
        val s = json.encodeToString(
            AppDto(
                bundleId = "com.example.app",
                version = "1.2.3",
                firstInstallTime = 100L,
                lastUpdateTime = 200L,
                startTime = 300L,
            ),
        )
        assertTrue(s.contains("\"bundleId\":\"com.example.app\""))
        assertTrue(s.contains("\"version\":\"1.2.3\""))
        assertTrue(s.contains("\"firstInstallTime\":100"))
        assertTrue(s.contains("\"lastUpdateTime\":200"))
        assertTrue(s.contains("\"startTime\":300"))
    }

    @Test
    fun `omits null timestamps with encodeDefaults false`() {
        val s = json.encodeToString(AppDto(bundleId = "com.example.app", version = "1.2.3"))
        assertTrue(s.contains("\"bundleId\""))
        assertFalse(s.contains("firstInstallTime"))
        assertFalse(s.contains("lastUpdateTime"))
        assertFalse(s.contains("startTime"))
    }

    @Test
    fun `decodes with the timestamps defaulting to null`() {
        val dto = json.decodeFromString<AppDto>("""{"bundleId":"com.example.app","version":"1.2.3"}""")
        assertEquals("com.example.app", dto.bundleId)
        assertEquals("1.2.3", dto.version)
        assertNull(dto.firstInstallTime)
        assertNull(dto.lastUpdateTime)
        assertNull(dto.startTime)
    }
}

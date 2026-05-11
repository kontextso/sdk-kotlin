package so.kontext.ads.network.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InitResponseDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes`() {
        val body = """{"enabled":true,"preloadTimeout":15}"""
        val response = json.decodeFromString<InitResponseDto>(body)
        assertEquals(true, response.enabled)
        assertEquals(15, response.preloadTimeout)
    }

    @Test
    fun `defaults enabled to true when absent (Swift-parity tolerant decode)`() {
        val response = json.decodeFromString<InitResponseDto>("""{}""")
        assertTrue(response.enabled)
        assertEquals(null, response.preloadTimeout)
        // Reporting toggles default to the documented values: errors
        // forwarded (preserves prior fire-and-forget behaviour), debug
        // local-only (privacy).
        assertTrue(response.reportErrors)
        assertEquals(false, response.reportDebug)
    }

    @Test
    fun `decodes explicit reportErrors false (server kill switch)`() {
        val response = json.decodeFromString<InitResponseDto>("""{"reportErrors":false}""")
        assertEquals(false, response.reportErrors)
        assertEquals(false, response.reportDebug)
    }

    @Test
    fun `decodes explicit reportDebug true (server opt-in)`() {
        val response = json.decodeFromString<InitResponseDto>("""{"reportDebug":true}""")
        assertEquals(true, response.reportDebug)
        assertTrue(response.reportErrors)
    }
}

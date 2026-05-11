package so.kontext.ads

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.network.dto.PreloadResponseDto
import java.util.UUID

class PreloadResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val sessionUuid = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `parses successful response with bids`() {
        val body = """
            {
                "sessionId": "33333333-3333-3333-3333-333333333333",
                "bids": [
                    {"bidId": "11111111-1111-1111-1111-111111111111", "code": "inlineAd", "revenue": 1.5, "impressionTrigger": "immediate"},
                    {"bidId": "22222222-2222-2222-2222-222222222222", "code": "sidebar"}
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<PreloadResponseDto>(body)

        assertEquals(sessionUuid, response.sessionId)
        assertEquals(2, response.bids?.size)
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), response.bids!![0].bidId)
        assertEquals("inlineAd", response.bids!![0].code)
        assertEquals(1.5, response.bids!![0].revenue)
        assertEquals(so.kontext.ads.model.ImpressionTrigger.IMMEDIATE, response.bids!![0].impressionTrigger)
    }

    @Test
    fun `parses no-fill response`() {
        val body = """
            {
                "sessionId": "33333333-3333-3333-3333-333333333333",
                "bids": [],
                "skip": true,
                "skipCode": "unfilled_bid"
            }
        """.trimIndent()

        val response = json.decodeFromString<PreloadResponseDto>(body)

        assertEquals(true, response.skip)
        assertEquals("unfilled_bid", response.skipCode)
        assertTrue(response.bids!!.isEmpty())
    }

    @Test
    fun `parses error response`() {
        // Server may also send a human-readable `error` message, but the
        // SDK doesn't decode it — error events are built from the typed
        // `errCode` plus a hardcoded fallback message (matches sdk-swift).
        val body = """
            {
                "errCode": "geo_disabled",
                "permanent": true
            }
        """.trimIndent()

        val response = json.decodeFromString<PreloadResponseDto>(body)

        assertEquals("geo_disabled", response.errCode)
        assertEquals(true, response.permanent)
        assertNull(response.sessionId)
    }

    @Test
    fun `parses response with unknown fields gracefully`() {
        val body = """
            {
                "sessionId": "33333333-3333-3333-3333-333333333333",
                "bids": [],
                "unknownField": "ignored",
                "remoteLogLevel": "debug"
            }
        """.trimIndent()

        val response = json.decodeFromString<PreloadResponseDto>(body)
        assertEquals(sessionUuid, response.sessionId)
    }
}

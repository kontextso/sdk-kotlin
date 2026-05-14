package so.kontext.ads

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.Character
import so.kontext.ads.model.ImpressionTrigger
import so.kontext.ads.model.Message
import so.kontext.ads.model.PreloadResult
import so.kontext.ads.model.Regulatory
import so.kontext.ads.model.Role
import so.kontext.ads.model.SessionOptions
import so.kontext.ads.network.HttpClient
import so.kontext.ads.network.HttpResponse
import so.kontext.ads.network.Preload
import so.kontext.ads.network.PreloadParams
import so.kontext.ads.network.dto.AppDto
import so.kontext.ads.network.dto.DeviceDto
import so.kontext.ads.network.dto.PreloadRequestDto
import java.net.URI

class PreloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val testDevice = DeviceDto(
        hardware = so.kontext.ads.network.dto.HardwareDto(type = so.kontext.ads.network.dto.HardwareType.HANDSET, brand = "Google", model = "Pixel 8", bootTime = 1_700_000_000_000L, sdCardAvailable = false),
        os = so.kontext.ads.network.dto.OsDto(name = "android", version = "14", locale = "en-US", timezone = "UTC"),
        screen = so.kontext.ads.network.dto.ScreenDto(width = 1080, height = 2400, dpr = 2.75, darkMode = false, orientation = so.kontext.ads.network.dto.ScreenOrientation.PORTRAIT, brightness = 50.0),
        power = so.kontext.ads.network.dto.PowerDto(lowPowerMode = false, batteryState = so.kontext.ads.network.dto.BatteryState.UNKNOWN),
        audio = so.kontext.ads.network.dto.AudioDto(volume = 50, muted = false, outputPluggedIn = false, outputType = emptyList()),
    )
    private val testApp = AppDto(bundleId = "test.bundle", version = "1.0.0")

    private fun makeConfig(
        regulatory: Regulatory? = null,
        character: Character? = null,
        variantId: String? = null,
        userEmail: String? = null,
    ) = resolveConfig(
        SessionOptions(
            publisherToken = "test-token",
            userId = "test-user",
            conversationId = "test-conv",
            regulatory = regulatory,
            character = character,
            variantId = variantId,
            userEmail = userEmail,
        ),
    )

    private fun makeMessages(count: Int = 1): List<Message> =
        (1..count).map { Message(id = "m$it", role = Role.USER, content = "Message $it") }

    // ---------------------------------------------------------------------------
    // Basic request flow
    // ---------------------------------------------------------------------------

    @Test
    fun `requestAd returns failure for empty messages`() = runTest {
        val preload = Preload(
            PreloadParams(
                messages = emptyList(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ -> HttpResponse(200, "{}") },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Failure)
        assertEquals("No messages", (result as PreloadResult.Failure).reason)
    }

    @Test
    fun `requestAd sends POST to correct URL`() = runTest {
        var capturedUrl: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { url, _, _, _ ->
                    capturedUrl = url
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        assertEquals("https://server.megabrain.co/preload", capturedUrl)
    }

    @Test
    fun `requestAd sends correct headers`() = runTest {
        var capturedHeaders: Map<String, String>? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, headers, _, _ ->
                    capturedHeaders = headers
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        assertEquals("application/json", capturedHeaders!!["Content-Type"])
        assertEquals("test-token", capturedHeaders!!["Kontextso-Publisher-Token"])
        assertEquals("0", capturedHeaders!!["Kontextso-Is-Disabled"])
    }

    @Test
    fun `requestAd sends disabled header when disabled`() = runTest {
        var capturedHeaders: Map<String, String>? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, headers, _, _ ->
                    capturedHeaders = headers
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = true)

        assertEquals("1", capturedHeaders!!["Kontextso-Is-Disabled"])
    }

    // ---------------------------------------------------------------------------
    // Request body
    // ---------------------------------------------------------------------------

    @Test
    fun `requestAd body contains publisherToken, userId, conversationId`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        assertEquals("test-token", dto.publisherToken)
        assertEquals("test-user", dto.userId)
        assertEquals("test-conv", dto.conversationId)
        assertEquals(listOf("inlineAd"), dto.enabledPlacementCodes)
    }

    @Test
    fun `requestAd body contains SDK info`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        assertEquals("sdk-kotlin", dto.sdk.name)
        assertEquals("4.0.0", dto.sdk.version)
        assertEquals("android", dto.sdk.platform)
    }

    @Test
    fun `requestAd body contains messages`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = listOf(
                    Message(id = "u1", role = Role.USER, content = "Hello"),
                    Message(id = "a1", role = Role.ASSISTANT, content = "Hi there"),
                ),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        assertEquals(2, dto.messages.size)
        assertEquals("u1", dto.messages[0].id)
        assertEquals(Role.USER, dto.messages[0].role)
        assertEquals("Hello", dto.messages[0].content)
        assertEquals("a1", dto.messages[1].id)
        assertEquals(Role.ASSISTANT, dto.messages[1].role)
    }

    @Test
    fun `requestAd trims messages to MAX_MESSAGES`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(40),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        assertEquals(Constants.MAX_MESSAGES, dto.messages.size)
        assertEquals("m11", dto.messages.first().id) // last 30 of 40
    }

    @Test
    fun `requestAd includes sessionId when provided`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        val resumeId = java.util.UUID.fromString("44444444-4444-4444-4444-444444444444")
        preload.requestAd(sessionId = resumeId, disabled = false)

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        // sessionId round-trips as a typed UUID via UuidSerializer; the
        // wire form is the canonical UUID string.
        assertEquals(resumeId, dto.sessionId)
    }

    @Test
    fun `requestAd includes advertisingId when provided`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false, advertisingId = "test-gaid")

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        assertEquals("test-gaid", dto.advertisingId)
    }

    @Test
    fun `requestAd includes character when configured`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(
                    character = Character(
                        id = "c1",
                        name = "Bot",
                        avatarUrl = URI.create("https://example.com/bot.png"),
                        persona = "Friendly",
                    ),
                ),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        val character = checkNotNull(dto.character)
        assertEquals("c1", character.id)
        assertEquals("Bot", character.name)
        assertEquals("Friendly", character.persona)
    }

    @Test
    fun `requestAd includes regulatory when configured`() = runTest {
        var capturedBody: String? = null

        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(regulatory = Regulatory(gdpr = 1, gdprConsent = "consent-str", coppa = 0)),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, body, _ ->
                    capturedBody = body
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[]}""")
                },
            ),
        )

        preload.requestAd(sessionId = null, disabled = false)

        val dto = json.decodeFromString<PreloadRequestDto>(capturedBody!!)
        val regulatory = checkNotNull(dto.regulatory)
        assertEquals(1, regulatory.gdpr)
        assertEquals("consent-str", regulatory.gdprConsent)
        assertEquals(0, regulatory.coppa)
    }

    // ---------------------------------------------------------------------------
    // Response handling
    // ---------------------------------------------------------------------------

    @Test
    fun `requestAd returns success with bids`() = runTest {
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ ->
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":2.5,"impressionTrigger":"component"}]}""")
                },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Success)
        val success = result as PreloadResult.Success
        assertEquals(java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"), success.sessionId)
        assertEquals(1, success.bids.size)
        val bid = success.bids[0]
        assertEquals(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"), bid.bidId)
        assertEquals(2.5, bid.revenue)
        assertEquals(ImpressionTrigger.COMPONENT, bid.impressionTrigger)
    }

    @Test
    fun `requestAd filters bids by enabledPlacementCodes`() = runTest {
        // makeConfig defaults enabledPlacementCodes to ["inlineAd"]; only the
        // inlineAd bid should survive the filter even though the server
        // returned a sidebar one too.
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ ->
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"sidebar"},{"bidId":"22222222-2222-2222-2222-222222222222","code":"inlineAd"}]}""")
                },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Success)
        val bids = (result as PreloadResult.Success).bids
        assertEquals(1, bids.size)
        assertEquals(java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"), bids[0].bidId)
        assertEquals("inlineAd", bids[0].code)
    }

    @Test
    fun `requestAd carries one bid per matching placement when multiple codes enabled`() = runTest {
        // Multi-placement support: when the publisher enables ["inlineAd", "sidebar"],
        // both bids surface in PreloadResult.Success. Previously Kotlin only
        // exposed `bid: Bid?` (the first) — sidebar ads silently got no fill
        // even when the server filled them. Regression guard.
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = resolveConfig(
                    so.kontext.ads.model.SessionOptions(
                        publisherToken = "test-token",
                        userId = "test-user",
                        conversationId = "test-conv",
                        enabledPlacementCodes = listOf("inlineAd", "sidebar"),
                    ),
                ),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ ->
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd"},{"bidId":"22222222-2222-2222-2222-222222222222","code":"sidebar"}]}""")
                },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Success)
        val bids = (result as PreloadResult.Success).bids
        assertEquals(2, bids.size)
        assertEquals(setOf("inlineAd", "sidebar"), bids.map { it.code }.toSet())
    }

    @Test
    fun `requestAd returns failure on HTTP error`() = runTest {
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ -> HttpResponse(500, "Internal Server Error") },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Failure)
        assertTrue((result as PreloadResult.Failure).reason.contains("500"))
    }

    @Test
    fun `requestAd returns failure with disableSession on permanent error`() = runTest {
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ ->
                    HttpResponse(200, """{"errCode":"geo_disabled","error":"Geo disabled","permanent":true}""")
                },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Failure)
        assertTrue((result as PreloadResult.Failure).disableSession)
    }

    @Test
    fun `requestAd returns no-fill when skip is true`() = runTest {
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ ->
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[],"skip":true,"skipCode":"unfilled_bid"}""")
                },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Failure)
        val failure = result as PreloadResult.Failure
        assertFalse(failure.disableSession)
        assertTrue(failure.event is AdEvent.NoFill)
        assertEquals("unfilled_bid", (failure.event as AdEvent.NoFill).skipCode)
    }

    @Test
    fun `requestAd returns failure on exception`() = runTest {
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ -> throw IllegalStateException("Network error") },
            ),
        )

        val result = preload.requestAd(sessionId = null, disabled = false)

        assertTrue(result is PreloadResult.Failure)
        assertTrue((result as PreloadResult.Failure).reason.contains("Network error"))
    }

    @Test
    fun `hasBid and bids reflect state after successful preload`() = runTest {
        val preload = Preload(
            PreloadParams(
                messages = makeMessages(),
                config = makeConfig(),
                device = testDevice,
                app = testApp,
                httpClient = HttpClient { _, _, _, _ ->
                    HttpResponse(200, """{"sessionId":"33333333-3333-3333-3333-333333333333","bids":[{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd"}]}""")
                },
            ),
        )

        assertFalse(preload.hasBid())
        assertTrue(preload.bids.isEmpty())

        preload.requestAd(sessionId = null, disabled = false)

        assertTrue(preload.hasBid())
        assertEquals(1, preload.bids.size)
        assertEquals(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"), preload.bids[0].bidId)
    }
}

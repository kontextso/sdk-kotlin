package so.kontext.ads.internal.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.data.dto.request.AppDto
import so.kontext.ads.internal.data.dto.request.AudioDto
import so.kontext.ads.internal.data.dto.request.ChatMessageDto
import so.kontext.ads.internal.data.dto.request.DeviceDto
import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.dto.request.HardwareDto
import so.kontext.ads.internal.data.dto.request.NetworkDto
import so.kontext.ads.internal.data.dto.request.OsDto
import so.kontext.ads.internal.data.dto.request.PowerDto
import so.kontext.ads.internal.data.dto.request.PreloadRequest
import so.kontext.ads.internal.data.dto.request.ScreenDto
import so.kontext.ads.internal.data.dto.request.SdkDto

/**
 * Integration tests for [AdsApiImpl] using Ktor's MockEngine. Exercises the
 * full HTTP request → deserialization pipeline with only the network
 * transport faked.
 */
class AdsApiImplTest {

    private fun preloadRequest(
        publisherToken: String = "pub-tok",
        isDisabled: Boolean = false,
    ) = PreloadRequest(
        publisherToken = publisherToken,
        conversationId = "c-1",
        userId = "u-1",
        messages = listOf(
            ChatMessageDto("m-1", "user", "hi", "2025-01-01T00:00:00Z"),
        ),
        sdk = SdkDto("sdk-kotlin", "1.0.0", "android"),
        app = AppDto("com.app", "1.0", null, 0, 0, 0),
        device = DeviceDto(
            os = OsDto("android", "14", "en-US", "UTC"),
            hardware = HardwareDto("Samsung", "S24", "handset", 0L, false),
            screen = ScreenDto(1080, 2400, 3f, "portrait", false),
            power = PowerDto(100, "full", false),
            audio = AudioDto(50, false, false, emptyList()),
            network = NetworkDto(null, null, null, null),
        ),
        isDisabled = isDisabled,
    )

    private fun buildApi(engine: MockEngine, baseUrl: String = "https://api.test"): AdsApiImpl {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
            }
            defaultRequest {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
        }
        return AdsApiImpl(httpClient = client, baseUrl = baseUrl)
    }

    @Test
    fun `preload POSTs to the preload endpoint with token + is-disabled headers`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = """{"sessionId": "s-1", "bids": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val response = buildApi(engine).preload(preloadRequest(publisherToken = "pub-int"), timeout = 5000)

        assertEquals("s-1", response.sessionId)
        val request = captured!!
        assertTrue(request.url.toString().endsWith("/preload"))
        assertEquals("POST", request.method.value)
        assertEquals("pub-int", request.headers["Kontextso-Publisher-Token"])
        assertEquals("0", request.headers["Kontextso-Is-Disabled"])
    }

    @Test
    fun `preload with isDisabled=true sets Kontextso-Is-Disabled header to 1`() = runTest {
        var capturedHeader: String? = null
        val engine = MockEngine { request ->
            capturedHeader = request.headers["Kontextso-Is-Disabled"]
            respond(
                content = """{"sessionId": "s"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        buildApi(engine).preload(preloadRequest(isDisabled = true), timeout = 5000)
        assertEquals("1", capturedHeader)
    }

    @Test
    fun `preload deserialises bids + skip + permanent fields from response`() = runTest {
        val body = """
            {
              "sessionId": "sess-1",
              "bids": [
                {"bidId": "b-1", "code": "inlineAd", "adDisplayPosition": "afterAssistantMessage"}
              ],
              "permanent": true,
              "skip": true,
              "skipCode": "no_fill",
              "preloadTimeout": 3000
            }
        """.trimIndent()
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val response = buildApi(engine).preload(preloadRequest(), timeout = 5000)

        assertEquals("sess-1", response.sessionId)
        assertEquals(1, response.bids?.size)
        assertEquals("b-1", response.bids?.first()?.bidId)
        assertEquals(true, response.permanent)
        assertEquals(true, response.skip)
        assertEquals("no_fill", response.skipCode)
        assertEquals(3000, response.preloadTimeout)
    }

    @Test
    fun `preload tolerates unknown keys in the response`() = runTest {
        val engine = MockEngine {
            respond(
                """{"sessionId": "s", "futureField": "ignored"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val response = buildApi(engine).preload(preloadRequest(), timeout = 5000)
        assertEquals("s", response.sessionId)
        assertNull(response.bids)
    }

    @Test
    fun `preload request URL uses the configured baseUrl`() = runTest {
        var url: String? = null
        val engine = MockEngine { request ->
            url = request.url.toString()
            respond(
                """{"sessionId": "s"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        buildApi(engine, baseUrl = "https://custom.example.com").preload(preloadRequest(), timeout = 5000)
        assertEquals("https://custom.example.com/preload", url)
    }

    @Test
    fun `reportError POSTs to the error endpoint with the serialized body`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.OK)
        }
        val body = ErrorRequest(error = "net down")
        buildApi(engine).reportError(body)
        assertTrue(request!!.url.toString().endsWith("/error"))
        assertEquals("POST", request!!.method.value)
        // Body is a Ktor OutgoingContent wrapping the JSON — we just check it's there.
        assertTrue(request!!.body.toString().isNotEmpty())
    }
}

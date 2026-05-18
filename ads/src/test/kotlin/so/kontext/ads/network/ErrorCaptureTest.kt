package so.kontext.ads.network

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import so.kontext.ads.Constants
import so.kontext.ads.SDKInfo
import so.kontext.ads.TEST_INSTALL_ID
import java.io.IOException
import java.util.UUID

/**
 * Tests the `POST /error` body shape. The actual HTTP send is JDK
 * mechanical (HttpURLConnection.outputStream.write) and not worth
 * mocking — the regression risk lives in body field naming, sdk
 * descriptor placement, and the optional-fields contract that the
 * ad server keys off for error attribution.
 */
class ErrorCaptureTest {

    private fun ctx(
        publisherToken: String? = "tok-1",
        conversationId: String? = "conv-1",
        userId: String? = "user-1",
        installId: String? = TEST_INSTALL_ID,
        bidId: UUID? = null,
    ) = ErrorContext(
        adServerUrl = "https://server.example.com",
        publisherToken = publisherToken,
        conversationId = conversationId,
        userId = userId,
        installId = installId,
        bidId = bidId,
    )

    @Test
    fun `body has error and stack at top level`() {
        val raw = buildErrorReportBody(
            context = ctx(),
            message = "boom",
            stack = "at foo (bar:1)",
        )
        val body = JSONObject(raw)
        assertEquals("boom", body.getString("error"))
        assertEquals("at foo (bar:1)", body.getString("stack"))
    }

    @Test
    fun `additionalData carries publisherToken, conversationId, userId`() {
        val body = JSONObject(
            buildErrorReportBody(context = ctx(), message = "x", stack = ""),
        )
        val additional = body.getJSONObject("additionalData")
        assertEquals("tok-1", additional.getString("publisherToken"))
        assertEquals("conv-1", additional.getString("conversationId"))
        assertEquals("user-1", additional.getString("userId"))
    }

    @Test
    fun `additionalData carries installId for per-install attribution`() {
        // installId lives on additionalData (parallel to DebugRequestDto)
        // so the server's error-ingestion pipeline can attribute reports
        // to a stable install identity. Mirrors sdk-swift
        // `ErrorRequestDTO.AdditionalData`.
        val body = JSONObject(
            buildErrorReportBody(context = ctx(), message = "x", stack = ""),
        )
        val additional = body.getJSONObject("additionalData")
        assertEquals(TEST_INSTALL_ID, additional.getString("installId"))
    }

    @Test
    fun `additionalData_sdk has name, version, AND platform (matches iOS shape)`() {
        // The ad server attributes errors per SDK by reading
        // `additionalData.sdk.{name, version, platform}`. iOS sends the
        // full SDKDTO via `SDKInfo.current.toDTO()`; Android must match
        // so error-attribution dashboards can slice by platform.
        // Regression guard — `platform` was missing from this body until
        // 2026-05; lock it in.
        val body = JSONObject(
            buildErrorReportBody(context = ctx(), message = "x", stack = ""),
        )
        val sdk = body.getJSONObject("additionalData").getJSONObject("sdk")
        assertEquals(SDKInfo.NAME, sdk.getString("name"))
        assertEquals(SDKInfo.VERSION, sdk.getString("version"))
        assertEquals(SDKInfo.PLATFORM, sdk.getString("platform"))
    }

    @Test
    fun `bidId is encoded as lowercase canonical string when supplied`() {
        // ErrorContext.bidId is a typed UUID; the wire form is
        // UUID.toString() which is RFC 4122 lowercase. Mixed-case input
        // through fromString() must round-trip to lowercase on the wire.
        val mixed = UUID.fromString("AAAABBBB-CCCC-DDDD-EEEE-FFFFFFFFFFFF")
        val body = JSONObject(
            buildErrorReportBody(
                context = ctx(bidId = mixed),
                message = "render fail",
                stack = "",
            ),
        )
        assertEquals(
            "aaaabbbb-cccc-dddd-eeee-ffffffffffff",
            body.getJSONObject("additionalData").getString("bidId"),
        )
    }

    @Test
    fun `bidId omitted when null (keeps wire payload minimal)`() {
        val body = JSONObject(
            buildErrorReportBody(context = ctx(bidId = null), message = "x", stack = ""),
        )
        // Wire format matches iOS — bidId only appears when present, not
        // as `"bidId": null`. Server handlers checking `obj.bidId !== undefined`
        // would mis-classify a null as a meaningful value.
        assertFalse(body.getJSONObject("additionalData").has("bidId"))
    }

    @Test
    fun `null identifier fields are omitted from the wire (matches iOS Encodable)`() {
        // Wire format matches sdk-swift's default Encodable behaviour:
        // null optionals are omitted entirely rather than emitted as
        // `"key": null`. Server doesn't differentiate the two cases for
        // attribution (filters on field presence, not null-vs-absent).
        val body = JSONObject(
            buildErrorReportBody(
                context = ctx(publisherToken = null, conversationId = null, userId = null, installId = null),
                message = "boot",
                stack = null,
            ),
        )
        val additional = body.getJSONObject("additionalData")
        assertFalse(additional.has("publisherToken"))
        assertFalse(additional.has("conversationId"))
        assertFalse(additional.has("userId"))
        assertFalse(additional.has("installId"))
        // Top-level `stack` likewise omitted when null.
        assertFalse(body.has("stack"))
    }

    // ---------------------------------------------------------------------------
    // postErrorReport HTTP-send path — verifies what reaches the wire end-to-end
    // (URL construction, headers, body shape, timeout). Drives a fake HttpClient
    // captures the four POST args; no real network required.
    // ---------------------------------------------------------------------------

    private class CapturingHttpClient : HttpClient {
        var url: String? = null
        var headers: Map<String, String>? = null
        var body: String? = null
        var timeoutMs: Long? = null
        var thrown: Throwable? = null

        override suspend fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Long): HttpResponse {
            this.url = url
            this.headers = headers
            this.body = body
            this.timeoutMs = timeoutMs
            thrown?.let { throw it }
            return HttpResponse(204, "")
        }
    }

    @Test
    fun `postErrorReport posts to {adServerUrl}slasherror with Content-Type and ERROR_REPORT_TIMEOUT_MS`() = runTest {
        val client = CapturingHttpClient()
        postErrorReport(
            context = ctx().copy(adServerUrl = "https://server.example.com"),
            message = "boom",
            stack = "at foo",
            httpClient = client,
        )

        assertEquals("https://server.example.com/error", client.url)
        assertEquals("application/json", client.headers!!["Content-Type"])
        // Wired to the same constant the suspend wrapper uses; if this
        // ever drifts the entire SDK's network timeout strategy is broken.
        assertEquals(Constants.ERROR_REPORT_TIMEOUT_MS, client.timeoutMs)
    }

    @Test
    fun `postErrorReport body matches buildErrorReportBody output`() = runTest {
        // Don't re-test the body shape (covered above) — pin that the
        // wire body and the testable builder produce the same string,
        // so no future refactor can fork them.
        val client = CapturingHttpClient()
        val context = ctx(bidId = UUID.fromString("11111111-1111-1111-1111-111111111111"))
        postErrorReport(context = context, message = "render fail", stack = "trace", httpClient = client)

        val expected = buildErrorReportBody(context, message = "render fail", stack = "trace")
        assertEquals(expected, client.body)
    }

    @Test
    fun `postErrorReport swallows IOException from HttpClient`() = runTest {
        // Network failure must not propagate — error reporting can
        // never disrupt the SDK. No assertion needed beyond "didn't throw".
        val client = CapturingHttpClient().apply { thrown = IOException("connection refused") }
        postErrorReport(context = ctx(), message = "x", stack = null, httpClient = client)
        assertNotNull(client.url, "delegate should have been invoked despite throwing")
    }

    @Test
    fun `postErrorReport swallows generic Exception from HttpClient`() = runTest {
        // RuntimeException from a misbehaving HttpClient (e.g. a future
        // RetryHttpClient.RateLimited) must also be swallowed.
        val client = CapturingHttpClient().apply { thrown = RuntimeException("unexpected") }
        postErrorReport(context = ctx(), message = "x", stack = null, httpClient = client)
        assertNotNull(client.url)
    }

    @Test
    fun `body round-trips through JSONObject (well-formed JSON)`() {
        // Sanity: the produced string must be valid JSON. Catches a
        // category of bugs where someone passes a non-JSON-encodable
        // value at the leaf.
        val raw = buildErrorReportBody(
            context = ctx(),
            message = "Special chars: \" \\ \n",
            stack = "with \"quotes\"",
        )
        val parsed = JSONObject(raw)
        assertEquals("Special chars: \" \\ \n", parsed.getString("error"))
        assertEquals("with \"quotes\"", parsed.getString("stack"))
    }
}

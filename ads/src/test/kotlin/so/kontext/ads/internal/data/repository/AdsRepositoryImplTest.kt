package so.kontext.ads.internal.data.repository

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.takeFrom
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.PreloadResult
import so.kontext.ads.internal.data.api.AdsApi
import so.kontext.ads.internal.data.dto.response.BidDto
import so.kontext.ads.internal.data.dto.response.PreloadResponse
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.utils.ApiResponse
import java.io.IOException

class AdsRepositoryImplTest {

    private lateinit var adsApi: AdsApi
    private lateinit var adsRepository: AdsRepository

    @BeforeEach
    fun setUp() {
        adsApi = mockk()
        adsRepository = AdsRepositoryImpl(adsApi)
    }

    private suspend fun callPreload() = adsRepository.preload(
        sessionId = "session_id_1",
        messages = emptyList(),
        deviceInfo = mockk(relaxed = true),
        adsConfiguration = mockk(relaxed = true),
        timeout = 5000,
        isDisabled = false,
    )

    // ---- Success ----

    @Test
    fun `preload should return success when api call is successful`() = runTest {
        val preloadResponse = PreloadResponse(
            bids = listOf(BidDto("bid_id_1", "inlineAd", "afterAssistantMessage")),
        )
        coEvery { adsApi.preload(any(), any()) } returns preloadResponse

        val result = callPreload()
        assertTrue(result is ApiResponse.Success)
        val successResult = (result as ApiResponse.Success<PreloadResult>).data
        assertEquals(1, successResult.bids?.size)
        assertEquals("bid_id_1", successResult.bids?.first()?.bidId)
    }

    @Test
    fun `preload maps sessionId and remoteLogLevel through to PreloadResult`() = runTest {
        val preloadResponse = PreloadResponse(
            sessionId = "new-session",
            remoteLogLevel = "debug",
            bids = emptyList(),
        )
        coEvery { adsApi.preload(any(), any()) } returns preloadResponse

        val result = (callPreload() as ApiResponse.Success).data
        assertEquals("new-session", result.sessionId)
        assertEquals("debug", result.remoteLogLevel)
    }

    @Test
    fun `preload passes skip flag and skipCode through`() = runTest {
        val preloadResponse = PreloadResponse(
            skip = true,
            skipCode = "no_fill",
        )
        coEvery { adsApi.preload(any(), any()) } returns preloadResponse

        val result = (callPreload() as ApiResponse.Success).data
        assertEquals(true, result.skip)
        assertEquals("no_fill", result.skipCode)
    }

    @Test
    fun `preload passes preloadTimeout through`() = runTest {
        coEvery { adsApi.preload(any(), any()) } returns PreloadResponse(preloadTimeout = 2500)
        val result = (callPreload() as ApiResponse.Success).data
        assertEquals(2500, result.preloadTimeout)
    }

    @Test
    fun `preload returns null bids when response has null bids list`() = runTest {
        coEvery { adsApi.preload(any(), any()) } returns PreloadResponse(bids = null)
        val result = (callPreload() as ApiResponse.Success).data
        assertNull(result.bids)
    }

    // ---- Error paths ----

    @Test
    fun `preload should return network error when api call throws IOException`() = runTest {
        coEvery { adsApi.preload(any(), any()) } throws IOException("Network connection failed")

        val result = callPreload()
        assertTrue(result is ApiResponse.Error)
        val errorResult = (result as ApiResponse.Error).error
        assertTrue(errorResult is ApiError.Connection)
    }

    @Test
    fun `preload maps HttpRequestTimeoutException to ApiError_Timeout`() = runTest {
        val builder = HttpRequestBuilder()
        builder.url.takeFrom("https://x")
        coEvery { adsApi.preload(any(), any()) } throws HttpRequestTimeoutException(builder)
        val result = callPreload()
        assertTrue(result is ApiResponse.Error)
        val err = (result as ApiResponse.Error).error
        assertTrue(err is ApiError.Timeout)
    }

    @Test
    fun `preload maps SerializationException to ApiError_Serialization`() = runTest {
        coEvery { adsApi.preload(any(), any()) } throws SerializationException("bad json")
        val result = callPreload()
        val err = (result as ApiResponse.Error).error
        assertTrue(err is ApiError.Serialization)
    }

    @Test
    fun `preload maps any other Exception to ApiError_UnexpectedError`() = runTest {
        coEvery { adsApi.preload(any(), any()) } throws IllegalStateException("what")
        val result = callPreload()
        val err = (result as ApiResponse.Error).error
        assertTrue(err is ApiError.UnexpectedError)
    }
}

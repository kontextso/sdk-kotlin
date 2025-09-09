package so.kontext.ads.internal.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `preload should return success when api call is successful`() = runTest {
        val preloadResponse = PreloadResponse(
            bids = listOf(BidDto("bid_id_1", "inlineAd", "afterAssistantMessage")),
        )
        coEvery { adsApi.preload(any(), any()) } returns preloadResponse

        val result = adsRepository.preload(
            sessionId = "session_id_1",
            messages = emptyList(),
            deviceInfo = mockk(relaxed = true),
            adsConfiguration = mockk(relaxed = true),
            timeout = 5000,
        )

        assertTrue(result is ApiResponse.Success)
        val successResult = (result as ApiResponse.Success<PreloadResult>).data

        assertEquals(1, successResult.bids?.size)
        assertEquals("bid_id_1", successResult.bids?.first()?.bidId)
    }

    @Test
    fun `preload should return network error when api call throws IOException`() = runTest {
        val exceptionMessage = "Network connection failed"
        coEvery { adsApi.preload(any(), any()) } throws IOException(exceptionMessage)

        val result = adsRepository.preload(
            sessionId = "session_id_1",
            messages = emptyList(),
            deviceInfo = mockk(relaxed = true),
            adsConfiguration = mockk(relaxed = true),
            timeout = 5000,
        )

        assertTrue(result is ApiResponse.Error)
        val errorResult = (result as ApiResponse.Error).error
        assertTrue(errorResult is ApiError.Connection)
    }
}

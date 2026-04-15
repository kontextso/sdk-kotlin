package so.kontext.ads.internal.utils

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.data.error.ApiError
import java.io.IOException

class ApiCallTest {

    @Test
    fun `withApiCall wraps success into ApiResponse_Success`() = runTest {
        val result = withApiCall { 42 }
        assertTrue(result is ApiResponse.Success)
        assertEquals(42, (result as ApiResponse.Success).data)
    }

    @Test
    fun `IOException is mapped to ApiError_Connection`() = runTest {
        val cause = IOException("dns down")
        val result = withApiCall { throw cause }
        assertTrue(result is ApiResponse.Error)
        val err = (result as ApiResponse.Error).error
        assertTrue(err is ApiError.Connection)
        assertSame(cause, (err as ApiError.Connection).cause)
    }

    @Test
    fun `SerializationException is mapped to ApiError_Serialization`() = runTest {
        val cause = SerializationException("bad json")
        val result = withApiCall { throw cause }
        val err = (result as ApiResponse.Error).error
        assertTrue(err is ApiError.Serialization)
    }

    @Test
    fun `HttpRequestTimeoutException is mapped to ApiError_Timeout`() = runTest {
        val builder = HttpRequestBuilder()
        builder.url.takeFrom("https://x")
        val cause = HttpRequestTimeoutException(builder)
        val result = withApiCall { throw cause }
        val err = (result as ApiResponse.Error).error
        assertTrue(err is ApiError.Timeout)
    }

    @Test
    fun `any other Exception is mapped to UnexpectedError`() = runTest {
        val cause = IllegalStateException("what")
        val result = withApiCall { throw cause }
        val err = (result as ApiResponse.Error).error
        assertTrue(err is ApiError.UnexpectedError)
        assertSame(cause, (err as ApiError.UnexpectedError).cause)
    }
}

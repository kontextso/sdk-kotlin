package so.kontext.ads.internal.data.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class ErrorsTest {

    // ---- ApiError ----

    @Test
    fun `ApiError TemporaryError carries code and cause`() {
        val cause = IOException("net")
        val err = ApiError.TemporaryError(cause = cause, code = "E_TEMP")
        assertSame(cause, err.cause)
        assertEquals("E_TEMP", err.code)
        assertTrue(err is ApiError)
        assertTrue(err is SdkError)
    }

    @Test
    fun `ApiError PermanentError carries code and cause`() {
        val err = ApiError.PermanentError(cause = null, code = "E_PERM")
        assertEquals("E_PERM", err.code)
        assertNull(err.cause)
    }

    @Test
    fun `ApiError Timeout carries cause`() {
        val cause = IOException("t/o")
        val err = ApiError.Timeout(cause = cause)
        assertSame(cause, err.cause)
    }

    @Test
    fun `ApiError Connection carries cause`() {
        val cause = IOException("boom")
        val err = ApiError.Connection(cause = cause)
        assertSame(cause, err.cause)
    }

    @Test
    fun `ApiError Serialization carries cause`() {
        val cause = IllegalStateException("bad json")
        val err = ApiError.Serialization(cause = cause)
        assertSame(cause, err.cause)
    }

    @Test
    fun `ApiError Http carries status code + cause`() {
        val cause = IOException("http")
        val err = ApiError.Http(cause = cause, code = 503)
        assertSame(cause, err.cause)
        assertEquals(503, err.code)
    }

    @Test
    fun `ApiError UnexpectedError allows null cause`() {
        val err = ApiError.UnexpectedError(cause = null)
        assertNull(err.cause)
    }

    // ---- KontextError ----

    @Test
    fun `KontextError AdUnavailable has default message`() {
        val err = KontextError.AdUnavailable()
        assertEquals("No ad was available.", err.message)
        assertTrue(err is KontextError)
        assertTrue(err is Exception)
    }

    @Test
    fun `KontextError AdUnavailable accepts custom message`() {
        val err = KontextError.AdUnavailable(message = "custom")
        assertEquals("custom", err.message)
    }

    @Test
    fun `KontextError NetworkError wraps cause and keeps default message`() {
        val cause = IOException("dns")
        val err = KontextError.NetworkError(cause = cause)
        assertEquals("A network error occurred.", err.message)
        assertSame(cause, err.cause)
    }

    @Test
    fun `KontextError NetworkError accepts custom message`() {
        val cause = IOException("dns")
        val err = KontextError.NetworkError(message = "custom", cause = cause)
        assertEquals("custom", err.message)
    }

    // ---- data class equality ----

    @Test
    fun `ApiError_Http equality is structural`() {
        val c = IOException("x")
        val a = ApiError.Http(cause = c, code = 500)
        val b = ApiError.Http(cause = c, code = 500)
        assertEquals(a, b)
    }

    @Test
    fun `KontextError_AdUnavailable equality is structural`() {
        val a = KontextError.AdUnavailable()
        val b = KontextError.AdUnavailable()
        assertEquals(a, b)
    }
}

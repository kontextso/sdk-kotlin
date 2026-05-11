package so.kontext.ads.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `DateFormatting.iso8601String` produces ISO 8601 in UTC with
 * millisecond precision (e.g. `"2024-01-01T00:00:00.000Z"`) — matches
 * sdk-js's `Date.toISOString()` and sdk-swift's `DateFormatting`.
 *
 * Wire-format-stability is critical: cross-platform conversations cap
 * messages at the same point only if all SDKs format `createdAt` the
 * same way. A regression here silently corrupts message ordering on
 * the ad server.
 */
class DateFormattingTest {

    @Test
    fun `epoch zero formats as expected ISO 8601 UTC`() {
        assertEquals("1970-01-01T00:00:00.000Z", DateFormatting.iso8601String(Date(0)))
    }

    @Test
    fun `output always ends with Z (UTC) regardless of system timezone`() {
        // The formatter is locked to UTC at construction, so the system
        // default timezone shouldn't shift the output. A test running
        // on a non-UTC CI machine would catch a regression where someone
        // dropped the `timeZone = TimeZone.getTimeZone("UTC")` config.
        val s = DateFormatting.iso8601String(Date())
        assertTrue(s.endsWith("Z"), "Expected trailing Z, got: $s")
    }

    @Test
    fun `format includes millisecond precision (3-digit fractional seconds)`() {
        // sdk-js's Date.toISOString() always emits .SSS — without this
        // precision, server-side ordering of rapid-fire messages goes
        // wrong (multiple messages collapse into the same second).
        val s = DateFormatting.iso8601String(Date(1234L)) // 1.234 s after epoch
        assertEquals("1970-01-01T00:00:01.234Z", s)
    }

    @Test
    fun `distinct millisecond inputs produce distinct outputs`() {
        // Sanity: format isn't accidentally truncating sub-second.
        val a = DateFormatting.iso8601String(Date(1_700_000_000_001L))
        val b = DateFormatting.iso8601String(Date(1_700_000_000_002L))
        assertNotEquals(a, b)
    }

    @Test
    fun `concurrent calls produce correct output (formatter is synchronised)`() {
        // SimpleDateFormat is NOT thread-safe; the implementation
        // synchronises around the shared formatter. Throw 100 threads at
        // it and confirm every result formats correctly. A regression
        // (someone removing the synchronized block) would surface as
        // garbled output strings under contention.
        val pool = Executors.newFixedThreadPool(8)
        val date = Date(1_700_000_000_000L)
        val expected = "2023-11-14T22:13:20.000Z"
        try {
            val futures = (1..100).map {
                pool.submit<String> { DateFormatting.iso8601String(date) }
            }
            futures.forEach { f ->
                assertEquals(expected, f.get(5, TimeUnit.SECONDS))
            }
        } finally {
            pool.shutdownNow()
        }
    }
}

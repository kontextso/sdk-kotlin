package so.kontext.ads.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Centralised `Date` → wire-format conversion.
 *
 * Every `Date` shipped to the ad server is encoded as an ISO 8601 string
 * **with millisecond precision** (e.g. `"2024-01-01T00:00:00.000Z"`) to
 * match sdk-js's `JSON.stringify(date)` (which calls
 * `Date.toISOString()`). Plain `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")`
 * silently coarsens timestamps vs sdk-js — always go through this helper.
 *
 * `SimpleDateFormat` is *not* thread-safe, so we synchronise per-call
 * format. Constructing a new formatter per call would be cheap enough
 * for the rates we run at, but caching one and synchronising is faster
 * and matches iOS's `DateFormatting` shared-formatter pattern.
 *
 * Mirrors iOS `Utils/DateFormatting.swift`.
 */
internal object DateFormatting {

    private val ISO_8601_MS_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Returns an ISO 8601 string with millisecond precision in UTC. */
    fun iso8601String(date: Date): String = synchronized(ISO_8601_MS_FORMAT) {
        ISO_8601_MS_FORMAT.format(date)
    }
}
